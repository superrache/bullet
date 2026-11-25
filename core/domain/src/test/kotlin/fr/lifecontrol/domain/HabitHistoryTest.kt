package fr.lifecontrol.domain

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.domain.eval.HabitHistory
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.StreakUnit
import fr.lifecontrol.domain.model.WeekStatus
import org.junit.Test
import java.time.DayOfWeek

class HabitHistoryTest {

    @Test
    fun RG07_streakNotBrokenBeforeEndOfDay() {
        val today = MONDAY.days(3)
        val entries = (0L..2L).map { done(MONDAY.days(it)) }
        val streak = HabitHistory(habit(), entries, today).streak
        assertThat(streak.current).isEqualTo(3)
        assertThat(streak.best).isEqualTo(3)
        assertThat(streak.unit).isEqualTo(StreakUnit.DAYS)
    }

    @Test
    fun RG07_failureResetsCurrentButKeepsBest() {
        val today = MONDAY.days(6)
        val entries = listOf(
            done(MONDAY), done(MONDAY.days(1)), done(MONDAY.days(2)),
            done(MONDAY.days(3), false),
            done(MONDAY.days(4)), done(MONDAY.days(5)),
        )
        val streak = HabitHistory(habit(), entries, today).streak
        assertThat(streak.current).isEqualTo(2)
        assertThat(streak.best).isEqualTo(3)
    }

    @Test
    fun RG07_missingDayBreaksStreak() {
        val today = MONDAY.days(4)
        val entries = listOf(done(MONDAY), done(MONDAY.days(2)), done(MONDAY.days(3)))
        assertThat(HabitHistory(habit(), entries, today).streak.current).isEqualTo(2)
    }

    @Test
    fun RG05_neutralDaysDoNotBreakStreak() {
        val h = habit(direction = Direction.START, frequency = Frequency.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val today = MONDAY.days(7) // lundi suivant
        val entries = listOf(done(MONDAY), done(MONDAY.days(2)))
        val streak = HabitHistory(h, entries, today).streak
        assertThat(streak.current).isEqualTo(2)
    }

    @Test
    fun RG06_weeklyStreakInWeeks_currentWeekNotFailedWhileOpen() {
        val h = habit(direction = Direction.START, frequency = Frequency.TimesPerWeek(2))
        val today = MONDAY.days(15) // mardi de la 3e semaine
        val entries = listOf(
            done(MONDAY), done(MONDAY.days(3)), // semaine 1 : 2/2
            done(MONDAY.days(8)), done(MONDAY.days(9)), // semaine 2 : 2/2
        )
        val history = HabitHistory(h, entries, today)
        assertThat(history.allWeeks.map { it.status })
            .containsExactly(WeekStatus.SUCCESS, WeekStatus.SUCCESS, WeekStatus.IN_PROGRESS).inOrder()
        assertThat(history.streak.current).isEqualTo(2)
        assertThat(history.streak.unit).isEqualTo(StreakUnit.WEEKS)
    }

    @Test
    fun RG06_weeklyFailedWeekBreaksStreak() {
        val h = habit(direction = Direction.START, frequency = Frequency.TimesPerWeek(3))
        val today = MONDAY.days(14)
        val entries = listOf(done(MONDAY), done(MONDAY.days(1)), done(MONDAY.days(2)), done(MONDAY.days(8)))
        val history = HabitHistory(h, entries, today)
        assertThat(history.streak.current).isEqualTo(0)
        assertThat(history.streak.best).isEqualTo(1)
    }

    @Test
    fun RG06_partialFirstWeekTargetIsCapped() {
        val start = MONDAY.days(5) // samedi
        val h = habit(direction = Direction.START, frequency = Frequency.TimesPerWeek(3), start = start)
        val history = HabitHistory(h, listOf(done(start), done(start.days(1))), MONDAY.days(8))
        assertThat(history.allWeeks.first().target).isEqualTo(2)
        assertThat(history.allWeeks.first().status).isEqualTo(WeekStatus.SUCCESS)
    }

    @Test
    fun reboundCountsRecoveriesOfAtLeastThreeDays() {
        val today = MONDAY.days(10)
        val entries = listOf(
            done(MONDAY, false),
            done(MONDAY.days(1)), done(MONDAY.days(2)), done(MONDAY.days(3)),
            done(MONDAY.days(4), false),
            done(MONDAY.days(5)), done(MONDAY.days(6)),
        )
        assertThat(HabitHistory(habit(), entries, today).reboundCount()).isEqualTo(1)
    }
}
