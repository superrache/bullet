package fr.lifecontrol.domain.eval

import fr.lifecontrol.domain.model.DayRecord
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Entry
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.Streak
import fr.lifecontrol.domain.model.StreakUnit
import fr.lifecontrol.domain.model.WeekRecord
import fr.lifecontrol.domain.model.WeekStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Historique évalué d'une habitude : statut de chaque jour, semaines, séries.
 * Toutes les règles sont calculées en mémoire à partir des entrées (le statut n'est jamais stocké).
 */
class HabitHistory(
    val habit: Habit,
    entries: List<Entry>,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    val entriesByDate: Map<LocalDate, Entry> = entries.associateBy { it.date }

    /** Dernier jour évaluable : aujourd'hui, ou la veille de l'archivage. */
    val lastDay: LocalDate = habit.lastActiveDay?.let { minOf(it, today) } ?: today

    fun entry(date: LocalDate): Entry? = entriesByDate[date]

    fun status(date: LocalDate): DayStatus =
        DayStatusEvaluator.evaluate(habit, date, entriesByDate[date], today)

    /** Tous les jours suivis, de la date de début au dernier jour. */
    val days: List<DayRecord> by lazy { records(habit.startDate, lastDay) }

    fun records(from: LocalDate, to: LocalDate): List<DayRecord> {
        val start = maxOf(from, habit.startDate)
        val end = minOf(to, lastDay)
        if (start.isAfter(end)) return emptyList()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { DayRecord(it, status(it), entriesByDate[it]) }
            .toList()
    }

    fun weekStartOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    /** Évaluation par semaine (RG-06), pour les semaines qui chevauchent [from, to]. */
    fun weeks(from: LocalDate, to: LocalDate): List<WeekRecord> {
        val start = maxOf(from, habit.startDate)
        val end = minOf(to, lastDay)
        if (start.isAfter(end)) return emptyList()
        val result = mutableListOf<WeekRecord>()
        var weekStart = weekStartOf(start)
        while (!weekStart.isAfter(end)) {
            evaluateWeek(weekStart)?.let(result::add)
            weekStart = weekStart.plusWeeks(1)
        }
        return result
    }

    val allWeeks: List<WeekRecord> by lazy { weeks(habit.startDate, lastDay) }

    private fun evaluateWeek(weekStart: LocalDate): WeekRecord? {
        val weekEnd = weekStart.plusDays(6)
        val records = records(weekStart, weekEnd)
        if (records.isEmpty()) return null
        val successes = records.count { it.status == DayStatus.SUCCESS }
        val isOpen = !weekEnd.isBefore(today) && (habit.lastActiveDay == null || !weekEnd.isAfter(lastDay))
        val goal = habit.goalAt(minOf(weekEnd, lastDay))
        val frequency = goal.frequency
        return if (frequency is Frequency.TimesPerWeek) {
            // Première ou dernière semaine incomplète : on ne peut exiger plus de jours que disponibles.
            val target = minOf(frequency.times, records.size)
            val status = when {
                successes >= target -> WeekStatus.SUCCESS
                isOpen -> WeekStatus.IN_PROGRESS
                else -> WeekStatus.FAILURE
            }
            WeekRecord(weekStart, successes, target, status)
        } else {
            val evaluated = records.filter {
                it.status == DayStatus.SUCCESS || it.status == DayStatus.FAILURE || it.status == DayStatus.MISSING
            }
            val status = when {
                evaluated.any { it.status != DayStatus.SUCCESS } -> WeekStatus.FAILURE
                isOpen -> WeekStatus.IN_PROGRESS
                evaluated.isEmpty() -> WeekStatus.NEUTRAL
                else -> WeekStatus.SUCCESS
            }
            WeekRecord(weekStart, successes, evaluated.size, status)
        }
    }

    val isWeekly: Boolean get() = habit.currentGoal.frequency is Frequency.TimesPerWeek

    /** Série en cours et meilleure série (RG-07), en jours ou en semaines selon l'objectif actuel. */
    val streak: Streak by lazy {
        if (isWeekly) {
            val (current, best) = runs(allWeeks.map {
                when (it.status) {
                    WeekStatus.SUCCESS -> RunStep.SUCCESS
                    WeekStatus.FAILURE -> RunStep.BREAK
                    else -> RunStep.SKIP
                }
            })
            Streak(current, best, StreakUnit.WEEKS)
        } else {
            val (current, best) = runs(days.map { dayStep(it.status) })
            Streak(current, best, StreakUnit.DAYS)
        }
    }

    /**
     * Nombre de fois où une série d'au moins [minRun] jours a été reconstruite après un écart
     * (badge « Rebond »).
     */
    fun reboundCount(minRun: Int = 3): Int {
        var afterFailure = false
        var run = 0
        var count = 0
        for (record in days) {
            when (dayStep(record.status)) {
                RunStep.SUCCESS -> {
                    run++
                    if (afterFailure && run == minRun) {
                        count++
                        afterFailure = false
                    }
                }
                RunStep.BREAK -> {
                    if (record.status == DayStatus.FAILURE) afterFailure = true
                    run = 0
                }
                RunStep.SKIP -> Unit
            }
        }
        return count
    }

    private enum class RunStep { SUCCESS, BREAK, SKIP }

    private fun dayStep(status: DayStatus) = when (status) {
        DayStatus.SUCCESS -> RunStep.SUCCESS
        DayStatus.FAILURE, DayStatus.MISSING -> RunStep.BREAK
        else -> RunStep.SKIP
    }

    private fun runs(steps: List<RunStep>): Pair<Int, Int> {
        var run = 0
        var best = 0
        for (step in steps) {
            when (step) {
                RunStep.SUCCESS -> {
                    run++
                    best = maxOf(best, run)
                }
                RunStep.BREAK -> run = 0
                RunStep.SKIP -> Unit
            }
        }
        return run to best
    }
}
