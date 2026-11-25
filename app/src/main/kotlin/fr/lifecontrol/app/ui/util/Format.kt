package fr.lifecontrol.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.lifecontrol.app.R
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.StreakUnit
import fr.lifecontrol.domain.model.TrackingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Fmt {
    private val locale: Locale get() = Locale.getDefault()

    fun number(value: Double): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = if (abs(value - value.roundToInt()) < 0.001) 0 else 1
        return nf.format(value)
    }

    fun percent(rate: Double?): String = rate?.let { "${(it * 100).roundToInt()} %" } ?: "—"

    fun money(cents: Long, currency: String): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = if (cents % 100 == 0L) 0 else 2
        nf.minimumFractionDigits = if (cents % 100 == 0L) 0 else 2
        return "${nf.format(cents / 100.0)} $currency"
    }

    fun minutes(total: Double): String {
        val m = total.roundToInt()
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> "${m / 60} h"
            else -> "${m / 60} h ${(m % 60).toString().padStart(2, '0')}"
        }
    }

    fun time(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun dayLong(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) }

    fun dayShort(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

    fun dayMonth(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    fun month(month: YearMonth): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.titlecase(locale) }

    fun monthShort(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMM", locale))

    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')

    fun weekdayFull(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    fun weekdayNarrow(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)

    /** Valeur d'une habitude dans son unité (« 3 verres », « 1 h 20 »). */
    fun value(habit: Habit, value: Double): String = when (habit.trackingMode) {
        TrackingMode.DURATION -> minutes(value)
        else -> listOfNotNull(number(value), habit.unit).joinToString(" ")
    }

    /** Jours de la semaine dans l'ordre choisi par l'utilisateur. */
    fun orderedWeek(firstDay: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDay.plus(it) }
}

@Composable
fun frequencyLabel(frequency: Frequency): String = when (frequency) {
    Frequency.Daily -> stringResource(R.string.freq_daily)
    is Frequency.TimesPerWeek -> pluralStringResource(R.plurals.freq_times_per_week, frequency.times, frequency.times)
    is Frequency.DaysOfWeek -> if (frequency.days.size == 7) {
        stringResource(R.string.freq_daily)
    } else {
        DayOfWeek.entries.filter { it in frequency.days }.joinToString(", ") { Fmt.weekdayShort(it) }
    }
}

/** Texte de l'objectif courant (« ≤ 2 verres · tous les jours »). */
@Composable
fun goalLabel(habit: Habit, date: LocalDate? = null): String {
    val goal = date?.let(habit::goalAt) ?: habit.currentGoal
    val target = when (habit.direction) {
        Direction.QUIT -> stringResource(R.string.goal_quit)
        Direction.LIMIT -> stringResource(R.string.goal_limit, Fmt.value(habit, goal.threshold ?: 0.0))
        Direction.START -> when (habit.trackingMode) {
            TrackingMode.BINARY -> stringResource(R.string.goal_start_binary)
            else -> stringResource(R.string.goal_start_min, Fmt.value(habit, goal.threshold ?: 1.0))
        }
    }
    return "$target · ${frequencyLabel(goal.frequency)}"
}

@Composable
fun streakLabel(count: Int, unit: StreakUnit): String = when (unit) {
    StreakUnit.DAYS -> pluralStringResource(R.plurals.days, count, count)
    StreakUnit.WEEKS -> pluralStringResource(R.plurals.weeks, count, count)
}
