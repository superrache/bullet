package fr.lifecontrol.domain

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.domain.badge.BadgeEngine
import fr.lifecontrol.domain.badge.BadgeProgress
import fr.lifecontrol.domain.badge.BadgeRules
import fr.lifecontrol.domain.badge.BadgeSnapshot
import fr.lifecontrol.domain.badge.BadgeUnit
import fr.lifecontrol.domain.eval.HabitHistory
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.TrackingMode
import org.junit.Test
import java.time.LocalDate

class BadgeEngineTest {

    private fun snapshot(history: HabitHistory, articles: Int = 0) =
        BadgeSnapshot(history.today, listOf(history), articles)

    private fun List<BadgeProgress>.of(id: String) = first { it.badgeId == id }

    @Test
    fun progressExposesNextTierAndFraction() {
        val p = BadgeProgress("streak", "h1", current = 5, tiers = listOf(3, 7, 21), unit = BadgeUnit.DAYS)
        assertThat(p.reachedLevel).isEqualTo(1)
        assertThat(p.nextTarget).isEqualTo(7)
        assertThat(p.fraction).isWithin(1e-6f).of(5f / 7)
    }

    @Test
    fun firstStepAndStreak() {
        val today = MONDAY.days(7)
        val entries = (0L..6L).map { done(MONDAY.days(it), note = if (it < 2) "note" else null) }
        val progress = BadgeEngine.evaluate(snapshot(HabitHistory(habit(), entries, today)))
        assertThat(progress.of("first_step").reachedLevel).isEqualTo(1)
        assertThat(progress.of("streak").current).isEqualTo(7)
        assertThat(progress.of("streak").reachedLevel).isEqualTo(2)
        assertThat(progress.of("writer").current).isEqualTo(2)
        assertThat(progress.of("journal").current).isEqualTo(7)
    }

    @Test
    fun perfectMonthRequiresFullCalendarMonth() {
        val start = LocalDate.of(2026, 9, 1)
        val today = LocalDate.of(2026, 11, 3)
        val h = habit(start = start, passive = true)
        val failure = done(LocalDate.of(2026, 10, 10), false)
        val history = HabitHistory(h, listOf(failure), today)
        // Septembre parfait (mode passif), octobre raté, novembre pas terminé.
        assertThat(BadgeRules.perfectMonth.metric(snapshot(history), history)).isEqualTo(1)
    }

    @Test
    fun saverOnlyForQuitOrLimitWithCost() {
        val h = habit(mode = TrackingMode.QUANTITY, unitCostCents = 100, baseline = 10.0)
        val entries = (0L..5L).map { qty(MONDAY.days(it), 0.0) }
        val progress = BadgeEngine.evaluate(snapshot(HabitHistory(h, entries, MONDAY.days(6))))
        assertThat(progress.of("saver").current).isEqualTo(60)
        assertThat(progress.of("saver").reachedLevel).isEqualTo(1)

        val start = habit(direction = Direction.START)
        assertThat(BadgeEngine.evaluate(snapshot(HabitHistory(start, emptyList(), MONDAY))).none { it.badgeId == "saver" }).isTrue()
    }

    @Test
    fun cumulativeDurationInHours() {
        val h = habit(direction = Direction.START, mode = TrackingMode.DURATION, threshold = 30.0)
        val entries = (0L..9L).map { qty(MONDAY.days(it), 60.0) }
        val progress = BadgeEngine.evaluate(snapshot(HabitHistory(h, entries, MONDAY.days(10))))
        assertThat(progress.of("cumulative").current).isEqualTo(10)
        assertThat(progress.of("cumulative").unit).isEqualTo(BadgeUnit.HOURS)
    }

    @Test
    fun thematicBadgeOnlyForMatchingTemplate() {
        val smoking = HabitHistory(habit(templateId = "smoking"), emptyList(), MONDAY)
        val other = HabitHistory(habit(templateId = "alcohol"), emptyList(), MONDAY)
        assertThat(BadgeEngine.evaluate(snapshot(smoking)).any { it.badgeId == "lungs_free" }).isTrue()
        assertThat(BadgeEngine.evaluate(snapshot(other)).any { it.badgeId == "lungs_free" }).isFalse()
    }

    @Test
    fun anniversaryAndCurious() {
        val h = habit(start = MONDAY.minusYears(1))
        val progress = BadgeEngine.evaluate(snapshot(HabitHistory(h, emptyList(), MONDAY), articles = 6))
        assertThat(progress.of("anniversary").current).isEqualTo(1)
        assertThat(progress.of("curious").reachedLevel).isEqualTo(1)
    }
}
