package fr.lifecontrol.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** Sens d'une habitude (glossaire §2). */
enum class Direction { QUIT, LIMIT, START }

/** Mode de suivi (glossaire §2). */
enum class TrackingMode { BINARY, QUANTITY, DURATION }

sealed interface Frequency {
    data object Daily : Frequency
    data class TimesPerWeek(val times: Int) : Frequency {
        init {
            require(times in 1..7) { "times must be in 1..7" }
        }
    }
    data class DaysOfWeek(val days: Set<DayOfWeek>) : Frequency {
        init {
            require(days.isNotEmpty()) { "days must not be empty" }
        }
    }
}

/**
 * Une version d'objectif, valable à partir de [validFrom] (RG-08).
 * [threshold] : maximum pour [Direction.LIMIT], minimum pour [Direction.START] en quantité/durée.
 */
data class Goal(
    val validFrom: LocalDate,
    val frequency: Frequency,
    val threshold: Double?,
)

data class Habit(
    val id: String,
    val templateId: String?,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val direction: Direction,
    val trackingMode: TrackingMode,
    val unit: String?,
    val startDate: LocalDate,
    val unitCostCents: Long?,
    val baselinePerDay: Double?,
    val motivation: String?,
    val passiveMode: Boolean,
    val sortOrder: Int,
    val archivedAt: LocalDate?,
    /** Versions d'objectif, non vide. */
    val goals: List<Goal>,
    /** Pas d'incrément proposé à la saisie (quantité / minutes). */
    val step: Double = 1.0,
) {
    init {
        require(goals.isNotEmpty()) { "A habit needs at least one goal" }
    }

    private val sortedGoals = goals.sortedBy { it.validFrom }

    /** Objectif en vigueur à [date] (RG-08). Avant la première version, la première s'applique. */
    fun goalAt(date: LocalDate): Goal =
        sortedGoals.lastOrNull { !it.validFrom.isAfter(date) } ?: sortedGoals.first()

    val currentGoal: Goal get() = sortedGoals.last()

    val isArchived: Boolean get() = archivedAt != null

    /** Jour compris entre la date de début et la date d'archivage (exclue). */
    fun isActiveOn(date: LocalDate): Boolean =
        !date.isBefore(startDate) && (archivedAt == null || date.isBefore(archivedAt))

    /** Dernier jour suivi (veille de l'archivage), ou null si jamais archivée. */
    val lastActiveDay: LocalDate? get() = archivedAt?.minusDays(1)
}
