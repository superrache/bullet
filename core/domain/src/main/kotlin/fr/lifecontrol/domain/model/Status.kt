package fr.lifecontrol.domain.model

import java.time.LocalDate

enum class DayStatus {
    /** Objectif atteint. */
    SUCCESS,
    /** Écart. */
    FAILURE,
    /** Jour passé, planifié, non renseigné (RG-04). */
    MISSING,
    /** Aujourd'hui, pas encore atteint ni raté : la journée n'est pas finie (RG-07). */
    PENDING,
    /** Non planifié, en pause, hors période de suivi (RG-05). */
    NEUTRAL,
    FUTURE,
}

enum class WeekStatus { SUCCESS, FAILURE, IN_PROGRESS, NEUTRAL }

data class DayRecord(val date: LocalDate, val status: DayStatus, val entry: Entry?)

data class WeekRecord(
    val weekStart: LocalDate,
    val successes: Int,
    val target: Int,
    val status: WeekStatus,
)

enum class StreakUnit { DAYS, WEEKS }

data class Streak(val current: Int, val best: Int, val unit: StreakUnit)
