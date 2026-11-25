package fr.lifecontrol.domain.model

import java.time.LocalDate

/**
 * Entrée du journal pour une habitude et un jour (RG-01 : une seule par jour).
 *
 * [done] : pour le mode binaire, `true` = objectif respecté (« Fait » pour commencer, « Tenu » pour arrêter),
 * `false` = non fait / craqué.
 * [quantity] : quantité ou minutes.
 */
data class Entry(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val done: Boolean? = null,
    val quantity: Double? = null,
    val isPause: Boolean = false,
    val note: String? = null,
    val mood: Int? = null,
    val triggerId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** L'entrée contient une valeur exploitable pour le calcul du statut. */
    val hasValue: Boolean get() = done != null || quantity != null
}
