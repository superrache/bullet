package fr.lifecontrol.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.lifecontrol.app.data.repository.BadgeService
import fr.lifecontrol.app.di.ApplicationScope
import fr.lifecontrol.app.notifications.Notifications
import fr.lifecontrol.app.notifications.ReminderScheduler
import fr.lifecontrol.app.widget.ProgressWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BulletApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    /** Injecté pour démarrer le moteur de badges dès le lancement. */
    @Inject lateinit var badgeService: BadgeService
    @Inject lateinit var widgetUpdater: ProgressWidgetUpdater

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        scope.launch { scheduler.rescheduleAll() }
        widgetUpdater.start()
    }
}
