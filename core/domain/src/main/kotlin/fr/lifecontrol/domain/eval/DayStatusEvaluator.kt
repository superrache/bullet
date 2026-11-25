package fr.lifecontrol.domain.eval

import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Entry
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Goal
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import java.time.LocalDate

/** Résultat d'une entrée au regard de l'objectif, indépendamment du jour. */
enum class Outcome { MET, NOT_MET, IN_PROGRESS }

/** Application des règles RG-03 à RG-05 et RG-08. */
object DayStatusEvaluator {

    fun outcome(habit: Habit, goal: Goal, entry: Entry): Outcome? {
        if (!entry.hasValue) return null
        return when (habit.direction) {
            Direction.QUIT -> when (habit.trackingMode) {
                TrackingMode.BINARY -> if (entry.done == true) Outcome.MET else Outcome.NOT_MET
                // Sans quantité (saisie faite en oui/non avant un changement de mode) : « craqué » = au moins un.
                else -> {
                    val q = entry.quantity ?: if (entry.done == true) 0.0 else 1.0
                    if (q <= 0.0) Outcome.MET else Outcome.NOT_MET
                }
            }
            Direction.LIMIT -> {
                val q = entry.quantity ?: if (entry.done == true) 0.0 else Double.MAX_VALUE
                if (q <= (goal.threshold ?: 0.0)) Outcome.MET else Outcome.NOT_MET
            }
            Direction.START -> when (habit.trackingMode) {
                TrackingMode.BINARY -> if (entry.done == true) Outcome.MET else Outcome.NOT_MET
                else -> {
                    val q = entry.quantity ?: 0.0
                    if (q >= (goal.threshold ?: 1.0)) Outcome.MET else Outcome.IN_PROGRESS
                }
            }
        }
    }

    fun evaluate(habit: Habit, date: LocalDate, entry: Entry?, today: LocalDate): DayStatus {
        if (date.isAfter(today)) return DayStatus.FUTURE
        if (!habit.isActiveOn(date)) return DayStatus.NEUTRAL
        if (entry?.isPause == true) return DayStatus.NEUTRAL

        val goal = habit.goalAt(date)
        val frequency = goal.frequency
        if (frequency is Frequency.DaysOfWeek && date.dayOfWeek !in frequency.days) {
            return DayStatus.NEUTRAL
        }
        val outcome = entry?.let { outcome(habit, goal, it) }

        // N fois par semaine (RG-06) : un jour sans réalisation n'est pas un écart.
        if (frequency is Frequency.TimesPerWeek) {
            return when {
                outcome == Outcome.MET -> DayStatus.SUCCESS
                date == today -> DayStatus.PENDING
                else -> DayStatus.NEUTRAL
            }
        }

        return when (outcome) {
            Outcome.MET -> DayStatus.SUCCESS
            Outcome.NOT_MET -> DayStatus.FAILURE
            Outcome.IN_PROGRESS -> if (date == today) DayStatus.PENDING else DayStatus.FAILURE
            null -> when {
                date == today -> DayStatus.PENDING
                habit.passiveMode && habit.direction != Direction.START -> DayStatus.SUCCESS
                else -> DayStatus.MISSING
            }
        }
    }
}
