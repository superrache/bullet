package fr.lifecontrol.app.data.repository

import androidx.room.withTransaction
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.db.BadgeUnlockEntity
import fr.lifecontrol.app.data.db.CardEntity
import fr.lifecontrol.app.data.db.ContentReadEntity
import fr.lifecontrol.app.data.db.EntryEntity
import fr.lifecontrol.app.data.db.GoalEntity
import fr.lifecontrol.app.data.db.HabitEntity
import fr.lifecontrol.app.data.db.ReminderEntity
import fr.lifecontrol.app.data.db.TriggerEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportedSettings(
    val firstName: String,
    val currency: String,
    val dayStartHour: Int,
    val firstDayOfWeek: Int,
    val theme: String,
    val dynamicColor: Boolean,
    val eveningReminderEnabled: Boolean,
    val eveningReminderMinutes: Int,
)

/** Format d'export versionné (§10.7). */
@Serializable
data class ExportFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val habits: List<HabitEntity>,
    val goals: List<GoalEntity>,
    val entries: List<EntryEntity>,
    val triggers: List<TriggerEntity>,
    val reminders: List<ReminderEntity>,
    val badgeUnlocks: List<BadgeUnlockEntity>,
    val contentRead: List<ContentReadEntity> = emptyList(),
    /** Cartes à collectionner (contenu sans les images, retéléchargées au besoin). Absent des exports antérieurs. */
    val collectibleCards: List<CardEntity> = emptyList(),
    val settings: ExportedSettings? = null,
)

enum class ImportMode { REPLACE, MERGE }

class ImportException(message: String) : Exception(message)

@Singleton
class ExportRepository @Inject constructor(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) {
    suspend fun export(): String {
        val s = settingsRepository.current()
        val file = ExportFile(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = Instant.now().toString(),
            habits = db.habitDao().getHabits(),
            goals = db.habitDao().getGoals(),
            entries = db.entryDao().getAll(),
            triggers = db.triggerDao().getAll(),
            reminders = db.reminderDao().getAll(),
            badgeUnlocks = db.badgeDao().getUnlocks(),
            contentRead = db.badgeDao().getContentRead(),
            collectibleCards = db.cardDao().getAll(),
            settings = ExportedSettings(
                s.firstName, s.currency, s.dayStartHour, s.firstDayOfWeek.value, s.theme.name, s.dynamicColor,
                s.eveningReminderEnabled, s.eveningReminderMinutes,
            ),
        )
        return json.encodeToString(ExportFile.serializer(), file)
    }

    /** Lecture et validation d'un fichier, sans rien écrire (aperçu avant import). */
    fun parse(text: String): ExportFile {
        val file = try {
            json.decodeFromString(ExportFile.serializer(), text)
        } catch (e: Exception) {
            throw ImportException("Fichier illisible : ${e.message}")
        }
        if (file.schemaVersion > SCHEMA_VERSION) {
            throw ImportException("Ce fichier provient d'une version plus récente de l'application.")
        }
        val habitIds = file.habits.map { it.id }.toSet()
        if (file.goals.any { it.habitId !in habitIds } || file.entries.any { it.habitId !in habitIds }) {
            throw ImportException("Fichier incohérent : données rattachées à une habitude inconnue.")
        }
        return file
    }

    suspend fun import(file: ExportFile, mode: ImportMode) {
        db.withTransaction {
            when (mode) {
                ImportMode.REPLACE -> replace(file)
                ImportMode.MERGE -> merge(file)
            }
        }
        if (mode == ImportMode.REPLACE) {
            file.settings?.let { e ->
                settingsRepository.update {
                    it.copy(
                        firstName = e.firstName,
                        currency = e.currency,
                        dayStartHour = e.dayStartHour,
                        firstDayOfWeek = DayOfWeek.of(e.firstDayOfWeek),
                        theme = runCatching { ThemeMode.valueOf(e.theme) }.getOrDefault(ThemeMode.SYSTEM),
                        dynamicColor = e.dynamicColor,
                        eveningReminderEnabled = e.eveningReminderEnabled,
                        eveningReminderMinutes = e.eveningReminderMinutes,
                        onboardingDone = true,
                    )
                }
            }
        }
    }

    private suspend fun replace(file: ExportFile) {
        db.clearAllTables()
        db.triggerDao().upsertAll(file.triggers)
        db.habitDao().upsertHabits(file.habits)
        db.habitDao().upsertGoals(file.goals)
        val triggerIds = file.triggers.map { it.id }.toSet()
        db.entryDao().upsertAll(file.entries.map { e -> e.copy(triggerId = e.triggerId?.takeIf { it in triggerIds }) })
        db.reminderDao().upsertAll(file.reminders)
        db.badgeDao().insertUnlocks(file.badgeUnlocks)
        db.badgeDao().insertContentRead(file.contentRead)
        db.cardDao().insertAll(file.collectibleCards)
    }

    /** Fusion : la donnée la plus récente gagne (updatedAt). */
    private suspend fun merge(file: ExportFile) {
        val habitDao = db.habitDao()
        val entryDao = db.entryDao()

        val localTriggers = db.triggerDao().getAll().map { it.id }.toSet()
        db.triggerDao().upsertAll(file.triggers.filter { it.id !in localTriggers })
        val triggerIds = localTriggers + file.triggers.map { it.id }

        val localHabits = habitDao.getHabits().associateBy { it.id }
        val goalsByHabit = file.goals.groupBy { it.habitId }
        for (habit in file.habits) {
            val local = localHabits[habit.id]
            if (local == null || habit.updatedAt > local.updatedAt) {
                habitDao.replaceHabit(habit, goalsByHabit[habit.id].orEmpty())
            }
        }
        for (entry in file.entries) {
            val local = entryDao.get(entry.habitId, entry.date)
            if (local == null || entry.updatedAt > local.updatedAt) {
                if (local != null) entryDao.delete(entry.habitId, entry.date)
                entryDao.upsert(entry.copy(triggerId = entry.triggerId?.takeIf { it in triggerIds }))
            }
        }
        val localReminders = db.reminderDao().getAll().map { it.id }.toSet()
        db.reminderDao().upsertAll(file.reminders.filter { it.id !in localReminders })
        db.badgeDao().insertUnlocks(file.badgeUnlocks)
        db.badgeDao().insertContentRead(file.contentRead)
        db.cardDao().insertAll(file.collectibleCards)
    }

    suspend fun deleteAll() {
        db.clearAllTables()
        settingsRepository.clear()
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
