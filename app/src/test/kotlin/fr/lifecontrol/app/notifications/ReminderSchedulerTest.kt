package fr.lifecontrol.app.notifications

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.data.db.ALL_DAYS_MASK
import fr.lifecontrol.app.data.db.toMask
import fr.lifecontrol.app.data.repository.DateProvider
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderSchedulerTest {

    /** Lundi 2 novembre 2026, 10 h. */
    private val now = LocalDateTime.of(2026, 11, 2, 10, 0)

    @Test
    fun laterToday() {
        assertThat(ReminderScheduler.delayUntilNext(20 * 60, ALL_DAYS_MASK, now)).isEqualTo(Duration.ofHours(10))
    }

    @Test
    fun alreadyPassedGoesToTomorrow() {
        assertThat(ReminderScheduler.delayUntilNext(9 * 60, ALL_DAYS_MASK, now)).isEqualTo(Duration.ofHours(23))
    }

    @Test
    fun respectsSelectedDays() {
        val mask = setOf(DayOfWeek.WEDNESDAY).toMask()
        assertThat(ReminderScheduler.delayUntilNext(10 * 60, mask, now)).isEqualTo(Duration.ofDays(2))
    }

    @Test
    fun RG02_logicalDayStartsAtConfiguredHour() {
        val night = LocalDateTime.of(2026, 11, 3, 1, 30)
        assertThat(DateProvider.logicalDate(4, night)).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(DateProvider.logicalDate(0, night)).isEqualTo(LocalDate.of(2026, 11, 3))
    }
}
