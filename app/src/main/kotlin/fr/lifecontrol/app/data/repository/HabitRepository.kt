package fr.lifecontrol.app.data.repository

import androidx.room.withTransaction
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.db.HabitEntity
import fr.lifecontrol.app.data.db.ReminderEntity
import fr.lifecontrol.app.data.db.TriggerEntity
import fr.lifecontrol.app.data.db.toDomain
import fr.lifecontrol.app.data.db.toEntity
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Entry
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Goal
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Données saisies dans le formulaire de création / modification (F-03, F-04). */
data class HabitDraft(
    val id: String? = null,
    val templateId: String? = null,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val direction: Direction,
    val trackingMode: TrackingMode,
    val unit: String?,
    val step: Double,
    val startDate: LocalDate,
    val unitCostCents: Long?,
    val baselinePerDay: Double?,
    val motivation: String?,
    val passiveMode: Boolean,
    val frequency: Frequency,
    val threshold: Double?,
)

/** Une autre habitude porte déjà ce nom ou vient du même modèle du catalogue. */
class DuplicateHabitException(val existingId: String, val existingName: String, val archived: Boolean) :
    IllegalStateException("Habitude en double : $existingName")

/** Nom comparable : sans casse, sans accents, espaces normalisés (« Café » = « cafe »). */
internal fun normalizeHabitName(name: String): String =
    java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase()

data class Trigger(val id: String, val label: String)

/** Rappel d'une habitude (F-23). */
data class Reminder(val id: String, val habitId: String, val timeMinutes: Int, val daysMask: Int, val enabled: Boolean)

@Singleton
class HabitRepository @Inject constructor(
    private val db: AppDatabase,
) {
    private val habitDao = db.habitDao()
    private val entryDao = db.entryDao()
    private val triggerDao = db.triggerDao()
    private val reminderDao = db.reminderDao()

    /** Toutes les habitudes, archivées comprises, triées. */
    val habits: Flow<List<Habit>> = combine(habitDao.observeHabits(), habitDao.observeGoals()) { habits, goals ->
        val byHabit = goals.groupBy { it.habitId }
        habits.map { it.toDomain(byHabit[it.id].orEmpty()) }
    }

    val entries: Flow<List<Entry>> = entryDao.observeAll().map { list -> list.map { it.toDomain() } }

    val triggers: Flow<List<Trigger>> = triggerDao.observeAll().map { list -> list.map { Trigger(it.id, it.label) } }

    val reminders: Flow<List<Reminder>> = reminderDao.observeAll().map { list ->
        list.mapNotNull { r -> r.habitId?.let { Reminder(r.id, it, r.timeMinutes, r.daysOfWeekMask, r.enabled) } }
    }

    suspend fun getHabit(id: String): Habit? =
        habitDao.getHabit(id)?.toDomain(habitDao.getGoals(id))

    /**
     * Doublon éventuel (archivées comprises) : même nom normalisé, ou — pour une création —
     * même modèle du catalogue. [excludeId] est l'habitude en cours de modification.
     */
    suspend fun findDuplicate(name: String, templateId: String?, excludeId: String?): Habit? {
        val key = normalizeHabitName(name)
        val creating = excludeId == null
        return habitDao.getHabits()
            .filter { it.id != excludeId }
            .firstOrNull { other ->
                (key.isNotEmpty() && normalizeHabitName(other.name) == key) ||
                    (creating && templateId != null && other.templateId == templateId)
            }
            ?.let { getHabit(it.id) }
    }

    /** @throws DuplicateHabitException si l'habitude ferait doublon (voir [findDuplicate]). */
    suspend fun saveHabit(draft: HabitDraft, today: LocalDate): String = db.withTransaction {
        findDuplicate(draft.name, draft.templateId, draft.id)?.let {
            throw DuplicateHabitException(it.id, it.name, it.isArchived)
        }
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { habitDao.getHabit(it) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val newGoal = Goal(draft.startDate, draft.frequency, draft.threshold)

        val goals: List<Goal> = if (existing == null) {
            listOf(newGoal)
        } else {
            val current = habitDao.getGoals(id).map { it.toDomain() }
            val last = current.lastOrNull()
            when {
                last != null && last.frequency == draft.frequency && last.threshold == draft.threshold ->
                    current
                // RG-08 : le nouvel objectif s'applique à partir d'aujourd'hui (ou du début s'il est futur).
                else -> {
                    val from = maxOf(today, draft.startDate)
                    current.filter { it.validFrom.isBefore(from) } + newGoal.copy(validFrom = from)
                }
            }
        }

        val entity = HabitEntity(
            id = id,
            templateId = draft.templateId,
            name = draft.name.trim(),
            emoji = draft.emoji,
            colorIndex = draft.colorIndex,
            direction = draft.direction.name,
            trackingMode = draft.trackingMode.name,
            unit = draft.unit?.trim()?.ifBlank { null },
            step = draft.step,
            startDate = draft.startDate,
            unitCostCents = draft.unitCostCents,
            baselinePerDay = draft.baselinePerDay,
            motivation = draft.motivation?.trim()?.ifBlank { null },
            passiveMode = draft.passiveMode,
            sortOrder = existing?.sortOrder ?: (habitDao.maxSortOrder() + 1),
            archivedAt = existing?.archivedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        habitDao.replaceHabit(entity, goals.map { it.toEntity(UUID.randomUUID().toString(), id) })
        id
    }

    suspend fun setArchived(id: String, archivedAt: LocalDate?) {
        val habit = habitDao.getHabit(id) ?: return
        habitDao.upsertHabit(habit.copy(archivedAt = archivedAt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteHabit(id: String) = habitDao.deleteHabit(id)

    /** Échange l'ordre d'affichage avec la voisine (F-06). */
    suspend fun move(id: String, offset: Int) = db.withTransaction {
        val list = habitDao.getHabits().filter { it.archivedAt == null }.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in list.indices) return@withTransaction
        list.add(target, list.removeAt(index))
        val now = System.currentTimeMillis()
        list.forEachIndexed { i, h -> habitDao.setSortOrder(h.id, i, now) }
    }

    /**
     * Modifie l'entrée d'un jour. Si le résultat ne contient plus rien d'utile, l'entrée est supprimée.
     */
    suspend fun updateEntry(habitId: String, date: LocalDate, transform: (Entry) -> Entry) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val existing = entryDao.get(habitId, date)?.toDomain()
                ?: Entry(id = UUID.randomUUID().toString(), habitId = habitId, date = date, createdAt = now)
            val updated = transform(existing).copy(updatedAt = now)
            val empty = !updated.hasValue && !updated.isPause && updated.note.isNullOrBlank() && updated.mood == null
            if (empty) entryDao.delete(habitId, date) else entryDao.upsert(updated.toEntity())
        }
    }

    suspend fun deleteEntry(habitId: String, date: LocalDate) = entryDao.delete(habitId, date)

    suspend fun saveTriggers(labels: List<Pair<String?, String>>) {
        val existing = triggerDao.getAll()
        val keepIds = labels.mapNotNull { it.first }.toSet()
        existing.filter { it.id !in keepIds }.forEach { triggerDao.delete(it) }
        triggerDao.upsertAll(
            labels.mapIndexed { i, (id, label) ->
                TriggerEntity(id ?: UUID.randomUUID().toString(), label.trim(), isDefault = false, sortOrder = i)
            },
        )
    }

    suspend fun triggersCount(): Int = triggerDao.getAll().size

    suspend fun remindersFor(habitId: String): List<Reminder> = reminderDao.forHabit(habitId).map {
        Reminder(it.id, habitId, it.timeMinutes, it.daysOfWeekMask, it.enabled)
    }

    /** Remplace les rappels d'une habitude ; renvoie les identifiants supprimés (pour annuler leur planification). */
    suspend fun saveReminders(habitId: String, reminders: List<Reminder>): List<String> = db.withTransaction {
        val old = reminderDao.forHabit(habitId)
        val keep = reminders.map { it.id }.toSet()
        val removed = old.filter { it.id !in keep }.map { it.id }
        removed.forEach { reminderDao.delete(it) }
        reminderDao.upsertAll(reminders.map { ReminderEntity(it.id, habitId, it.timeMinutes, it.daysMask, it.enabled) })
        removed
    }

    suspend fun allReminders(): List<Reminder> = reminderDao.getAll().mapNotNull { r ->
        r.habitId?.let { Reminder(r.id, it, r.timeMinutes, r.daysOfWeekMask, r.enabled) }
    }

    suspend fun getReminder(id: String): Reminder? = reminderDao.get(id)?.let { r ->
        r.habitId?.let { Reminder(r.id, it, r.timeMinutes, r.daysOfWeekMask, r.enabled) }
    }

    suspend fun entry(habitId: String, date: LocalDate): Entry? = entryDao.get(habitId, date)?.toDomain()

    suspend fun allHabits(): List<Habit> {
        val goals = habitDao.getGoals().groupBy { it.habitId }
        return habitDao.getHabits().map { it.toDomain(goals[it.id].orEmpty()) }
    }
}
