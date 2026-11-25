package fr.lifecontrol.app.data.cards

import fr.lifecontrol.app.data.content.CardRarity
import fr.lifecontrol.app.data.content.CardTheme
import fr.lifecontrol.app.data.content.CardThemeRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Carte fraîchement générée à partir de Wikipédia. */
data class GeneratedCard(
    val qid: String,
    val title: String,
    val themeId: String,
    val themeLabel: String,
    val summary: String,
    val text: String,
    val url: String,
    /** Consultations de l'article sur 30 jours : sa popularité du moment. */
    val views: Long,
    val imageUrl: String,
    val imageArtist: String,
    val imageLicense: String,
    val imageLicenseUrl: String?,
    val imagePage: String?,
    val imageBytes: ByteArray,
)

class NoCardAvailableException : IOException("Aucun article utilisable trouvé")

/**
 * Génère une carte en direct (3 à 4 requêtes) :
 *  1. tire un thème, puis une douzaine de sujets pas encore obtenus (ou, si le thème est épuisé,
 *     des articles liés depuis ses sujets : le paquet est sans fin) ;
 *  2. récupère en une requête leur introduction, leur image principale libre et leurs consultations
 *     sur 30 jours ; la rareté choisit dans quelle tranche de popularité piocher (légendaire = les plus consultés) ;
 *  3. vérifie la licence et l'auteur de l'image sur Wikimedia Commons ;
 *  4. télécharge la vignette.
 * Aucune donnée de l'utilisateur n'est transmise : seulement des titres d'articles.
 */
@Singleton
class CardGenerator @Inject constructor(
    private val http: HttpFetcher,
    private val themeRepository: CardThemeRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param owned titres et identifiants Wikidata déjà obtenus (jamais deux fois la même carte).
     * @throws IOException hors connexion ou si aucun article ne convient.
     */
    suspend fun generate(rarity: CardRarity, owned: Set<String>, random: Random): GeneratedCard {
        val themes = themeRepository.themes.filter { it.titles.isNotEmpty() }
        if (themes.isEmpty()) throw NoCardAvailableException()
        val tried = mutableSetOf<String>()
        repeat(3) {
            val theme = pickTheme(themes.filter { it.id !in tried }.ifEmpty { themes }, random)
            tried += theme.id
            generateFrom(theme, rarity, owned, random)?.let { return it }
        }
        throw NoCardAvailableException()
    }

    /** Thème tiré au prorata du nombre de sujets (les thèmes les plus fournis sortent plus souvent). */
    private fun pickTheme(themes: List<CardTheme>, random: Random): CardTheme {
        var r = random.nextInt(themes.sumOf { it.titles.size })
        for (t in themes) {
            if (r < t.titles.size) return t
            r -= t.titles.size
        }
        return themes.last()
    }

    private suspend fun generateFrom(theme: CardTheme, rarity: CardRarity, owned: Set<String>, random: Random): GeneratedCard? {
        val ownedLower = owned.map { it.lowercase() }.toSet()
        val titles = theme.titles.filter { it.lowercase() !in ownedLower }.shuffled(random).take(SAMPLE).toMutableList()
        if (titles.size < SAMPLE / 2) {
            // Thème presque épuisé : on continue avec les articles liés à l'un de ses sujets.
            val seed = theme.titles[random.nextInt(theme.titles.size)]
            titles += linkedTitles(seed).filter { it.lowercase() !in ownedLower && it !in titles }
                .shuffled(random).take(SAMPLE - titles.size)
        }
        if (titles.isEmpty()) return null

        val candidates = fetchArticles(titles).filter { it.qid.lowercase() !in ownedLower && it.title.lowercase() !in ownedLower }
        if (candidates.isEmpty()) return null
        val ordered = orderForRarity(candidates.sortedByDescending { it.views }, rarity, random)
        val images = fetchImageInfo(ordered.map { it.image })
        val chosen = ordered.firstOrNull { images[it.image.normalizeFile()] != null } ?: return null
        val image = images.getValue(chosen.image.normalizeFile())
        val bytes = http.get(image.thumbUrl)
        return GeneratedCard(
            qid = chosen.qid,
            title = chosen.title,
            themeId = theme.id,
            themeLabel = theme.displayName,
            summary = chosen.description,
            text = chosen.extract,
            url = chosen.url,
            views = chosen.views,
            imageUrl = image.thumbUrl,
            imageArtist = image.artist,
            imageLicense = image.license,
            imageLicenseUrl = image.licenseUrl,
            imagePage = image.page,
            imageBytes = bytes,
        )
    }

    /** Téléchargement d'une image de carte (retéléchargement après un import). */
    suspend fun fetchImage(url: String): ByteArray = http.get(url)

    private data class Article(
        val title: String,
        val qid: String,
        val description: String,
        val extract: String,
        val url: String,
        val image: String,
        val views: Long,
    )

    private data class ImageInfo(
        val thumbUrl: String,
        val artist: String,
        val license: String,
        val licenseUrl: String?,
        val page: String?,
    )

    private suspend fun linkedTitles(title: String): List<String> {
        val url = WIKI_API + query(
            "action" to "query", "format" to "json", "formatversion" to "2", "redirects" to "1",
            "prop" to "links", "plnamespace" to "0", "pllimit" to "max", "titles" to title,
        )
        val pages = parse(url).obj("query")?.arr("pages") ?: return emptyList()
        return pages.flatMap { p -> p.asObj()?.arr("links").orEmpty().mapNotNull { it.asObj()?.str("title") } }
            // Écarte dates, années et listes : peu de chances d'en faire une belle carte.
            .filterNot { it.first().isDigit() || it.startsWith("Liste ") || it.contains("siècle") }
    }

    private suspend fun fetchArticles(titles: List<String>): List<Article> {
        val url = WIKI_API + query(
            "action" to "query", "format" to "json", "formatversion" to "2", "redirects" to "1",
            "prop" to "extracts|pageimages|pageprops|description|info|pageviews",
            "exintro" to "1", "explaintext" to "1", "exlimit" to "max",
            "piprop" to "name", "pilicense" to "free", "pilimit" to "max",
            "ppprop" to "wikibase_item|disambiguation", "inprop" to "url", "pvipdays" to "30",
            "titles" to titles.joinToString("|"),
        )
        val pages = parse(url).obj("query")?.arr("pages") ?: return emptyList()
        return pages.mapNotNull { it.asObj() }.mapNotNull { p ->
            val props = p.obj("pageprops")
            if (p.containsKey("missing") || props == null || props.containsKey("disambiguation")) return@mapNotNull null
            val extract = trimSentences(p.str("extract").orEmpty(), TEXT_MAX)
            Article(
                title = p.str("title") ?: return@mapNotNull null,
                qid = props.str("wikibase_item") ?: return@mapNotNull null,
                description = trimSentences(p.str("description").orEmpty(), 90),
                extract = extract.ifBlank { return@mapNotNull null },
                url = p.str("fullurl") ?: return@mapNotNull null,
                image = p.str("pageimage") ?: return@mapNotNull null,
                views = p.obj("pageviews")?.values?.sumOf { (it as? JsonPrimitive)?.longOrNull ?: 0L } ?: 0L,
            )
        }
    }

    private suspend fun fetchImageInfo(files: List<String>): Map<String, ImageInfo> {
        if (files.isEmpty()) return emptyMap()
        val url = COMMONS_API + query(
            "action" to "query", "format" to "json", "formatversion" to "2", "prop" to "imageinfo",
            "iiprop" to "url|extmetadata", "iiurlwidth" to THUMB_WIDTH.toString(),
            "titles" to files.distinct().take(50).joinToString("|") { "File:$it" },
        )
        val data = parse(url).obj("query") ?: return emptyMap()
        val alias = data.arr("normalized").orEmpty().mapNotNull { it.asObj() }
            .associate { it.str("to").orEmpty() to it.str("from").orEmpty() }
        val result = mutableMapOf<String, ImageInfo>()
        for (page in data.arr("pages").orEmpty().mapNotNull { it.asObj() }) {
            val info = page.arr("imageinfo")?.firstOrNull()?.asObj() ?: continue
            val title = page.str("title") ?: continue
            val meta = info.obj("extmetadata")
            fun metaValue(key: String) = meta?.obj(key)?.str("value")?.let(::stripHtml)?.takeIf { it.isNotBlank() }
            val license = metaValue("LicenseShortName") ?: continue
            if (!isFreeLicense(license)) continue
            val name = (alias[title] ?: title).substringAfter(':')
            result[name.normalizeFile()] = ImageInfo(
                thumbUrl = info.str("thumburl") ?: info.str("url") ?: continue,
                artist = (metaValue("Artist") ?: "Auteur inconnu").replace(Regex("\\s+"), " ").take(120),
                license = license,
                licenseUrl = metaValue("LicenseUrl"),
                page = info.str("descriptionurl"),
            )
        }
        return result
    }

    private suspend fun parse(url: String): JsonObject =
        try {
            json.parseToJsonElement(http.get(url).decodeToString()) as? JsonObject ?: JsonObject(emptyMap())
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Réponse illisible", e)
        }

    companion object {
        private const val WIKI_API = "https://fr.wikipedia.org/w/api.php?"
        private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php?"
        private const val SAMPLE = 12
        private const val TEXT_MAX = 700

        /** Wikimedia ne sert que des largeurs de vignette standard ; l'image est réduite ensuite. */
        const val THUMB_WIDTH = 960

        private val FREE_LICENSE = Regex("(^|\\b)(CC[ -]?BY|CC0|Public domain|Domaine public|PD|GFDL|FAL|Attribution)", RegexOption.IGNORE_CASE)

        fun isFreeLicense(license: String): Boolean = FREE_LICENSE.containsMatchIn(license) && !license.contains("fair", ignoreCase = true)

        /**
         * Ordre d'essai selon la rareté, parmi des candidats triés du plus au moins consulté :
         * légendaire pioche dans le premier sixième, rare dans la première moitié, commune dans la seconde.
         * Les autres suivent (au cas où l'image d'un candidat serait inutilisable), du plus proche au plus lointain.
         */
        internal fun <T> orderForRarity(sorted: List<T>, rarity: CardRarity, random: Random): List<T> {
            val n = sorted.size
            val legendaryEnd = maxOf(1, n / 6)
            val band = when (rarity) {
                CardRarity.LEGENDARY -> 0 until legendaryEnd
                CardRarity.RARE -> legendaryEnd until maxOf(legendaryEnd + 1, n / 2)
                CardRarity.COMMON -> (n / 2).coerceAtMost(n - 1) until n
            }
            val inBand = band.filter { it in sorted.indices }.shuffled(random)
            val center = if (inBand.isEmpty()) 0 else band.first
            val rest = sorted.indices.filter { it !in inBand }.sortedBy { kotlin.math.abs(it - center) }
            return (inBand + rest).map { sorted[it] }
        }

        internal fun trimSentences(input: String, limit: Int): String {
            val text = input.replace(Regex("\\s*\\[[^\\]]*]"), "").replace(Regex("\\s+"), " ").trim()
            if (text.length <= limit) return text
            val cut = text.substring(0, limit)
            val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "))
            return if (end > limit * 0.4) cut.substring(0, end + 1) else cut.substringBeforeLast(' ') + "…"
        }

        internal fun stripHtml(s: String): String = s.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace("&lt;", "<").replace("&gt;", ">").trim()

        private fun String.normalizeFile() = replace(' ', '_')

        private fun query(vararg params: Pair<String, String>): String =
            params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

        private fun JsonElement.asObj(): JsonObject? = this as? JsonObject
        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
    }
}
