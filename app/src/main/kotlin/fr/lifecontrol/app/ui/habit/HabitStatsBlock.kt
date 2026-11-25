package fr.lifecontrol.app.ui.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.lifecontrol.app.R
import fr.lifecontrol.app.ui.calendar.deltaText
import fr.lifecontrol.app.ui.components.StatRow
import fr.lifecontrol.app.ui.components.StatTile
import fr.lifecontrol.app.ui.theme.LocalStatusColors
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.app.ui.util.streakLabel
import fr.lifecontrol.domain.eval.HabitStats
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.Streak
import fr.lifecontrol.domain.model.TrackingMode

/** Indicateurs d'une habitude sur une période (F-14). */
@Composable
fun HabitStatsBlock(habit: Habit, streak: Streak, stats: HabitStats, previous: HabitStats?, currency: String) {
    val colors = LocalStatusColors.current
    Column {
        StatRow { m ->
            StatTile(stringResource(R.string.stat_success_rate), Fmt.percent(stats.rate), m, accent = colors.success)
            StatTile(stringResource(R.string.stat_current_streak), streakLabel(streak.current, streak.unit), m)
            StatTile(stringResource(R.string.stat_best_streak), streakLabel(streak.best, streak.unit), m)
        }
        Spacer(Modifier.height(8.dp))
        val unitRes = if (stats.weekly) R.plurals.weeks_short else R.plurals.days_short
        Text(
            listOfNotNull(
                "✓ " + pluralStringResource(unitRes, stats.successes, stats.successes) + " " + stringResource(R.string.stat_successes),
                "✕ " + pluralStringResource(unitRes, stats.failures, stats.failures) + " " + stringResource(R.string.stat_failures),
                if (!stats.weekly) "– " + pluralStringResource(unitRes, stats.missing, stats.missing) + " " + stringResource(R.string.stat_missing) else null,
            ).joinToString("   "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (previous != null) {
            Text(
                stringResource(R.string.stat_vs_previous) + " : " + deltaText(stats.rate, previous.rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val showTotal = habit.trackingMode != TrackingMode.BINARY || habit.direction == Direction.START
        if (showTotal && stats.recordedDays > 0) {
            Spacer(Modifier.height(8.dp))
            StatRow { m ->
                val total = if (habit.trackingMode == TrackingMode.BINARY) {
                    pluralStringResource(R.plurals.times, stats.total.toInt(), stats.total.toInt())
                } else {
                    Fmt.value(habit, stats.total)
                }
                StatTile(stringResource(R.string.stat_total), total, m)
                if (habit.trackingMode != TrackingMode.BINARY) {
                    StatTile(stringResource(R.string.stat_average), stats.average?.let { Fmt.value(habit, it) } ?: "—", m)
                }
            }
        }
        if (stats.savingsCents != null || stats.unitsAvoided != null) {
            Spacer(Modifier.height(8.dp))
            StatRow { m ->
                stats.savingsCents?.let {
                    StatTile(stringResource(R.string.stat_savings), Fmt.money(it, currency), m, accent = colors.success)
                }
                stats.unitsAvoided?.let {
                    StatTile(stringResource(R.string.stat_avoided), Fmt.value(habit, it), m)
                }
            }
        }
    }
}
