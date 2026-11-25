package fr.lifecontrol.domain

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.domain.eval.Aggregator
import fr.lifecontrol.domain.eval.HabitHistory
import fr.lifecontrol.domain.eval.StatsCalculator
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.TrackingMode
import org.junit.Test
import java.time.DayOfWeek

class StatsCalculatorTest {

    @Test
    fun RG09_rateExcludesNeutralFutureAndBeforeStart() {
        val today = MONDAY.days(4)
        val h = habit(start = MONDAY.days(1))
        val entries = listOf(done(MONDAY.days(1)), done(MONDAY.days(2), false), done(MONDAY.days(3)))
        val stats = StatsCalculator.compute(HabitHistory(h, entries, today), MONDAY, MONDAY.days(30))
        assertThat(stats.successes).isEqualTo(2)
        assertThat(stats.failures).isEqualTo(1)
        assertThat(stats.missing).isEqualTo(0)
        assertThat(stats.rate).isWithin(1e-9).of(2.0 / 3)
    }

    @Test
    fun RG09_missingDaysCountAgainstRate() {
        val stats = StatsCalculator.compute(HabitHistory(habit(), listOf(done(MONDAY)), MONDAY.days(2)), MONDAY, MONDAY.days(2))
        assertThat(stats.missing).isEqualTo(1)
        assertThat(stats.rate).isWithin(1e-9).of(0.5)
    }

    @Test
    fun RG10_savingsQuantity() {
        val h = habit(mode = TrackingMode.QUANTITY, unitCostCents = 60, baseline = 10.0)
        val entries = listOf(qty(MONDAY, 0.0), qty(MONDAY.days(1), 4.0), qty(MONDAY.days(2), 12.0))
        val stats = StatsCalculator.compute(HabitHistory(h, entries, MONDAY.days(3)), MONDAY, MONDAY.days(3))
        // 10 + 6 + 0 (jamais négatif)
        assertThat(stats.unitsAvoided).isWithin(1e-9).of(16.0)
        assertThat(stats.savingsCents).isEqualTo(960)
    }

    @Test
    fun RG10_savingsBinary() {
        val h = habit(unitCostCents = 100, baseline = 5.0)
        val entries = listOf(done(MONDAY), done(MONDAY.days(1), false))
        val stats = StatsCalculator.compute(HabitHistory(h, entries, MONDAY.days(2)), MONDAY, MONDAY.days(2))
        assertThat(stats.savingsCents).isEqualTo(500)
    }

    @Test
    fun totalsAndTriggers() {
        val h = habit(direction = Direction.LIMIT, mode = TrackingMode.QUANTITY, threshold = 2.0)
        val entries = listOf(qty(MONDAY, 1.0), qty(MONDAY.days(1), 4.0, "stress"), qty(MONDAY.days(2), 3.0, "stress"))
        val stats = StatsCalculator.compute(HabitHistory(h, entries, MONDAY.days(3)), MONDAY, MONDAY.days(3))
        assertThat(stats.total).isEqualTo(8.0)
        assertThat(stats.average).isWithin(1e-9).of(8.0 / 3)
        assertThat(stats.failuresByTrigger).containsExactly("stress", 2)
        assertThat(stats.failuresByWeekday).containsExactly(DayOfWeek.TUESDAY, 1, DayOfWeek.WEDNESDAY, 1)
    }

    @Test
    fun weeklyStatsCountWeeks() {
        val h = habit(direction = Direction.START, frequency = Frequency.TimesPerWeek(1))
        val entries = listOf(done(MONDAY), done(MONDAY.days(14)))
        val stats = StatsCalculator.compute(HabitHistory(h, entries, MONDAY.days(21)), MONDAY, MONDAY.days(20))
        assertThat(stats.weekly).isTrue()
        assertThat(stats.successes).isEqualTo(2)
        assertThat(stats.failures).isEqualTo(1)
    }

    @Test
    fun dailyAggregate() {
        val today = MONDAY.days(1)
        val a = HabitHistory(habit(), listOf(done(MONDAY)), today)
        val b = HabitHistory(habit().copy(id = "h2"), emptyList(), today)
        val agg = Aggregator.daily(listOf(a, b), MONDAY, today.days(1), today)
        assertThat(agg[0].ratio).isWithin(1e-9).of(0.5)
        assertThat(agg[1].ratio).isNull() // aujourd'hui : en attente
        assertThat(agg[2].future).isTrue()
    }
}
