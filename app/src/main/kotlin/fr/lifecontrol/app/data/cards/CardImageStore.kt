package fr.lifecontrol.app.data.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Images des cartes, gardées dans le stockage privé de l'application (consultables hors connexion). */
@Singleton
class CardImageStore @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "cards")

    fun file(cardId: String): File = File(dir, "$cardId.jpg")

    /** Réduit l'image téléchargée (640 px de large au plus) et l'enregistre en JPEG. */
    suspend fun save(cardId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val bitmap = decode(bytes, MAX_WIDTH) ?: throw IOException("Image illisible")
        val scaled = if (bitmap.width > MAX_WIDTH) {
            Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, bitmap.height * MAX_WIDTH / bitmap.width, true)
        } else bitmap
        dir.mkdirs()
        val tmp = File(dir, "$cardId.tmp")
        tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (!tmp.renameTo(file(cardId))) throw IOException("Enregistrement impossible")
    }

    suspend fun load(cardId: String, maxWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(cardId)
        if (!f.exists()) null else decode(f.readBytes(), maxWidth)
    }

    /** Supprime les images des cartes qui n'existent plus (import, remise à zéro). */
    suspend fun keepOnly(ids: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.nameWithoutExtension !in ids || it.extension != "jpg" }?.forEach { it.delete() }
    }

    private fun decode(bytes: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val MAX_WIDTH = 640
    }
}
