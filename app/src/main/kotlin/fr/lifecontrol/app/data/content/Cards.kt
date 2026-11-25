package fr.lifecontrol.app.data.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Rareté d'une carte : elle dépend du taux de réussite de la journée qui l'a fait gagner. */
enum class CardRarity {
    COMMON, RARE, LEGENDARY;

    companion object {
        /** 100 % → légendaire, au moins 50 % → rare, sinon commune. */
        fun forSuccessRate(rate: Double): CardRarity = when {
            rate >= 1.0 -> LEGENDARY
            rate >= 0.5 -> RARE
            else -> COMMON
        }

        fun parse(name: String): CardRarity = entries.firstOrNull { it.name == name } ?: COMMON
    }
}

/** Thème de cartes : une liste de sujets (titres d'articles de Wikipédia fr). Le contenu est récupéré en direct. */
@Serializable
data class CardTheme(
    val id: String,
    val emoji: String,
    val label: String,
    val titles: List<String>,
) {
    val displayName: String get() = "$emoji $label"
}

@Serializable
data class CardThemes(val themes: List<CardTheme>)

@Singleton
open class CardThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    open val themes: List<CardTheme> by lazy {
        runCatching {
            val text = context.assets.open(PATH).bufferedReader().use { it.readText() }
            json.decodeFromString(CardThemes.serializer(), text).themes
        }.getOrElse { emptyList() }
    }

    fun theme(id: String): CardTheme? = themes.firstOrNull { it.id == id }

    companion object {
        const val PATH = "cards/themes.json"
    }
}
