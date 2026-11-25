package fr.lifecontrol.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit ORDER BY sort_order, created_at")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_goal ORDER BY valid_from")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM habit ORDER BY sort_order, created_at")
    suspend fun getHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit_goal ORDER BY valid_from")
    suspend fun getGoals(): List<GoalEntity>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun getHabit(id: String): HabitEntity?

    @Query("SELECT * FROM habit_goal WHERE habit_id = :habitId ORDER BY valid_from")
    suspend fun getGoals(habitId: String): List<GoalEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM habit")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsertHabit(habit: HabitEntity)

    @Upsert
    suspend fun upsertHabits(habits: List<HabitEntity>)

    @Upsert
    suspend fun upsertGoals(goals: List<GoalEntity>)

    @Query("DELETE FROM habit_goal WHERE habit_id = :habitId")
    suspend fun deleteGoals(habitId: String)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun deleteHabit(id: String)

    @Query("UPDATE habit SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int, now: Long)

    @Transaction
    suspend fun replaceHabit(habit: HabitEntity, goals: List<GoalEntity>) {
        upsertHabit(habit)
        deleteGoals(habit.id)
        upsertGoals(goals)
    }
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entry")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE habit_id = :habitId AND date = :date")
    suspend fun get(habitId: String, date: LocalDate): EntryEntity?

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entry WHERE habit_id = :habitId AND date = :date")
    suspend fun delete(habitId: String, date: LocalDate)
}

@Dao
interface TriggerDao {
    @Query("SELECT * FROM trigger ORDER BY sort_order")
    fun observeAll(): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM trigger ORDER BY sort_order")
    suspend fun getAll(): List<TriggerEntity>

    @Upsert
    suspend fun upsertAll(triggers: List<TriggerEntity>)

    @Delete
    suspend fun delete(trigger: TriggerEntity)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE habit_id = :habitId")
    suspend fun forHabit(habitId: String): List<ReminderEntity>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Upsert
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge_unlock")
    suspend fun getUnlocks(): List<BadgeUnlockEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnlocks(unlocks: List<BadgeUnlockEntity>)

    @Query("SELECT * FROM content_read")
    fun observeContentRead(): Flow<List<ContentReadEntity>>

    @Query("SELECT * FROM content_read")
    suspend fun getContentRead(): List<ContentReadEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContentRead(items: List<ContentReadEntity>)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM card ORDER BY day DESC, created_at DESC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM card")
    suspend fun getAll(): List<CardEntity>

    @Query("SELECT * FROM card WHERE id = :id")
    suspend fun get(id: String): CardEntity?

    /** @return -1 si la carte existait déjà (une seule carte par jour). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<CardEntity>)

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM card")
    suspend fun clear()
}
