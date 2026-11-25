package fr.lifecontrol.domain

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.domain.eval.DayStatusEvaluator.evaluate
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Entry
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Goal
import fr.lifecontrol.domain.model.TrackingMode
import org.junit.Test
import java.time.DayOfWeek

class DayStatusEvaluatorTest {

    private val today = MONDAY.days(10)

    @Test
    fun RG03_quitBinary() {
        val h = habit()
        assertThat(evaluate(h, MONDAY, done(MONDAY, true), today)).isEqualTo(DayStatus.SUCCESS)
        assertThat(evaluate(h, MONDAY, done(MONDAY, false), today)).isEqualTo(DayStatus.FAILURE)
    }

    @Test
    fun RG03_quitQuantity_zeroIsSuccess() {
        val h = habit(mode = TrackingMode.QUANTITY)
        assertThat(evaluate(h, MONDAY, qty(MONDAY, 0.0), today)).isEqualTo(DayStatus.SUCCESS)
        assertThat(evaluate(h, MONDAY, qty(MONDAY, 1.0), today)).isEqualTo(DayStatus.FAILURE)
    }

    @Test
    fun quitQuantity_binaryEntryWithoutQuantity_keepsItsMeaning() {
        // Habitude passée de oui/non à chiffré : un ancien « craqué » reste un écart.
        val h = habit(mode = TrackingMode.QUANTITY)
        assertThat(evaluate(h, MONDAY, done(MONDAY, true), today)).isEqualTo(DayStatus.SUCCESS)
        assertThat(evaluate(h, MONDAY, done(MONDAY, false), today)).isEqualTo(DayStatus.FAILURE)
    }

    @Test
    fun RG03_limit_thresholdIsInclusive() {
        val h = habit(direction = Direction.LIMIT, mode = TrackingMode.QUANTITY, threshold = 2.0)
        assertThat(evaluate(h, MONDAY, qty(MONDAY, 2.0), today)).isEqualTo(DayStatus.SUCCESS)
        assertThat(evaluate(h, MONDAY, qty(MONDAY, 3.0), today)).isEqualTo(DayStatus.FAILURE)
    }

    @Test
    fun RG03_startDuration_belowThresholdIsPendingTodayAndFailureAfter() {
        val h = habit(direction = Direction.START, mode = TrackingMode.DURATION, threshold = 30.0)
        assertThat(evaluate(h, today, qty(today, 10.0), today)).isEqualTo(DayStatus.PENDING)
        assertThat(evaluate(h, MONDAY, qty(MONDAY, 10.0), today)).isEqualTo(DayStatus.FAILURE)
        assertThat(evaluate(h, MONDAY, qty(MONDAY, 45.0), today)).isEqualTo(DayStatus.SUCCESS)
    }

    @Test
    fun RG04_noEntry_missingInPastPendingToday() {
        val h = habit()
        assertThat(evaluate(h, MONDAY, null, today)).isEqualTo(DayStatus.MISSING)
        assertThat(evaluate(h, today, null, today)).isEqualTo(DayStatus.PENDING)
    }

    @Test
    fun RG04_passiveMode_noEntryIsSuccessForQuitButNotStart() {
        assertThat(evaluate(habit(passive = true), MONDAY, null, today)).isEqualTo(DayStatus.SUCCESS)
        val start = habit(direction = Direction.START, passive = true)
        assertThat(evaluate(start, MONDAY, null, today)).isEqualTo(DayStatus.MISSING)
    }

    @Test
    fun RG04_noteOnlyEntryCountsAsNoEntry() {
        val entry = Entry(id = "x", habitId = "h1", date = MONDAY, note = "hello")
        assertThat(evaluate(habit(), MONDAY, entry, today)).isEqualTo(DayStatus.MISSING)
    }

    @Test
    fun RG05_unscheduledDayIsNeutral() {
        val h = habit(direction = Direction.START, frequency = Frequency.DaysOfWeek(setOf(DayOfWeek.MONDAY)))
        assertThat(evaluate(h, MONDAY.days(1), null, today)).isEqualTo(DayStatus.NEUTRAL)
        assertThat(evaluate(h, MONDAY.days(1), done(MONDAY.days(1)), today)).isEqualTo(DayStatus.NEUTRAL)
        assertThat(evaluate(h, MONDAY, null, today)).isEqualTo(DayStatus.MISSING)
    }

    @Test
    fun RG06_weekly_dayWithoutActivityIsNeutral() {
        val h = habit(direction = Direction.START, frequency = Frequency.TimesPerWeek(3))
        assertThat(evaluate(h, MONDAY, null, today)).isEqualTo(DayStatus.NEUTRAL)
        assertThat(evaluate(h, MONDAY, done(MONDAY), today)).isEqualTo(DayStatus.SUCCESS)
    }

    @Test
    fun RG08_goalVersionAppliesFromItsDate() {
        val h = habit(
            direction = Direction.LIMIT,
            mode = TrackingMode.QUANTITY,
            goals = listOf(
                Goal(MONDAY, Frequency.Daily, 3.0),
                Goal(MONDAY.days(5), Frequency.Daily, 1.0),
            ),
        )
        assertThat(evaluate(h, MONDAY.days(4), qty(MONDAY.days(4), 2.0), today)).isEqualTo(DayStatus.SUCCESS)
        assertThat(evaluate(h, MONDAY.days(5), qty(MONDAY.days(5), 2.0), today)).isEqualTo(DayStatus.FAILURE)
    }

    @Test
    fun RG13_beforeStartNeutral_futureIsFuture_pauseNeutral() {
        val h = habit(start = MONDAY.days(2))
        assertThat(evaluate(h, MONDAY, null, today)).isEqualTo(DayStatus.NEUTRAL)
        assertThat(evaluate(h, today.days(1), null, today)).isEqualTo(DayStatus.FUTURE)
        val pause = Entry(id = "p", habitId = "h1", date = MONDAY.days(3), isPause = true)
        assertThat(evaluate(h, MONDAY.days(3), pause, today)).isEqualTo(DayStatus.NEUTRAL)
    }

    @Test
    fun archivedDaysAreNeutral() {
        val h = habit(archivedAt = MONDAY.days(3))
        assertThat(evaluate(h, MONDAY.days(2), null, today)).isEqualTo(DayStatus.MISSING)
        assertThat(evaluate(h, MONDAY.days(3), null, today)).isEqualTo(DayStatus.NEUTRAL)
    }
}
