package fr.lifecontrol.domain.eval

import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.TrackingMode
import fr.lifecontrol.domain.model.WeekStatus
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToLong

data class HabitStats(
    /** Jours (ou semaines si [weekly]) réussis. */
    val successes: Int,
    val failures: Int,
    val missing: Int,
    val weekly: Boolean,
    /** Taux de réussite (RG-09), null si rien à évaluer. */
    val rate: Double?,
    /** Somme des quantités / minutes, ou nombre de « fait » en binaire. */
    val total: Double,
    /** Nombre de jours renseignés avec une valeur. */
    val recordedDays: Int,
    /** Économies en centimes (RG-10), null si non applicable. */
    val savingsCents: Long?,
    /** Unités non consommées par rapport à la consommation de référence. */
    val unitsAvoided: Double?,
    val failuresByWeekday: Map<DayOfWeek, Int>,
    val failuresByTrigger: Map<String, Int>,
) {
    val evaluated: Int get() = successes + failures + missing
    val average: Double? get() = if (recordedDays == 0) null else total / recordedDays
}

object StatsCalculator {

    fun compute(history: HabitHistory, from: LocalDate, to: LocalDate): HabitStats {
        val habit = history.habit
        val records = history.records(from, to)

        val successes: Int
        val failures: Int
        val missing: Int
        if (history.isWeekly) {
            // Une semaine est rattachée à la période qui contient son premier jour (pas de double compte
            // entre deux périodes contiguës). Si la période commence en milieu de semaine, cette semaine
            // est tout de même retenue lorsque la période est plus courte qu'une semaine.
            val firstWeekStart = history.weekStartOf(from)
            val weeks = history.weeks(firstWeekStart, to).filter {
                val effectiveStart = maxOf(it.weekStart, habit.startDate)
                !effectiveStart.isBefore(from) || it.weekStart == firstWeekStart && to.isBefore(firstWeekStart.plusDays(7))
            }
            successes = weeks.count { it.status == WeekStatus.SUCCESS }
            failures = weeks.count { it.status == WeekStatus.FAILURE }
            missing = 0
        } else {
            successes = records.count { it.status == DayStatus.SUCCESS }
            failures = records.count { it.status == DayStatus.FAILURE }
            missing = records.count { it.status == DayStatus.MISSING }
        }
        val evaluated = successes + failures + missing
        val rate = if (evaluated == 0) null else successes.toDouble() / evaluated

        val valued = records.mapNotNull { r -> r.entry?.takeIf { it.hasValue && !it.isPause } }
        val total = when (habit.trackingMode) {
            TrackingMode.BINARY -> valued.count { it.done == true }.toDouble()
            else -> valued.sumOf { it.quantity ?: 0.0 }
        }

        var savingsCents: Long? = null
        var unitsAvoided: Double? = null
        val baseline = habit.baselinePerDay
        if (habit.direction != Direction.START && baseline != null) {
            var avoided = 0.0
            for (record in records) {
                val consumption = when (record.status) {
                    DayStatus.SUCCESS, DayStatus.FAILURE, DayStatus.PENDING -> consumptionOf(history, record.date, record.status, baseline)
                    else -> null
                } ?: continue
                avoided += (baseline - consumption).coerceAtLeast(0.0)
            }
            unitsAvoided = avoided
            savingsCents = habit.unitCostCents?.let { (avoided * it).roundToLong() }
        }

        val failureRecords = records.filter { it.status == DayStatus.FAILURE }
        return HabitStats(
            successes = successes,
            failures = failures,
            missing = missing,
            weekly = history.isWeekly,
            rate = rate,
            total = total,
            recordedDays = valued.size,
            savingsCents = savingsCents,
            unitsAvoided = unitsAvoided,
            failuresByWeekday = failureRecords.groupingBy { it.date.dayOfWeek }.eachCount(),
            failuresByTrigger = failureRecords.mapNotNull { it.entry?.triggerId }.groupingBy { it }.eachCount(),
        )
    }

    /** Consommation estimée d'un jour, pour RG-10. Null si inconnue. */
    private fun consumptionOf(history: HabitHistory, date: LocalDate, status: DayStatus, baseline: Double): Double? {
        val habit = history.habit
        val entry = history.entry(date)?.takeIf { it.hasValue }
        return when (habit.trackingMode) {
            TrackingMode.BINARY -> when {
                entry == null && status == DayStatus.PENDING -> null
                status == DayStatus.SUCCESS -> 0.0
                status == DayStatus.FAILURE -> baseline
                else -> null
            }
            else -> when {
                entry?.quantity != null -> entry.quantity
                status == DayStatus.SUCCESS && habit.direction == Direction.QUIT -> 0.0
                status == DayStatus.SUCCESS && habit.direction == Direction.LIMIT -> habit.goalAt(date).threshold ?: 0.0
                else -> null
            }
        }
    }
}

/** Agrégat tous habitudes confondues d'un jour, pour la carte de chaleur. */
data class DayAggregate(val date: LocalDate, val successes: Int, val evaluated: Int, val future: Boolean) {
    val ratio: Double? get() = if (evaluated == 0) null else successes.toDouble() / evaluated
}

object Aggregator {
    fun daily(histories: List<HabitHistory>, from: LocalDate, to: LocalDate, today: LocalDate): List<DayAggregate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .map { date ->
                var s = 0
                var e = 0
                for (h in histories) {
                    when (h.status(date)) {
                        DayStatus.SUCCESS -> { s++; e++ }
                        DayStatus.FAILURE, DayStatus.MISSING -> e++
                        else -> Unit
                    }
                }
                DayAggregate(date, s, e, date.isAfter(today))
            }
            .toList()
}
