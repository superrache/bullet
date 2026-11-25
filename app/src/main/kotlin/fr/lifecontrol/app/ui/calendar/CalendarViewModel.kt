package fr.lifecontrol.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.data.repository.Journal
import fr.lifecontrol.app.data.repository.JournalRepository
import fr.lifecontrol.domain.eval.Aggregator
import fr.lifecontrol.domain.eval.DayAggregate
import fr.lifecontrol.domain.eval.HabitHistory
import fr.lifecontrol.domain.eval.HabitStats
import fr.lifecontrol.domain.eval.StatsCalculator
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class CalendarMode { MONTH, OVERVIEW }

enum class PeriodType { WEEK, MONTH, QUARTER, YEAR, ALL, CUSTOM }

data class Period(val type: PeriodType, val from: LocalDate, val to: LocalDate)

data class HabitPeriodStats(
    val habit: Habit,
    val history: HabitHistory,
    val stats: HabitStats,
    val previous: HabitStats?,
)

/** Barre de graphique : libellé + valeur. */
data class ChartBar(val label: String, val value: Float)

data class OverviewState(
    val period: Period,
    val canGoNext: Boolean,
    val aggregates: Map<LocalDate, DayAggregate>,
    val habitStats: List<HabitPeriodStats>,
    /** Taux global sur la période. */
    val globalRate: Double?,
    val previousGlobalRate: Double?,
    val chart: List<ChartBar>,
    /** true si [chart] représente des taux (%), false si des quantités. */
    val chartIsRate: Boolean,
    val chartReference: Float?,
    val failuresByTrigger: List<Pair<String, Int>>,
    val failuresByWeekday: List<Pair<DayOfWeek, Int>>,
)

data class CalendarUiState(
    val journal: Journal,
    val mode: CalendarMode,
    val selectedHabitId: String?,
    val month: YearMonth,
    val overview: OverviewState?,
) {
    val selectedHabit: Habit? get() = selectedHabitId?.let(journal::habit)
}

private data class Selection(
    val mode: CalendarMode = CalendarMode.MONTH,
    val habitId: String? = null,
    val month: YearMonth = YearMonth.now(),
    val periodType: PeriodType = PeriodType.MONTH,
    val anchor: LocalDate? = null,
    val custom: Pair<LocalDate, LocalDate>? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    journalRepository: JournalRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())

    val uiState: StateFlow<CalendarUiState?> = combine(journalRepository.journal.filterNotNull(), selection) { journal, sel ->
        val habitId = sel.habitId?.takeIf { journal.habit(it) != null }
        CalendarUiState(
            journal = journal,
            mode = sel.mode,
            selectedHabitId = habitId,
            month = sel.month,
            overview = if (sel.mode == CalendarMode.OVERVIEW) overview(journal, sel.copy(habitId = habitId)) else null,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMode(mode: CalendarMode) = selection.update { it.copy(mode = mode) }
    fun selectHabit(id: String?) = selection.update { it.copy(habitId = id) }
    fun shiftMonth(offset: Long) = selection.update { it.copy(month = it.month.plusMonths(offset)) }
    fun setPeriodType(type: PeriodType) = selection.update { it.copy(periodType = type, anchor = null) }
    fun setCustomRange(from: LocalDate, to: LocalDate) =
        selection.update { it.copy(periodType = PeriodType.CUSTOM, custom = from to to) }

    fun shiftPeriod(direction: Int) {
        val journal = uiState.value?.journal ?: return
        selection.update { sel ->
            val period = period(journal, sel)
            val anchor = when (sel.periodType) {
                PeriodType.WEEK -> period.from.plusWeeks(direction.toLong())
                PeriodType.MONTH -> period.from.plusMonths(direction.toLong())
                PeriodType.QUARTER -> period.from.plusMonths(3L * direction)
                PeriodType.YEAR -> period.from.plusYears(direction.toLong())
                PeriodType.ALL -> return@update sel
                PeriodType.CUSTOM -> {
                    val length = ChronoUnit.DAYS.between(period.from, period.to) + 1
                    return@update sel.copy(custom = period.from.plusDays(length * direction) to period.to.plusDays(length * direction))
                }
            }
            sel.copy(anchor = anchor)
        }
    }

    private fun period(journal: Journal, sel: Selection): Period {
        val anchor = sel.anchor ?: journal.today
        return when (sel.periodType) {
            PeriodType.WEEK -> {
                val start = anchor.with(TemporalAdjusters.previousOrSame(journal.settings.firstDayOfWeek))
                Period(PeriodType.WEEK, start, start.plusDays(6))
            }
            PeriodType.MONTH -> YearMonth.from(anchor).let { Period(PeriodType.MONTH, it.atDay(1), it.atEndOfMonth()) }
            PeriodType.QUARTER -> {
                val start = anchor.with(IsoFields.DAY_OF_QUARTER, 1)
                Period(PeriodType.QUARTER, start, start.plusMonths(3).minusDays(1))
            }
            PeriodType.YEAR -> Period(PeriodType.YEAR, anchor.withDayOfYear(1), anchor.withDayOfYear(1).plusYears(1).minusDays(1))
            PeriodType.ALL -> {
                val start = journal.habits.minOfOrNull { it.startDate } ?: journal.today
                Period(PeriodType.ALL, minOf(start, journal.today), journal.today)
            }
            PeriodType.CUSTOM -> {
                val (from, to) = sel.custom ?: (journal.today.minusDays(29) to journal.today)
                Period(PeriodType.CUSTOM, from, to)
            }
        }
    }

    private fun previousPeriod(period: Period): Period? = when (period.type) {
        PeriodType.WEEK -> Period(period.type, period.from.minusWeeks(1), period.to.minusWeeks(1))
        PeriodType.MONTH -> YearMonth.from(period.from).minusMonths(1).let { Period(period.type, it.atDay(1), it.atEndOfMonth()) }
        PeriodType.QUARTER -> Period(period.type, period.from.minusMonths(3), period.from.minusDays(1))
        PeriodType.YEAR -> Period(period.type, period.from.minusYears(1), period.from.minusDays(1))
        PeriodType.ALL -> null
        PeriodType.CUSTOM -> {
            val length = ChronoUnit.DAYS.between(period.from, period.to) + 1
            Period(period.type, period.from.minusDays(length), period.from.minusDays(1))
        }
    }

    private fun overview(journal: Journal, sel: Selection): OverviewState {
        val period = period(journal, sel)
        val previous = previousPeriod(period)
        val selected = sel.habitId?.let { journal.habit(it) }
        val habits = (selected?.let(::listOf) ?: journal.habits).filter { h ->
            !h.startDate.isAfter(period.to) && (h.archivedAt == null || h.archivedAt!!.isAfter(period.from))
        }
        val histories = habits.mapNotNull { journal.history(it.id) }
        val stats = histories.map { h ->
            HabitPeriodStats(
                habit = h.habit,
                history = h,
                stats = StatsCalculator.compute(h, period.from, period.to),
                previous = previous?.let { p -> StatsCalculator.compute(h, p.from, p.to).takeIf { it.evaluated > 0 } },
            )
        }
        val aggregates = Aggregator.daily(histories, period.from, period.to, journal.today).associateBy { it.date }
        val globalRate = rate(aggregates.values)
        val previousRate = previous?.let { rate(Aggregator.daily(histories, it.from, it.to, journal.today)) }

        val chartIsRate = selected == null || selected.trackingMode == TrackingMode.BINARY
        val chart = chart(period, journal.today, aggregates, selected, histories.firstOrNull(), chartIsRate)
        val reference = if (!chartIsRate && selected != null && ChronoUnit.DAYS.between(period.from, period.to) <= 31) {
            selected.currentGoal.threshold?.toFloat()
        } else null

        val triggerNames = journal.triggers.associate { it.id to it.label }
        val byTrigger = stats.flatMap { it.stats.failuresByTrigger.entries }
            .groupBy({ triggerNames[it.key] ?: "?" }, { it.value })
            .mapValues { it.value.sum() }
            .toList().sortedByDescending { it.second }
        val byWeekday = DayOfWeek.entries.map { d -> d to stats.sumOf { it.stats.failuresByWeekday[d] ?: 0 } }
            .let { list -> val order = (0L until 7L).map { journal.settings.firstDayOfWeek.plus(it) }; order.map { d -> list.first { it.first == d } } }

        return OverviewState(
            period = period,
            canGoNext = period.to < journal.today && period.type != PeriodType.ALL,
            aggregates = aggregates,
            habitStats = stats,
            globalRate = globalRate,
            previousGlobalRate = previousRate,
            chart = chart,
            chartIsRate = chartIsRate,
            chartReference = reference,
            failuresByTrigger = byTrigger,
            failuresByWeekday = byWeekday,
        )
    }

    private fun rate(aggregates: Collection<DayAggregate>): Double? {
        val evaluated = aggregates.sumOf { it.evaluated }
        return if (evaluated == 0) null else aggregates.sumOf { it.successes }.toDouble() / evaluated
    }

    private fun chart(
        period: Period,
        today: LocalDate,
        aggregates: Map<LocalDate, DayAggregate>,
        selected: Habit?,
        history: HabitHistory?,
        isRate: Boolean,
    ): List<ChartBar> {
        val end = minOf(period.to, today)
        if (end.isBefore(period.from)) return emptyList()
        val days = ChronoUnit.DAYS.between(period.from, period.to) + 1
        val buckets: List<Pair<LocalDate, LocalDate>> = when {
            days <= 31 -> generateSequence(period.from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { it to it }.toList()
            days <= 120 -> generateSequence(period.from) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }
                .map { it to minOf(it.plusDays(6), end) }.toList()
            else -> generateSequence(YearMonth.from(period.from)) { it.plusMonths(1) }
                .takeWhile { !it.atDay(1).isAfter(end) }
                .map { maxOf(it.atDay(1), period.from) to minOf(it.atEndOfMonth(), end) }.toList()
        }
        return buckets.map { (from, to) ->
            val label = when {
                days <= 7 -> fr.lifecontrol.app.ui.util.Fmt.weekdayNarrow(from.dayOfWeek)
                days <= 31 -> from.dayOfMonth.toString()
                days <= 120 -> fr.lifecontrol.app.ui.util.Fmt.dayMonth(from)
                else -> fr.lifecontrol.app.ui.util.Fmt.monthShort(YearMonth.from(from))
            }
            val value = if (isRate) {
                val range = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.mapNotNull { aggregates[it] }.toList()
                val evaluated = range.sumOf { it.evaluated }
                if (evaluated == 0) 0f else range.sumOf { it.successes }.toFloat() / evaluated * 100f
            } else {
                history?.records(from, to)?.sumOf { it.entry?.quantity ?: 0.0 }?.toFloat() ?: 0f
            }
            ChartBar(label, value)
        }
    }
}
