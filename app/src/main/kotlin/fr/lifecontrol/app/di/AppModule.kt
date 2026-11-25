package fr.lifecontrol.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(SeedCallback(context))
            .addMigrations(*fr.lifecontrol.app.data.db.ALL_MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

/** Déclencheurs par défaut (F-08), insérés à la création de la base. */
class SeedCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        seedTriggers(context, db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) = Unit

    companion object {
        fun defaultTriggers(context: Context): List<Pair<String, String>> = listOf(
            "stress" to R.string.trigger_stress,
            "party" to R.string.trigger_party,
            "boredom" to R.string.trigger_boredom,
            "tiredness" to R.string.trigger_tiredness,
            "social" to R.string.trigger_social,
            "other" to R.string.trigger_other,
        ).map { (id, res) -> id to context.getString(res) }

        fun seedTriggers(context: Context, db: SupportSQLiteDatabase) {
            defaultTriggers(context).forEachIndexed { index, (id, label) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `trigger` (id, label, is_default, sort_order) VALUES (?, ?, 1, ?)",
                    arrayOf<Any>(id, label, index),
                )
            }
        }
    }
}
