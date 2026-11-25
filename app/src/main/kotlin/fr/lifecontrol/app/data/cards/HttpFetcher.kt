package fr.lifecontrol.app.data.cards

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Seul accès réseau de l'application : lecture de Wikipédia / Wikimedia Commons pour les cartes. */
interface HttpFetcher {
    /** @throws IOException en cas d'erreur réseau ou de réponse non 2xx. */
    suspend fun get(url: String): ByteArray
}

@Singleton
class UrlConnectionFetcher @Inject constructor() : HttpFetcher {

    override suspend fun get(url: String): ByteArray {
        // Wikimedia peut demander de ralentir (429/503) : on patiente un peu, deux fois au plus.
        repeat(2) { attempt ->
            try {
                return fetch(url)
            } catch (e: RetryLater) {
                delay(minOf(e.seconds, 5L) * 1000L + attempt * 1000L)
            }
        }
        return fetch(url)
    }

    private class RetryLater(val seconds: Long, code: Int) : IOException("HTTP $code")

    private suspend fun fetch(url: String): ByteArray = withContext(Dispatchers.IO) {
        // HTTPS uniquement, vers Wikimedia uniquement.
        val target = URL(url)
        if (target.protocol != "https") throw IOException("HTTPS requis")
        val host = target.host
        if (ALLOWED_HOSTS.none { host == it || host.endsWith(".$it") }) throw IOException("Hôte non autorisé : $host")
        val connection = target.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            // Identifiant générique (règle d'usage de Wikimedia) : rien sur l'utilisateur.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, image/*")
            val code = connection.responseCode
            if (code == 429 || code == 503) {
                throw RetryLater(connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 2L, code)
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "Bullet/1.0 (Android app; collectible cards from Wikipedia)"
        val ALLOWED_HOSTS = listOf("wikipedia.org", "wikimedia.org")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun httpFetcher(impl: UrlConnectionFetcher): HttpFetcher
}
