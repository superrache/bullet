package fr.lifecontrol.app.ui.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.data.content.CatalogRepository
import fr.lifecontrol.app.data.db.ALL_DAYS_MASK
import fr.lifecontrol.app.data.db.toDays
import fr.lifecontrol.app.data.db.toMask
import fr.lifecontrol.app.data.repository.DateProvider
import fr.lifecontrol.app.data.repository.DuplicateHabitException
import fr.lifecontrol.app.data.repository.HabitDraft
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.Reminder
import fr.lifecontrol.app.notifications.ReminderScheduler
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToLong

enum class FrequencyKind { DAILY, TIMES_PER_WEEK, DAYS_OF_WEEK }

/** État du formulaire (F-03). Les nombres sont gardés en texte pour l'édition. */
data class HabitForm(
    val id: String? = null,
    val templateId: String? = null,
    val name: String = "",
    val emoji: String = "⭐",
    val colorIndex: Int = 0,
    val direction: Direction = Direction.START,
    val trackingMode: TrackingMode = TrackingMode.BINARY,
    val unit: String = "",
    val step: String = "1",
    val threshold: String = "",
    val frequencyKind: FrequencyKind = FrequencyKind.DAILY,
    val timesPerWeek: Int = 3,
    val daysMask: Int = ALL_DAYS_MASK,
    val startDate: LocalDate = LocalDate.now(),
    val unitCost: String = "",
    val baseline: String = "",
    val motivation: String = "",
    val passiveMode: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val isNew: Boolean = true,
    val loaded: Boolean = false,
) {
    val needsThreshold: Boolean
        get() = direction == Direction.LIMIT || (direction == Direction.START && trackingMode != TrackingMode.BINARY)

    val allowsCost: Boolean get() = direction != Direction.START
}

enum class FormError { NAME, THRESHOLD, DAYS }

@HiltViewModel
class HabitEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val habitRepository: HabitRepository,
    private val catalogRepository: CatalogRepository,
    private val dateProvider: DateProvider,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val habitId: String? = savedStateHandle["habitId"]
    private val templateId: String? = savedStateHandle["templateId"]

    private val _form = MutableStateFlow(HabitForm(startDate = dateProvider.today.value))
    val form = _form.asStateFlow()

    private val _errors = MutableStateFlow<Set<FormError>>(emptySet())
    val errors = _errors.asStateFlow()

    /** Habitude existante avec laquelle le formulaire ferait doublon (vérifié en direct). */
    private val _duplicate = MutableStateFlow<Habit?>(null)
    val duplicate = _duplicate.asStateFlow()
    private var duplicateCheck: Job? = null

    private val savedChannel = Channel<String>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = habitId?.let { habitRepository.getHabit(it) }
            _form.value = when {
                existing != null -> {
                    val goal = existing.currentGoal
                    HabitForm(
                        id = existing.id,
                        templateId = existing.templateId,
                        name = existing.name,
                        emoji = existing.emoji,
                        colorIndex = existing.colorIndex,
                        direction = existing.direction,
                        trackingMode = existing.trackingMode,
                        unit = existing.unit.orEmpty(),
                        step = Fmt.number(existing.step),
                        threshold = goal.threshold?.let(Fmt::number).orEmpty(),
                        frequencyKind = when (goal.frequency) {
                            Frequency.Daily -> FrequencyKind.DAILY
                            is Frequency.TimesPerWeek -> FrequencyKind.TIMES_PER_WEEK
                            is Frequency.DaysOfWeek -> FrequencyKind.DAYS_OF_WEEK
                        },
                        timesPerWeek = (goal.frequency as? Frequency.TimesPerWeek)?.times ?: 3,
                        daysMask = (goal.frequency as? Frequency.DaysOfWeek)?.days?.toMask() ?: ALL_DAYS_MASK,
                        startDate = existing.startDate,
                        unitCost = existing.unitCostCents?.let { Fmt.number(it / 100.0) }.orEmpty(),
                        baseline = existing.baselinePerDay?.let(Fmt::number).orEmpty(),
                        motivation = existing.motivation.orEmpty(),
                        passiveMode = existing.passiveMode,
                        reminders = habitRepository.remindersFor(existing.id),
                        isNew = false,
                        loaded = true,
                    )
                }
                else -> {
                    val t = catalogRepository.template(templateId)
                    if (t == null) {
                        _form.value.copy(loaded = true)
                    } else {
                        HabitForm(
                            templateId = t.id,
                            name = t.name,
                            emoji = t.emoji,
                            colorIndex = t.color,
                            direction = t.direction,
                            trackingMode = t.mode,
                            unit = t.unit.orEmpty(),
                            step = Fmt.number(t.step),
                            threshold = t.threshold?.let(Fmt::number).orEmpty(),
                            frequencyKind = if (t.frequency == "TIMES_PER_WEEK") FrequencyKind.TIMES_PER_WEEK else FrequencyKind.DAILY,
                            timesPerWeek = t.timesPerWeek ?: 3,
                            startDate = dateProvider.today.value,
                            unitCost = t.unitCostCents?.let { Fmt.number(it / 100.0) }.orEmpty(),
                            baseline = t.baselinePerDay?.let(Fmt::number).orEmpty(),
                            loaded = true,
                        )
                    }
                }
            }
            checkDuplicate()
        }
    }

    private fun checkDuplicate() {
        val f = _form.value
        duplicateCheck?.cancel()
        duplicateCheck = viewModelScope.launch {
            _duplicate.value = habitRepository.findDuplicate(f.name, f.templateId, f.id)
        }
    }

    fun update(transform: (HabitForm) -> HabitForm) {
        _form.update { old ->
            var f = transform(old)
            // « Limiter » impose un suivi chiffré ; « N fois par semaine » est réservé à « commencer ».
            if (f.direction == Direction.LIMIT && f.trackingMode == TrackingMode.BINARY) f = f.copy(trackingMode = TrackingMode.QUANTITY)
            if (f.direction != Direction.START && f.frequencyKind == FrequencyKind.TIMES_PER_WEEK) f = f.copy(frequencyKind = FrequencyKind.DAILY)
            if (f.direction == Direction.START) f = f.copy(passiveMode = false)
            f
        }
        _errors.value = emptySet()
        checkDuplicate()
    }

    fun addReminder(minutes: Int) = update {
        it.copy(reminders = it.reminders + Reminder(UUID.randomUUID().toString(), it.id.orEmpty(), minutes, ALL_DAYS_MASK, true))
    }

    fun updateReminder(reminder: Reminder) = update {
        it.copy(reminders = it.reminders.map { r -> if (r.id == reminder.id) reminder else r })
    }

    fun removeReminder(id: String) = update { it.copy(reminders = it.reminders.filterNot { r -> r.id == id }) }

    private fun String.toNumber(): Double? = replace(',', '.').trim().toDoubleOrNull()

    fun save() {
        val f = _form.value
        val errors = buildSet {
            if (f.name.isBlank()) add(FormError.NAME)
            if (f.needsThreshold && (f.threshold.toNumber() == null || f.threshold.toNumber()!! < 0)) add(FormError.THRESHOLD)
            if (f.frequencyKind == FrequencyKind.DAYS_OF_WEEK && f.daysMask == 0) add(FormError.DAYS)
        }
        if (errors.isNotEmpty()) {
            _errors.value = errors
            return
        }
        if (_duplicate.value != null) return
        val frequency = when (f.frequencyKind) {
            FrequencyKind.DAILY -> Frequency.Daily
            FrequencyKind.TIMES_PER_WEEK -> Frequency.TimesPerWeek(f.timesPerWeek.coerceIn(1, 7))
            FrequencyKind.DAYS_OF_WEEK -> f.daysMask.toDays().let { if (it.size == 7) Frequency.Daily else Frequency.DaysOfWeek(it) }
        }
        val draft = HabitDraft(
            id = f.id,
            templateId = f.templateId,
            name = f.name,
            emoji = f.emoji,
            colorIndex = f.colorIndex,
            direction = f.direction,
            trackingMode = f.trackingMode,
            unit = f.unit.takeIf { f.trackingMode == TrackingMode.QUANTITY },
            step = f.step.toNumber()?.takeIf { it > 0 } ?: 1.0,
            startDate = f.startDate,
            unitCostCents = f.unitCost.toNumber()?.takeIf { f.allowsCost }?.let { (it * 100).roundToLong() },
            baselinePerDay = f.baseline.toNumber()?.takeIf { f.allowsCost },
            motivation = f.motivation,
            passiveMode = f.passiveMode,
            frequency = frequency,
            threshold = if (f.needsThreshold) f.threshold.toNumber() else null,
        )
        viewModelScope.launch {
            duplicateCheck?.join()
            val id = try {
                habitRepository.saveHabit(draft, dateProvider.today.value)
            } catch (e: DuplicateHabitException) {
                _duplicate.value = habitRepository.getHabit(e.existingId)
                return@launch
            }
            val reminders = f.reminders.map { it.copy(habitId = id) }
            val removed = habitRepository.saveReminders(id, reminders)
            removed.forEach(scheduler::cancel)
            reminders.forEach(scheduler::schedule)
            savedChannel.send(id)
        }
    }
}
