package fr.lifecontrol.domain.badge

import fr.lifecontrol.domain.eval.HabitHistory
import fr.lifecontrol.domain.eval.StatsCalculator
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

enum class BadgeScope { GLOBAL, HABIT }

/** Unité de la mesure d'un badge, pour l'affichage de la progression. */
enum class BadgeUnit { COUNT, DAYS, WEEKS, MONTHS, QUARTERS, YEARS, EUROS, HOURS, UNITS }

/** Données nécessaires à l'évaluation des badges (§7.3). */
data class BadgeSnapshot(
    val today: LocalDate,
    /** Historiques de toutes les habitudes, archivées comprises. */
    val histories: List<HabitHistory>,
    val articlesRead: Int,
)

/**
 * Règle d'un badge : une mesure et des paliers croissants (bronze, argent, or, platine…).
 * Moteur pur, sans dépendance Android.
 */
abstract class BadgeRule(val id: String, val scope: BadgeScope) {
    open fun appliesTo(habit: Habit): Boolean = true
    abstract fun tiers(habit: Habit?): List<Long>
    abstract fun unit(habit: Habit?): BadgeUnit
    /** Mesure courante (meilleure valeur atteinte, pour que la progression ne recule pas inutilement). */
    abstract fun metric(snapshot: BadgeSnapshot, history: HabitHistory?): Long
}

data class BadgeProgress(
    val badgeId: String,
    val habitId: String?,
    val current: Long,
    val tiers: List<Long>,
    val unit: BadgeUnit,
) {
    /** Nombre de paliers atteints (0 = verrouillé). */
    val reachedLevel: Int = tiers.count { current >= it }
    val nextTarget: Long? = tiers.getOrNull(reachedLevel)
    val isComplete: Boolean get() = nextTarget == null

    /** Avancement vers le prochain palier, entre 0 et 1. */
    val fraction: Float
        get() {
            val target = nextTarget ?: return 1f
            return (current.toFloat() / target).coerceIn(0f, 1f)
        }
}

object BadgeEngine {

    fun evaluate(snapshot: BadgeSnapshot, rules: List<BadgeRule> = BadgeRules.all): List<BadgeProgress> =
        rules.flatMap { rule ->
            when (rule.scope) {
                BadgeScope.GLOBAL -> listOf(
                    BadgeProgress(rule.id, null, rule.metric(snapshot, null), rule.tiers(null), rule.unit(null)),
                )
                BadgeScope.HABIT -> snapshot.histories
                    .filter { rule.appliesTo(it.habit) }
                    .map {
                        BadgeProgress(rule.id, it.habit.id, rule.metric(snapshot, it), rule.tiers(it.habit), rule.unit(it.habit))
                    }
            }
        }
}

object BadgeRules {

    val firstStep = object : BadgeRule("first_step", BadgeScope.GLOBAL) {
        override fun tiers(habit: Habit?) = listOf(1L)
        override fun unit(habit: Habit?) = BadgeUnit.COUNT
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) =
            snapshot.histories.sumOf { h -> h.entriesByDate.values.count { it.hasValue } }.toLong().coerceAtMost(1)
    }

    val streak = object : BadgeRule("streak", BadgeScope.HABIT) {
        override fun tiers(habit: Habit?) =
            if (habit.isWeekly()) listOf(4L, 12, 26, 52) else listOf(3L, 7, 21, 30, 66, 100, 365)
        override fun unit(habit: Habit?) = if (habit.isWeekly()) BadgeUnit.WEEKS else BadgeUnit.DAYS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) = history!!.streak.best.toLong()
    }

    val perfectMonth = object : BadgeRule("perfect_month", BadgeScope.HABIT) {
        override fun appliesTo(habit: Habit) = !habit.isWeekly()
        override fun tiers(habit: Habit?) = listOf(1L, 3, 6, 12)
        override fun unit(habit: Habit?) = BadgeUnit.MONTHS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?): Long {
            val h = history!!
            var month = YearMonth.from(h.habit.startDate)
            var count = 0L
            while (month.atEndOfMonth().isBefore(snapshot.today)) {
                val first = month.atDay(1)
                val last = month.atEndOfMonth()
                if (!first.isBefore(h.habit.startDate) && !last.isAfter(h.lastDay)) {
                    val records = h.records(first, last)
                    val evaluated = records.count { it.status in EVALUATED }
                    if (evaluated > 0 && records.all { it.status !in BROKEN }) count++
                }
                month = month.plusMonths(1)
            }
            return count
        }
    }

    val rebound = object : BadgeRule("rebound", BadgeScope.HABIT) {
        override fun appliesTo(habit: Habit) = !habit.isWeekly()
        override fun tiers(habit: Habit?) = listOf(1L, 5, 20)
        override fun unit(habit: Habit?) = BadgeUnit.COUNT
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) = history!!.reboundCount().toLong()
    }

    val journal = object : BadgeRule("journal", BadgeScope.GLOBAL) {
        override fun tiers(habit: Habit?) = listOf(7L, 30, 100)
        override fun unit(habit: Habit?) = BadgeUnit.DAYS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?): Long {
            val histories = snapshot.histories
            val start = histories.minOfOrNull { it.habit.startDate } ?: return 0
            var count = 0L
            var date = start
            while (!date.isAfter(snapshot.today)) {
                var required = 0
                var allRecorded = true
                for (h in histories) {
                    val status = h.status(date)
                    val recorded = h.entry(date)?.let { it.hasValue || it.isPause } == true
                    val needed = status in EVALUATED || status == DayStatus.PENDING
                    if (needed && !(h.isWeekly && !recorded)) {
                        required++
                        if (!recorded) allRecorded = false
                    }
                }
                if (required > 0 && allRecorded) count++
                date = date.plusDays(1)
            }
            return count
        }
    }

    val writer = object : BadgeRule("writer", BadgeScope.GLOBAL) {
        override fun tiers(habit: Habit?) = listOf(10L, 50, 200)
        override fun unit(habit: Habit?) = BadgeUnit.COUNT
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) =
            snapshot.histories.sumOf { h -> h.entriesByDate.values.count { !it.note.isNullOrBlank() } }.toLong()
    }

    val saver = object : BadgeRule("saver", BadgeScope.HABIT) {
        override fun appliesTo(habit: Habit) =
            habit.direction != Direction.START && habit.unitCostCents != null && habit.baselinePerDay != null
        override fun tiers(habit: Habit?) = listOf(50L, 200, 1000, 5000)
        override fun unit(habit: Habit?) = BadgeUnit.EUROS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?): Long {
            val h = history!!
            val cents = StatsCalculator.compute(h, h.habit.startDate, h.lastDay).savingsCents ?: 0
            return cents / 100
        }
    }

    val cumulative = object : BadgeRule("cumulative", BadgeScope.HABIT) {
        override fun appliesTo(habit: Habit) =
            habit.direction == Direction.START && habit.trackingMode != TrackingMode.BINARY
        override fun tiers(habit: Habit?) =
            if (habit?.trackingMode == TrackingMode.DURATION) listOf(10L, 50, 100, 500) else listOf(100L, 500, 1000, 5000)
        override fun unit(habit: Habit?) =
            if (habit?.trackingMode == TrackingMode.DURATION) BadgeUnit.HOURS else BadgeUnit.UNITS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?): Long {
            val h = history!!
            val total = h.entriesByDate.values.filter { h.habit.isActiveOn(it.date) && !it.isPause }.sumOf { it.quantity ?: 0.0 }
            return if (h.habit.trackingMode == TrackingMode.DURATION) (total / 60).toLong() else total.toLong()
        }
    }

    val juggler = object : BadgeRule("juggler", BadgeScope.GLOBAL) {
        override fun tiers(habit: Habit?) = listOf(3L, 5, 8)
        override fun unit(habit: Habit?) = BadgeUnit.COUNT
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) =
            snapshot.histories.count {
                !it.habit.isArchived && !it.habit.startDate.isAfter(snapshot.today.minusDays(6))
            }.toLong()
    }

    val curious = object : BadgeRule("curious", BadgeScope.GLOBAL) {
        override fun tiers(habit: Habit?) = listOf(5L, 15)
        override fun unit(habit: Habit?) = BadgeUnit.COUNT
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) = snapshot.articlesRead.toLong()
    }

    val solidQuarter = object : BadgeRule("solid_quarter", BadgeScope.HABIT) {
        override fun tiers(habit: Habit?) = listOf(1L, 4)
        override fun unit(habit: Habit?) = BadgeUnit.QUARTERS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?): Long {
            val h = history!!
            var quarterStart = h.habit.startDate.with(IsoFields.DAY_OF_QUARTER, 1)
            var count = 0L
            while (true) {
                val quarterEnd = quarterStart.plusMonths(3).minusDays(1)
                if (!quarterEnd.isBefore(snapshot.today)) break
                if (!quarterStart.isBefore(h.habit.startDate) && !quarterEnd.isAfter(h.lastDay)) {
                    val rate = StatsCalculator.compute(h, quarterStart, quarterEnd).rate
                    if (rate != null && rate >= 0.8) count++
                }
                quarterStart = quarterStart.plusMonths(3)
            }
            return count
        }
    }

    val anniversary = object : BadgeRule("anniversary", BadgeScope.HABIT) {
        override fun tiers(habit: Habit?) = listOf(1L, 2, 3, 5)
        override fun unit(habit: Habit?) = BadgeUnit.YEARS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) =
            ChronoUnit.YEARS.between(history!!.habit.startDate, history.lastDay.plusDays(1))
    }

    /** Badges thématiques liés à un modèle du catalogue : meilleure série d'au moins 30 jours. */
    private fun thematicStreak(id: String, templateId: String) = object : BadgeRule(id, BadgeScope.HABIT) {
        override fun appliesTo(habit: Habit) = habit.templateId == templateId && !habit.isWeekly()
        override fun tiers(habit: Habit?) = listOf(30L)
        override fun unit(habit: Habit?) = BadgeUnit.DAYS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) = history!!.streak.best.toLong()
    }

    val lungsFree = thematicStreak("lungs_free", "smoking")
    val dryMonth = thematicStreak("dry_month", "alcohol")
    val veggie = thematicStreak("veggie", "meat")

    val bookworm = object : BadgeRule("bookworm", BadgeScope.HABIT) {
        override fun appliesTo(habit: Habit) = habit.templateId == "reading" && habit.trackingMode == TrackingMode.QUANTITY
        override fun tiers(habit: Habit?) = listOf(3000L)
        override fun unit(habit: Habit?) = BadgeUnit.UNITS
        override fun metric(snapshot: BadgeSnapshot, history: HabitHistory?) =
            history!!.entriesByDate.values.sumOf { it.quantity ?: 0.0 }.toLong()
    }

    val all: List<BadgeRule> = listOf(
        firstStep, streak, perfectMonth, rebound, journal, writer, saver, cumulative,
        juggler, curious, solidQuarter, anniversary, lungsFree, dryMonth, veggie, bookworm,
    )

    private val EVALUATED = setOf(DayStatus.SUCCESS, DayStatus.FAILURE, DayStatus.MISSING)
    private val BROKEN = setOf(DayStatus.FAILURE, DayStatus.MISSING)

    private fun Habit?.isWeekly() = this?.currentGoal?.frequency is Frequency.TimesPerWeek
}
