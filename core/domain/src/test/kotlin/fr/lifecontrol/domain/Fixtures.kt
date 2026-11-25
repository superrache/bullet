package fr.lifecontrol.domain

import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Entry
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Goal
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import java.time.LocalDate

/** Lundi 2 novembre 2026. */
val MONDAY: LocalDate = LocalDate.of(2026, 11, 2)

fun habit(
    direction: Direction = Direction.QUIT,
    mode: TrackingMode = TrackingMode.BINARY,
    frequency: Frequency = Frequency.Daily,
    threshold: Double? = null,
    start: LocalDate = MONDAY,
    passive: Boolean = false,
    archivedAt: LocalDate? = null,
    templateId: String? = null,
    unitCostCents: Long? = null,
    baseline: Double? = null,
    goals: List<Goal>? = null,
) = Habit(
    id = "h1",
    templateId = templateId,
    name = "Test",
    emoji = "🚭",
    colorIndex = 0,
    direction = direction,
    trackingMode = mode,
    unit = null,
    startDate = start,
    unitCostCents = unitCostCents,
    baselinePerDay = baseline,
    motivation = null,
    passiveMode = passive,
    sortOrder = 0,
    archivedAt = archivedAt,
    goals = goals ?: listOf(Goal(start, frequency, threshold)),
)

fun done(date: LocalDate, value: Boolean = true, note: String? = null) =
    Entry(id = "e$date", habitId = "h1", date = date, done = value, note = note)

fun qty(date: LocalDate, value: Double, trigger: String? = null) =
    Entry(id = "e$date", habitId = "h1", date = date, quantity = value, triggerId = trigger)

fun LocalDate.days(n: Long): LocalDate = plusDays(n)
