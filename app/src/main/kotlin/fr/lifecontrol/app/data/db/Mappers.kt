package fr.lifecontrol.app.data.db

import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Entry
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Goal
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import java.time.DayOfWeek

fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

const val ALL_DAYS_MASK = 0b1111111

fun GoalEntity.toDomain(): Goal = Goal(
    validFrom = validFrom,
    frequency = when (frequency) {
        "TIMES_PER_WEEK" -> Frequency.TimesPerWeek(timesPerWeek ?: 1)
        "DAYS_OF_WEEK" -> (daysOfWeekMask ?: ALL_DAYS_MASK).toDays()
            .takeIf { it.isNotEmpty() }?.let { Frequency.DaysOfWeek(it) } ?: Frequency.Daily
        else -> Frequency.Daily
    },
    threshold = threshold,
)

fun Goal.toEntity(id: String, habitId: String) = GoalEntity(
    id = id,
    habitId = habitId,
    validFrom = validFrom,
    frequency = when (frequency) {
        Frequency.Daily -> "DAILY"
        is Frequency.TimesPerWeek -> "TIMES_PER_WEEK"
        is Frequency.DaysOfWeek -> "DAYS_OF_WEEK"
    },
    timesPerWeek = (frequency as? Frequency.TimesPerWeek)?.times,
    daysOfWeekMask = (frequency as? Frequency.DaysOfWeek)?.days?.toMask(),
    threshold = threshold,
)

fun HabitEntity.toDomain(goals: List<GoalEntity>): Habit = Habit(
    id = id,
    templateId = templateId,
    name = name,
    emoji = emoji,
    colorIndex = colorIndex,
    direction = Direction.valueOf(direction),
    trackingMode = TrackingMode.valueOf(trackingMode),
    unit = unit,
    startDate = startDate,
    unitCostCents = unitCostCents,
    baselinePerDay = baselinePerDay,
    motivation = motivation,
    passiveMode = passiveMode,
    sortOrder = sortOrder,
    archivedAt = archivedAt,
    goals = goals.map { it.toDomain() }.ifEmpty { listOf(Goal(startDate, Frequency.Daily, null)) },
    step = step,
)

fun EntryEntity.toDomain() = Entry(
    id = id,
    habitId = habitId,
    date = date,
    done = done,
    quantity = quantity,
    isPause = isPause,
    note = note,
    mood = mood,
    triggerId = triggerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Entry.toEntity() = EntryEntity(
    id = id,
    habitId = habitId,
    date = date,
    done = done,
    quantity = quantity,
    isPause = isPause,
    note = note,
    mood = mood,
    triggerId = triggerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
