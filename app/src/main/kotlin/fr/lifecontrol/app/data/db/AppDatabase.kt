package fr.lifecontrol.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

@Database(
    entities = [
        HabitEntity::class, GoalEntity::class, EntryEntity::class, TriggerEntity::class,
        ReminderEntity::class, BadgeUnlockEntity::class, ContentReadEntity::class,
        CardEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun entryDao(): EntryDao
    abstract fun triggerDao(): TriggerDao
    abstract fun reminderDao(): ReminderDao
    abstract fun badgeDao(): BadgeDao
    abstract fun cardDao(): CardDao

    companion object {
        const val NAME = "life-control.db"
    }
}
