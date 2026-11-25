package fr.lifecontrol.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.lifecontrol.app.MainActivity
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.repository.DateProvider
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.JournalRepository
import fr.lifecontrol.app.data.repository.Reminder
import fr.lifecontrol.app.data.repository.SettingsRepository
import fr.lifecontrol.app.data.db.ALL_DAYS_MASK
import fr.lifecontrol.app.data.db.toDays
import fr.lifecontrol.app.di.ApplicationScope
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Planification des rappels (F-23, §10.8) : un travail unique WorkManager par rappel,
 * replanifié après chaque déclenchement. WorkManager conserve les travaux après redémarrage.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habitRepository: HabitRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedule(reminder: Reminder) {
        if (!reminder.enabled) {
            cancel(reminder.id)
            return
        }
        val delay = delayUntilNext(reminder.timeMinutes, reminder.daysMask)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminder.id))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(reminderId: String) {
        workManager.cancelUniqueWork(workName(reminderId))
    }

    suspend fun scheduleEvening() {
        val settings = settingsRepository.current()
        if (!settings.eveningReminderEnabled) {
            workManager.cancelUniqueWork(EVENING)
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNext(settings.eveningReminderMinutes, ALL_DAYS_MASK).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(EVENING, ExistingWorkPolicy.REPLACE, request)
    }

    /** Replanifie tout (démarrage, import, suppression des données). */
    suspend fun rescheduleAll() {
        workManager.cancelAllWorkByTag(TAG)
        val habits = habitRepository.allHabits().associateBy { it.id }
        habitRepository.allReminders()
            .filter { habits[it.habitId]?.isArchived == false }
            .forEach(::schedule)
        scheduleEvening()
    }

    companion object {
        const val TAG = "reminder"
        const val EVENING = "reminder-evening"
        fun workName(id: String) = "reminder-$id"

        fun delayUntilNext(minutes: Int, daysMask: Int, now: LocalDateTime = LocalDateTime.now()): Duration {
            val days = daysMask.toDays().ifEmpty { ALL_DAYS_MASK.toDays() }
            val time = LocalTime.of(minutes / 60, minutes % 60)
            var candidate = now.toLocalDate().atTime(time)
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
            while (candidate.dayOfWeek !in days) candidate = candidate.plusDays(1)
            return Duration.between(now, candidate)
        }
    }
}

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("MissingPermission")
    fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val journalRepository: JournalRepository,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getString(KEY_REMINDER_ID)
        val journal = journalRepository.journal.filterNotNull().first()
        val ctx = applicationContext

        if (reminderId == null) {
            // Rappel du soir : seulement s'il reste des habitudes non renseignées.
            val pending = journal.activeHabits.count { journal.history(it.id)?.status(journal.today) == DayStatus.PENDING }
            if (pending > 0) {
                val builder = NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(ctx.getString(R.string.notif_evening_title))
                    .setContentText(ctx.resources.getQuantityString(R.plurals.notif_evening_text, pending, pending))
                    .setContentIntent(Notifications.openAppIntent(ctx))
                    .setAutoCancel(true)
                Notifications.notify(ctx, EVENING_NOTIFICATION_ID, builder)
            }
            scheduler.scheduleEvening()
            return Result.success()
        }

        val reminder = habitRepository.getReminder(reminderId) ?: return Result.success()
        val habit = journal.habit(reminder.habitId)
        if (habit != null && !habit.isArchived && reminder.enabled) {
            val status = journal.history(habit.id)?.status(journal.today)
            if (status == DayStatus.PENDING) {
                val notificationId = reminder.id.hashCode()
                val builder = NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("${habit.emoji} ${habit.name}")
                    .setContentText(ctx.getString(R.string.notif_habit_text))
                    .setContentIntent(Notifications.openAppIntent(ctx))
                    .setAutoCancel(true)
                if (habit.trackingMode == TrackingMode.BINARY) {
                    val label = if (habit.direction == Direction.START) R.string.action_done else R.string.action_held
                    builder.addAction(0, ctx.getString(label), NotificationActionReceiver.intent(ctx, habit.id, notificationId))
                }
                Notifications.notify(ctx, notificationId, builder)
            }
        }
        scheduler.schedule(reminder)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val EVENING_NOTIFICATION_ID = 1
    }
}

/** Action « Fait / Tenu » directement depuis la notification. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var habitRepository: HabitRepository
    @Inject lateinit var dateProvider: DateProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        scope.launch {
            try {
                habitRepository.updateEntry(habitId, dateProvider.today.value) { it.copy(done = true) }
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_HABIT_ID = "habit_id"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun intent(context: Context, habitId: String, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationActionReceiver::class.java)
                .putExtra(EXTRA_HABIT_ID, habitId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Replanifie les rappels après un changement d'heure ou de fuseau. */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
