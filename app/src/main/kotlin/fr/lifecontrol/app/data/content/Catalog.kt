package fr.lifecontrol.app.data.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Catalog(
    val categories: List<Category>,
    val templates: List<HabitTemplate>,
    val help: List<HelpResource>,
    val badges: List<BadgeMeta>,
    val encouragements: List<String>,
)

@Serializable
data class Category(val id: String, val name: String, val emoji: String)

@Serializable
data class HabitTemplate(
    val id: String,
    val category: String,
    val name: String,
    val emoji: String,
    val color: Int,
    val direction: Direction,
    val mode: TrackingMode,
    val unit: String? = null,
    val step: Double = 1.0,
    val threshold: Double? = null,
    /** DAILY ou TIMES_PER_WEEK */
    val frequency: String = "DAILY",
    val timesPerWeek: Int? = null,
    val unitCostCents: Long? = null,
    val baselinePerDay: Double? = null,
    val summary: String,
    val help: List<String> = emptyList(),
    val article: Article,
) {
    val defaultFrequency: Frequency
        get() = if (frequency == "TIMES_PER_WEEK") Frequency.TimesPerWeek(timesPerWeek ?: 3) else Frequency.Daily
}

@Serializable
data class Article(
    val sections: List<Section>,
    val sources: List<String> = emptyList(),
    val updated: String,
)

@Serializable
data class Section(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
)

@Serializable
data class TimelineItem(val time: String, val text: String)

@Serializable
data class HelpResource(
    val id: String,
    val name: String,
    val description: String,
    val phone: String? = null,
    val url: String? = null,
)

@Serializable
data class BadgeMeta(
    val id: String,
    val emoji: String,
    val title: String,
    /** « {n} » est remplacé par la cible du palier, « {unit} » par l'unité. */
    val description: String,
)

/** Contenus embarqués (§8, §10.6), chargés depuis les assets selon la langue de l'appareil. */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    val catalog: Catalog by lazy { load() }

    private fun load(): Catalog {
        val language = Locale.getDefault().language
        val path = listOf("catalog/$language/catalog.json", "catalog/fr/catalog.json")
            .first { runCatching { context.assets.open(it).close() }.isSuccess }
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        return json.decodeFromString(Catalog.serializer(), text)
    }

    fun template(id: String?): HabitTemplate? = id?.let { catalog.templates.firstOrNull { t -> t.id == id } }

    fun badge(id: String): BadgeMeta? = catalog.badges.firstOrNull { it.id == id }

    fun help(ids: List<String>): List<HelpResource> =
        ids.mapNotNull { id -> catalog.help.firstOrNull { it.id == id } }
}
