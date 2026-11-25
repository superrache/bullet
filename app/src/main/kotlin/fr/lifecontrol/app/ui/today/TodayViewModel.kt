package fr.lifecontrol.app.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.data.content.CatalogRepository
import fr.lifecontrol.app.data.repository.BadgeService
import fr.lifecontrol.app.data.repository.BadgeView
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.JournalRepository
import fr.lifecontrol.app.data.repository.Trigger
import fr.lifecontrol.domain.eval.DayStatusEvaluator
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Entry
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.Streak
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitCardState(
    val habit: Habit,
    val status: DayStatus,
    val entry: Entry?,
    val streak: Streak,
    /** Avancement du jour pour « commencer » en quantité / durée (0..1). */
    val dayProgress: Float?,
)

data class TodayUiState(
    val date: LocalDate,
    val today: LocalDate,
    val firstName: String,
    val scheduled: List<HabitCardState>,
    val unscheduled: List<HabitCardState>,
    val nearestBadge: BadgeView?,
    val triggers: List<Trigger>,
    val hasHabits: Boolean,
) {
    val isToday: Boolean get() = date == today
    val successCount: Int get() = scheduled.count { it.status == DayStatus.SUCCESS }
}

sealed interface TodayEvent {
    data object Celebrate : TodayEvent
    data class Slip(val habit: Habit, val encouragement: String, val hasHelp: Boolean, val hasArticle: Boolean) : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journalRepository: JournalRepository,
    private val habitRepository: HabitRepository,
    private val catalogRepository: CatalogRepository,
    badgeService: BadgeService,
) : ViewModel() {

    private val fixedDate: LocalDate? = savedStateHandle.get<String>("date")?.let(LocalDate::parse)

    /** null = suit le jour courant. */
    private val selectedDate = MutableStateFlow(fixedDate)

    private val events = Channel<TodayEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<TodayUiState?> = combine(
        journalRepository.journal.filterNotNull(),
        selectedDate,
        badgeService.badges,
    ) { journal, selected, badges ->
        val date = selected?.coerceAtMost(journal.today) ?: journal.today
        val cards = journal.activeHabits
            .filter { it.isActiveOn(date) }
            .map { habit ->
                val history = journal.history(habit.id)!!
                val entry = history.entry(date)
                val goal = habit.goalAt(date)
                HabitCardState(
                    habit = habit,
                    status = history.status(date),
                    entry = entry,
                    streak = history.streak,
                    dayProgress = if (habit.direction == Direction.START && habit.trackingMode != TrackingMode.BINARY) {
                        ((entry?.quantity ?: 0.0) / (goal.threshold ?: 1.0)).toFloat().coerceIn(0f, 1f)
                    } else {
                        null
                    },
                )
            }
        TodayUiState(
            date = date,
            today = journal.today,
            firstName = journal.settings.firstName,
            scheduled = cards.filter { it.status != DayStatus.NEUTRAL || it.entry?.hasValue == true },
            unscheduled = cards.filter { it.status == DayStatus.NEUTRAL && it.entry?.hasValue != true },
            nearestBadge = badges
                .filter { !it.isComplete && it.fraction > 0f && it.habitId?.let { id -> journal.habit(id)?.isArchived == false } != false }
                .maxWithOrNull(compareBy<BadgeView> { it.fraction }.thenByDescending { it.nextTarget ?: 0 }),
            triggers = journal.triggers,
            hasHabits = journal.activeHabits.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isStandalone: Boolean get() = fixedDate != null

    fun previousDay() {
        val state = uiState.value ?: return
        selectedDate.value = state.date.minusDays(1)
    }

    fun nextDay() {
        val state = uiState.value ?: return
        if (state.date < state.today) {
            selectedDate.value = state.date.plusDays(1).takeIf { it < state.today || fixedDate != null }
        }
    }

    fun goToToday() {
        selectedDate.value = null
    }

    /** Saisie binaire (F-07). Re-taper la même valeur annule la saisie. */
    fun setDone(habit: Habit, value: Boolean) = change(habit) { entry ->
        entry.copy(done = if (entry.done == value) null else value)
    }

    fun increment(habit: Habit, delta: Double) = change(habit) { entry ->
        val base = entry.quantity ?: 0.0
        entry.copy(quantity = (base + delta).coerceAtLeast(0.0))
    }

    fun saveDetails(
        habit: Habit,
        done: Boolean?,
        quantity: Double?,
        note: String?,
        mood: Int?,
        triggerId: String?,
    ) = change(habit) { entry ->
        entry.copy(
            done = done,
            quantity = quantity,
            note = note?.trim()?.take(MAX_NOTE)?.ifBlank { null },
            mood = mood,
            triggerId = triggerId,
        )
    }

    fun clear(habit: Habit) {
        val date = uiState.value?.date ?: return
        viewModelScope.launch { habitRepository.deleteEntry(habit.id, date) }
    }

    fun move(habit: Habit, offset: Int) {
        viewModelScope.launch { habitRepository.move(habit.id, offset) }
    }

    private fun change(habit: Habit, transform: (Entry) -> Entry) {
        val state = uiState.value ?: return
        val date = state.date
        viewModelScope.launch {
            val journal = journalRepository.journal.value ?: return@launch
            val before = DayStatusEvaluator.evaluate(habit, date, journal.history(habit.id)?.entry(date), journal.today)
            var updated: Entry? = null
            habitRepository.updateEntry(habit.id, date) { transform(it).also { e -> updated = e } }
            val after = DayStatusEvaluator.evaluate(habit, date, updated, journal.today)
            if (after == DayStatus.SUCCESS && before != DayStatus.SUCCESS) {
                events.send(TodayEvent.Celebrate)
            } else if (after == DayStatus.FAILURE && before != DayStatus.FAILURE) {
                val template = catalogRepository.template(habit.templateId)
                events.send(
                    TodayEvent.Slip(
                        habit = habit,
                        encouragement = catalogRepository.catalog.encouragements.random(),
                        hasHelp = template?.help?.isNotEmpty() == true,
                        hasArticle = template != null,
                    ),
                )
            }
        }
    }

    companion object {
        const val MAX_NOTE = 500
    }
}
