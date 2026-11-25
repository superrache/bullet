package fr.lifecontrol.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.repository.SettingsRepository
import fr.lifecontrol.app.di.SeedCallback
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.UUID

fun context(): Context = ApplicationProvider.getApplicationContext()

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java)
        .addCallback(SeedCallback(context()))
        .allowMainThreadQueries()
        .build()

fun settingsRepository(scope: CoroutineScope): SettingsRepository =
    SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context().cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        },
    )
