package fr.lifecontrol.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "template_id") val templateId: String?,
    val name: String,
    val emoji: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    val direction: String,
    @ColumnInfo(name = "tracking_mode") val trackingMode: String,
    val unit: String?,
    /** Pas d'incrément des boutons +/−. */
    val step: Double,
    @ColumnInfo(name = "start_date") @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @ColumnInfo(name = "unit_cost_cents") val unitCostCents: Long?,
    @ColumnInfo(name = "baseline_per_day") val baselinePerDay: Double?,
    val motivation: String?,
    @ColumnInfo(name = "passive_mode") val passiveMode: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "archived_at") @Serializable(with = NullableLocalDateSerializer::class) val archivedAt: LocalDate?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "habit_goal",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habit_id", "valid_from"], unique = true)],
)
data class GoalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "valid_from") @Serializable(with = LocalDateSerializer::class) val validFrom: LocalDate,
    /** DAILY | TIMES_PER_WEEK | DAYS_OF_WEEK */
    val frequency: String,
    @ColumnInfo(name = "times_per_week") val timesPerWeek: Int?,
    /** bit 0 = lundi … bit 6 = dimanche */
    @ColumnInfo(name = "days_of_week_mask") val daysOfWeekMask: Int?,
    val threshold: Double?,
)

@Serializable
@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TriggerEntity::class, ["id"], ["trigger_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["habit_id", "date"], unique = true), Index("trigger_id")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    @ColumnInfo(name = "binary_value") val done: Boolean?,
    val quantity: Double?,
    @ColumnInfo(name = "is_pause") val isPause: Boolean,
    val note: String?,
    val mood: Int?,
    @ColumnInfo(name = "trigger_id") val triggerId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Serializable
@Entity(tableName = "trigger")
data class TriggerEntity(
    @PrimaryKey val id: String,
    val label: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Serializable
@Entity(
    tableName = "reminder",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habit_id")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    /** null = rappel global du soir. */
    @ColumnInfo(name = "habit_id") val habitId: String?,
    /** Minutes depuis minuit. */
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int,
    @ColumnInfo(name = "days_of_week_mask") val daysOfWeekMask: Int,
    val enabled: Boolean,
)

@Serializable
@Entity(
    tableName = "badge_unlock",
    indices = [Index(value = ["badge_id", "habit_id", "level"], unique = true)],
)
data class BadgeUnlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "badge_id") val badgeId: String,
    /** Pas de clé étrangère : un badge reste acquis même si l'habitude est supprimée (RG-11). */
    @ColumnInfo(name = "habit_id") val habitId: String?,
    @ColumnInfo(name = "habit_name") val habitName: String?,
    val level: Int,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long,
)

@Serializable
@Entity(tableName = "content_read")
data class ContentReadEntity(
    @PrimaryKey @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "first_read_at") val firstReadAt: Long,
)

/**
 * Carte à collectionner : une par jour (id « day-AAAA-MM-JJ »), générée en direct depuis Wikipédia.
 * Tant qu'elle n'a pas pu être générée (hors connexion), [status] vaut PENDING et seuls jour, rareté et taux sont connus.
 * L'image est enregistrée à part, dans le stockage privé de l'application (CardImageStore).
 */
@Serializable
@Entity(tableName = "card", indices = [Index("day")])
data class CardEntity(
    @PrimaryKey val id: String,
    val day: String,
    /** « day » (carte du jour) ou « debug ». */
    val source: String,
    /** COMMON, RARE ou LEGENDARY : fixée au gain, d'après le taux de réussite du jour. */
    val rarity: String,
    /** Taux de réussite de la journée (0..1), -1 pour une carte de débogage. */
    @ColumnInfo(name = "success_rate") val successRate: Double,
    val status: String = STATUS_PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val qid: String? = null,
    val title: String? = null,
    @ColumnInfo(name = "theme_id") val themeId: String? = null,
    @ColumnInfo(name = "theme_label") val themeLabel: String? = null,
    val summary: String? = null,
    val text: String? = null,
    val url: String? = null,
    val views: Long? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "image_artist") val imageArtist: String? = null,
    @ColumnInfo(name = "image_license") val imageLicense: String? = null,
    @ColumnInfo(name = "image_license_url") val imageLicenseUrl: String? = null,
    @ColumnInfo(name = "image_page") val imagePage: String? = null,
) {
    val isReady: Boolean get() = status == STATUS_READY && title != null

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_READY = "READY"
        fun dailyId(day: String) = "day-$day"
    }
}
