package fr.lifecontrol.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.lifecontrol.app.MainActivity
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.repository.Journal
import fr.lifecontrol.app.data.repository.JournalRepository
import fr.lifecontrol.app.di.ApplicationScope
import fr.lifecontrol.domain.model.DayStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Objectifs tenus / prévus un jour donné, comptés comme sur l'écran « Aujourd'hui ». */
data class DayProgress(val success: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else success.toFloat() / total

    companion object {
        fun of(journal: Journal, date: LocalDate = journal.today): DayProgress {
            var success = 0
            var total = 0
            for (habit in journal.activeHabits) {
                if (!habit.isActiveOn(date)) continue
                val history = journal.history(habit.id) ?: continue
                val status = history.status(date)
                if (status == DayStatus.NEUTRAL && history.entry(date)?.hasValue != true) continue
                total++
                if (status == DayStatus.SUCCESS) success++
            }
            return DayProgress(success, total)
        }
    }
}

/**
 * Widget d'écran d'accueil : 2 × 2 avec logotype, anneau « 3/5 » et « objectifs tenus aujourd'hui ».
 * Volontairement discret (l'application peut être verrouillée) : aucun nom d'habitude, seulement des chiffres.
 * Un appui ouvre l'application (et donc son écran de verrouillage s'il est activé).
 */
object ProgressWidget {

    fun render(context: Context, progress: DayProgress?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_progress).apply {
            if (progress != null) {
                setProgressBar(R.id.widget_ring, maxOf(progress.total, 1), progress.success, false)
                setTextViewText(R.id.widget_ratio, context.getString(R.string.widget_ratio, progress.success, progress.total))
                val nothing = progress.total == 0
                setTextViewText(
                    R.id.widget_caption,
                    if (nothing) {
                        context.getString(R.string.widget_nothing_today)
                    } else {
                        context.resources.getQuantityString(R.plurals.widget_caption, progress.total)
                    },
                )
                setContentDescription(
                    R.id.widget_root,
                    if (nothing) {
                        context.getString(R.string.widget_nothing_today)
                    } else {
                        context.resources.getQuantityString(R.plurals.widget_summary, progress.total, progress.success, progress.total)
                    },
                )
            }
            val open = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

    /** Met à jour tous les widgets posés (rien à faire s'il n'y en a aucun). */
    fun push(context: Context, progress: DayProgress?) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, ProgressWidgetProvider::class.java))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, progress))
    }
}

/** Tient les widgets à jour tant que l'application tourne (saisie, changement de jour…). */
@Singleton
class ProgressWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalRepository: JournalRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            journalRepository.journal.filterNotNull()
                .map { DayProgress.of(it) }
                .distinctUntilChanged()
                .collect { ProgressWidget.push(context, it) }
        }
    }

    /** Calcule l'avancement du jour à la demande (le processus vient peut-être d'être lancé). */
    suspend fun current(): DayProgress? =
        withTimeoutOrNull(8_000) { journalRepository.journal.filterNotNull().first() }?.let { DayProgress.of(it) }
}

/** Mises à jour système (pose du widget, toutes les 30 minutes pour suivre le changement de jour). */
@AndroidEntryPoint
class ProgressWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var updater: ProgressWidgetUpdater
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                ProgressWidget.push(context, updater.current())
            } finally {
                pending.finish()
            }
        }
    }
}
