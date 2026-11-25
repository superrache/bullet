package fr.lifecontrol.app.data.cards

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Singleton

/**
 * Wikipédia / Commons simulés : chaque titre demandé existe, avec une image libre et un nombre de consultations
 * fixé par [views] (par défaut, selon la longueur du titre). Aucun accès réseau dans les tests.
 */
class FakeWikimedia(
    var offline: Boolean = false,
    private val views: (String) -> Long = { it.length * 1_000L },
    private val nonFreeImages: Set<String> = emptySet(),
    private val links: List<String> = listOf("Article lié A", "Article lié B", "Article lié C"),
) : HttpFetcher {
    val requests = mutableListOf<String>()

    override suspend fun get(url: String): ByteArray {
        requests += url
        if (offline) throw IOException("hors connexion")
        val params = url.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return when {
            url.startsWith("https://upload.wikimedia.org/") -> image()
            url.startsWith("https://commons.wikimedia.org/") -> imageInfo(params.getValue("titles").split('|'))
            params["prop"] == "links" -> linksOf(params.getValue("titles"))
            url.startsWith("https://fr.wikipedia.org/") -> articles(params.getValue("titles").split('|'))
            else -> throw IOException("URL inattendue : $url")
        }.let { if (it is String) it.toByteArray() else it as ByteArray }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun fileOf(title: String) = "Image_" + title.replace(' ', '_').replace("'", "") + ".jpg"

    private fun articles(titles: List<String>): String = titles.joinToString(",", "{\"query\":{\"pages\":[", "]}}") { t ->
        """{"title":"${esc(t)}","fullurl":"https://fr.wikipedia.org/wiki/${esc(t.replace(' ', '_'))}",
           "description":"sujet de test","extract":"${esc(t)} est un sujet passionnant. On y apprend beaucoup de choses.",
           "pageimage":"${esc(fileOf(t))}","pageprops":{"wikibase_item":"Q${t.hashCode().toLong() and 0xffffff}"},
           "pageviews":{"2026-11-01":${views(t)},"2026-11-02":null}}"""
    }

    private fun imageInfo(files: List<String>): String = files.joinToString(",", "{\"query\":{\"pages\":[", "]}}") { f ->
        val name = f.removePrefix("File:")
        val license = if (nonFreeImages.any { name.contains(it.replace(' ', '_')) }) "Fair use" else "CC BY-SA 4.0"
        """{"title":"${esc(f)}","imageinfo":[{"thumburl":"https://upload.wikimedia.org/test/${esc(name)}",
           "descriptionurl":"https://commons.wikimedia.org/wiki/${esc(f)}",
           "extmetadata":{"LicenseShortName":{"value":"$license"},"Artist":{"value":"<a href=\"x\">Photographe</a> &amp; co"}}}]}"""
    }

    private fun linksOf(title: String): String =
        """{"query":{"pages":[{"title":"${esc(title)}","links":[${links.joinToString(",") { "{\"ns\":0,\"title\":\"${esc(it)}\"}" }},{"ns":0,"title":"1889"}]}]}}"""

    private fun image(): ByteArray =
        FakeWikimedia::class.java.getResourceAsStream("/cards/sample.jpg")!!.use { it.readBytes() }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
object FakeNetworkModule {
    @Provides
    @Singleton
    fun httpFetcher(): HttpFetcher = FakeWikimedia()
}
