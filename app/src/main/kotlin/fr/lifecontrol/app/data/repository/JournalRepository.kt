package fr.lifecontrol.app.data.repository

import fr.lifecontrol.app.di.ApplicationScope
import fr.lifecontrol.domain.eval.HabitHistory
import fr.lifecontrol.domain.model.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Instantané complet et évalué du journal, partagé par tous les écrans. */
data class Journal(
    val today: LocalDate,
    val settings: UserSettings,
    /** Toutes les habitudes (archivées comprises), triées. */
    val habits: List<Habit>,
    val histories: Map<String, HabitHistory>,
    val triggers: List<Trigger>,
) {
    val activeHabits: List<Habit> get() = habits.filter { !it.isArchived }
    fun history(habitId: String): HabitHistory? = histories[habitId]
    fun habit(habitId: String): Habit? = habits.firstOrNull { it.id == habitId }
}

@Singleton
class JournalRepository @Inject constructor(
    habitRepository: HabitRepository,
    settingsRepository: SettingsRepository,
    dateProvider: DateProvider,
    @ApplicationScope scope: CoroutineScope,
) {
    val journal: StateFlow<Journal?> = combine(
        habitRepository.habits,
        habitRepository.entries,
        settingsRepository.settings,
        dateProvider.today,
        habitRepository.triggers,
    ) { habits, entries, settings, today, triggers ->
        val byHabit = entries.groupBy { it.habitId }
        Journal(
            today = today,
            settings = settings,
            habits = habits,
            histories = habits.associate { h ->
                h.id to HabitHistory(h, byHabit[h.id].orEmpty(), today, settings.firstDayOfWeek)
            },
            triggers = triggers,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)
}
