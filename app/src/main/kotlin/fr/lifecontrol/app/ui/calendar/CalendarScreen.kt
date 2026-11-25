package fr.lifecontrol.app.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.lifecontrol.app.R
import fr.lifecontrol.app.ui.components.AggregateDayCell
import fr.lifecontrol.app.ui.components.BarChart
import fr.lifecontrol.app.ui.components.CardShape
import fr.lifecontrol.app.ui.components.EmojiCircle
import fr.lifecontrol.app.ui.components.EmptyState
import fr.lifecontrol.app.ui.components.Heatmap
import fr.lifecontrol.app.ui.components.HeatmapLegend
import fr.lifecontrol.app.ui.components.HabitPixels
import fr.lifecontrol.app.ui.components.HabitPixelsLegend
import fr.lifecontrol.app.ui.components.HorizontalBars
import fr.lifecontrol.app.ui.components.MonthGrid
import fr.lifecontrol.app.ui.components.MonthHeader
import fr.lifecontrol.app.ui.components.SectionTitle
import fr.lifecontrol.app.ui.components.StatRow
import fr.lifecontrol.app.ui.components.StatTile
import fr.lifecontrol.app.ui.components.StatusDayCell
import fr.lifecontrol.app.ui.components.aggregateColorFn
import fr.lifecontrol.app.ui.components.statusColor
import fr.lifecontrol.app.ui.habit.HabitStatsBlock
import fr.lifecontrol.app.ui.theme.HabitPalette
import fr.lifecontrol.app.ui.theme.LocalStatusColors
import fr.lifecontrol.app.ui.today.statusText
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.TrackingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(
    onOpenDay: (LocalDate) -> Unit,
    onOpenHabit: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_title)

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_calendar)) },
                actions = {
                    val s = state
                    if (s?.mode == CalendarMode.OVERVIEW && s.overview != null) {
                        IconButton(onClick = { ShareImage.share(context, s, shareTitle) }) {
                            Icon(Icons.Rounded.Share, stringResource(R.string.cd_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (s.journal.habits.isEmpty()) {
            EmptyState("📅", stringResource(R.string.calendar_empty_title), stringResource(R.string.calendar_empty_body), Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CalendarMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size),
                    ) {
                        Text(stringResource(if (mode == CalendarMode.MONTH) R.string.calendar_month else R.string.calendar_overview))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HabitSelector(s, viewModel::selectHabit)
            Spacer(Modifier.height(8.dp))
            when (s.mode) {
                CalendarMode.MONTH -> MonthView(s, viewModel, onOpenDay)
                CalendarMode.OVERVIEW -> s.overview?.let { OverviewView(s, it, viewModel, onOpenHabit) }
            }
        }
    }
}

@Composable
private fun HabitSelector(state: CalendarUiState, onSelect: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.selectedHabitId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.calendar_all_habits)) },
        )
        state.journal.habits.forEach { habit ->
            FilterChip(
                selected = state.selectedHabitId == habit.id,
                onClick = { onSelect(habit.id) },
                label = { Text("${habit.emoji} ${habit.name}" + if (habit.isArchived) " (${stringResource(R.string.archived)})" else "") },
            )
        }
    }
}

@Composable
private fun MonthView(state: CalendarUiState, viewModel: CalendarViewModel, onOpenDay: (LocalDate) -> Unit) {
    val journal = state.journal
    MonthHeader(
        month = state.month,
        onPrevious = { viewModel.shiftMonth(-1) },
        onNext = { viewModel.shiftMonth(1) },
        canGoNext = state.month.atEndOfMonth() < journal.today,
    )
    val selected = state.selectedHabit
    MonthGrid(state.month, journal.settings.firstDayOfWeek) { date, modifier ->
        if (selected != null) {
            val history = journal.history(selected.id)!!
            val status = history.status(date)
            val entry = history.entry(date)
            val value = entry?.quantity?.takeIf { selected.trackingMode != TrackingMode.BINARY }?.let {
                if (selected.trackingMode == TrackingMode.DURATION) "${it.roundToInt()}′" else Fmt.number(it)
            }
            StatusDayCell(date, status, value, date == journal.today, statusText(status), modifier) { onOpenDay(date) }
        } else {
            val statuses = journal.activeHabits.mapNotNull { h ->
                journal.history(h.id)?.let { HabitPalette.color(h.colorIndex) to it.status(date) }
            }
            AggregateDayCell(date, statuses, date == journal.today, date.isAfter(journal.today), modifier) { onOpenDay(date) }
        }
    }
    Spacer(Modifier.height(8.dp))
    StatusLegend()
    if (selected == null) {
        Text(
            stringResource(R.string.calendar_all_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StatusLegend() {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(DayStatus.SUCCESS, DayStatus.FAILURE, DayStatus.MISSING, DayStatus.PENDING).forEach { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                fr.lifecontrol.app.ui.components.StatusDot(status, 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(statusText(status), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun OverviewView(
    state: CalendarUiState,
    overview: OverviewState,
    viewModel: CalendarViewModel,
    onOpenHabit: (String) -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    val journal = state.journal

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeriodType.entries.forEach { type ->
            FilterChip(
                selected = overview.period.type == type,
                onClick = { if (type == PeriodType.CUSTOM) showRangePicker = true else viewModel.setPeriodType(type) },
                label = { Text(periodTypeLabel(type)) },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.shiftPeriod(-1) }, enabled = overview.period.type != PeriodType.ALL) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.cd_previous))
        }
        Text(
            periodLabel(overview.period),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { viewModel.shiftPeriod(1) }, enabled = overview.canGoNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.cd_next))
        }
    }

    StatRow { m ->
        StatTile(stringResource(R.string.stat_success_rate), Fmt.percent(overview.globalRate), m, accent = MaterialTheme.colorScheme.primary)
        StatTile(stringResource(R.string.stat_vs_previous), deltaText(overview.globalRate, overview.previousGlobalRate), m)
    }

    SectionTitle(stringResource(R.string.overview_heatmap))
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            if (state.selectedHabit != null) {
                val history = journal.history(state.selectedHabit!!.id)!!
                val colors = LocalStatusColors.current
                val empty = MaterialTheme.colorScheme.surfaceContainerHighest
                Heatmap(overview.period.from, overview.period.to, journal.settings.firstDayOfWeek, { date ->
                    when (val st = history.status(date)) {
                        DayStatus.NEUTRAL, DayStatus.FUTURE -> empty
                        else -> statusColor(st, colors)
                    }
                })
            } else {
                Heatmap(overview.period.from, overview.period.to, journal.settings.firstDayOfWeek, aggregateColorFn(overview.aggregates))
                Spacer(Modifier.height(8.dp))
                HeatmapLegend()
            }
        }
    }

    if (state.selectedHabit == null && overview.habitStats.isNotEmpty()) {
        SectionTitle(stringResource(R.string.overview_pixels))
        HabitPixelsLegend(MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        overview.habitStats.forEach { item ->
            val habitColor = HabitPalette.color(item.habit.colorIndex)
            Card(
                onClick = { onOpenHabit(item.habit.id) },
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.habit.emoji, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                        Text(item.habit.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(Fmt.percent(item.stats.rate), style = MaterialTheme.typography.labelLarge, color = habitColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    HabitPixels(
                        overview.period.from, overview.period.to, journal.settings.firstDayOfWeek,
                        habitColor = habitColor,
                        status = item.history::status,
                    )
                }
            }
        }
    }

    if (overview.chart.isNotEmpty()) {
        SectionTitle(
            if (overview.chartIsRate) stringResource(R.string.overview_chart_rate)
            else stringResource(R.string.overview_chart_value, state.selectedHabit?.let { if (it.trackingMode == TrackingMode.DURATION) "min" else it.unit } ?: ""),
        )
        BarChart(
            bars = overview.chart.map { it.label to it.value },
            color = state.selectedHabit?.let { HabitPalette.color(it.colorIndex) } ?: MaterialTheme.colorScheme.primary,
            reference = overview.chartReference,
            fixedMax = if (overview.chartIsRate) 100f else null,
        )
    }

    SectionTitle(stringResource(R.string.overview_by_habit))
    overview.habitStats.forEach { item ->
        Card(
            onClick = { onOpenHabit(item.habit.id) },
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiCircle(item.habit.emoji, HabitPalette.color(item.habit.colorIndex), 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(item.habit.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Fmt.percent(item.stats.rate), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                HabitStatsBlock(item.habit, item.history.streak, item.stats, item.previous, journal.settings.currency)
            }
        }
    }

    if (overview.failuresByTrigger.isNotEmpty() || overview.failuresByWeekday.any { it.second > 0 }) {
        SectionTitle(stringResource(R.string.overview_slips))
        val failure = LocalStatusColors.current.failure
        if (overview.failuresByTrigger.isNotEmpty()) {
            Text(stringResource(R.string.overview_triggers), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            HorizontalBars(overview.failuresByTrigger, failure)
            Spacer(Modifier.height(12.dp))
        }
        Text(stringResource(R.string.overview_weekdays), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        HorizontalBars(overview.failuresByWeekday.map { Fmt.weekdayShort(it.first) to it.second }, failure)
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = overview.period.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = minOf(overview.period.to, journal.today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val from = Instant.ofEpochMilli(pickerState.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val to = Instant.ofEpochMilli(pickerState.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.setCustomRange(from, to)
                        showRangePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun periodTypeLabel(type: PeriodType): String = stringResource(
    when (type) {
        PeriodType.WEEK -> R.string.period_week
        PeriodType.MONTH -> R.string.period_month
        PeriodType.QUARTER -> R.string.period_quarter
        PeriodType.YEAR -> R.string.period_year
        PeriodType.ALL -> R.string.period_all
        PeriodType.CUSTOM -> R.string.period_custom
    },
)

fun periodLabel(period: Period): String = when (period.type) {
    PeriodType.MONTH -> Fmt.month(java.time.YearMonth.from(period.from))
    PeriodType.YEAR -> period.from.year.toString()
    PeriodType.QUARTER -> "T${(period.from.monthValue - 1) / 3 + 1} ${period.from.year}"
    else -> "${Fmt.dayShort(period.from)} – ${Fmt.dayShort(period.to)}"
}

@Composable
fun deltaText(current: Double?, previous: Double?): String {
    if (current == null || previous == null) return "—"
    val delta = ((current - previous) * 100).roundToInt()
    return when {
        delta > 0 -> "↑ +$delta pts"
        delta < 0 -> "↓ $delta pts"
        else -> "= 0 pt"
    }
}
