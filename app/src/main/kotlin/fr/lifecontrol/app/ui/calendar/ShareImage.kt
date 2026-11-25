package fr.lifecontrol.app.ui.calendar

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import fr.lifecontrol.app.R
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.model.DayStatus
import java.io.File
import java.time.temporal.TemporalAdjusters

/**
 * Image partageable d'une vue d'ensemble (F-15) : carte de chaleur + chiffres clés.
 * Générée localement, partagée uniquement via la feuille de partage Android.
 */
object ShareImage {

    fun share(context: Context, state: CalendarUiState, chooserTitle: String) {
        val overview = state.overview ?: return
        val bitmap = render(context, state, overview)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "progression.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun render(context: Context, state: CalendarUiState, overview: OverviewState): Bitmap {
        val period = overview.period
        val firstDay = state.journal.settings.firstDayOfWeek
        val start = period.from.with(TemporalAdjusters.previousOrSame(firstDay))
        val weeks = generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(period.to) }.toList()

        val width = 1080
        val margin = 64f
        val cell = ((width - 2 * margin) / weeks.size.coerceAtLeast(7)).coerceAtMost(110f)
        val gap = cell * 0.18f
        val heatHeight = 7 * cell
        val height = (420 + heatHeight + 160).toInt()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFBF7.toInt())

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1D1B20.toInt(); textSize = 56f; typeface = Typeface.DEFAULT_BOLD
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF49454F.toInt(); textSize = 38f }
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5B4FD6.toInt(); textSize = 120f; typeface = Typeface.DEFAULT_BOLD
        }

        val habit = state.selectedHabit
        canvas.drawText(habit?.let { "${it.emoji} ${it.name}" } ?: context.getString(R.string.share_my_progress), margin, 120f, title)
        canvas.drawText(periodLabel(period), margin, 180f, body)
        canvas.drawText(Fmt.percent(overview.globalRate), margin, 330f, big)
        canvas.drawText(context.getString(R.string.stat_success_rate), margin + 420f, 310f, body)

        val success = 0xFF2E9E78.toInt()
        val failure = 0xFFE5574F.toInt()
        val empty = 0xFFEAE4E2.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = 400f
        weeks.forEachIndexed { w, weekStart ->
            for (d in 0 until 7) {
                val date = weekStart.plusDays(d.toLong())
                if (date.isBefore(period.from) || date.isAfter(period.to)) continue
                paint.color = if (habit != null) {
                    when (state.journal.history(habit.id)?.status(date)) {
                        DayStatus.SUCCESS -> success
                        DayStatus.FAILURE -> failure
                        DayStatus.MISSING -> 0xFFD9D3D8.toInt()
                        else -> empty
                    }
                } else {
                    val ratio = overview.aggregates[date]?.ratio
                    when {
                        ratio == null -> empty
                        ratio == 0.0 -> failure
                        else -> (((0.25 + 0.75 * ratio) * 255).toInt() shl 24) or (success and 0x00FFFFFF)
                    }
                }
                val x = margin + w * cell
                val y = top + d * cell
                canvas.drawRoundRect(RectF(x, y, x + cell - gap, y + cell - gap), cell * 0.2f, cell * 0.2f, paint)
            }
        }
        canvas.drawText(context.getString(R.string.app_name), margin, top + heatHeight + 90f, body)
        return bitmap
    }
}
