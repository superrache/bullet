package fr.lifecontrol.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val onboardingDone: Boolean = false,
    val firstName: String = "",
    val currency: String = "€",
    /** Heure de début de journée logique (RG-02). */
    val dayStartHour: Int = 4,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderMinutes: Int = 21 * 60,
    /** Verrouillage par empreinte / code de l'appareil (propre à ce téléphone, non exporté). */
    val appLock: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val firstName = stringPreferencesKey("first_name")
        val currency = stringPreferencesKey("currency")
        val dayStartHour = intPreferencesKey("day_start_hour")
        val firstDayOfWeek = intPreferencesKey("first_day_of_week")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val eveningEnabled = booleanPreferencesKey("evening_reminder_enabled")
        val eveningMinutes = intPreferencesKey("evening_reminder_minutes")
        val appLock = booleanPreferencesKey("app_lock")
    }

    val settings: Flow<UserSettings> = dataStore.data.map(::readFrom)

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { p ->
            val s = transform(readFrom(p))
            p[Keys.onboardingDone] = s.onboardingDone
            p[Keys.firstName] = s.firstName
            p[Keys.currency] = s.currency
            p[Keys.dayStartHour] = s.dayStartHour
            p[Keys.firstDayOfWeek] = s.firstDayOfWeek.value
            p[Keys.theme] = s.theme.name
            p[Keys.dynamicColor] = s.dynamicColor
            p[Keys.eveningEnabled] = s.eveningReminderEnabled
            p[Keys.eveningMinutes] = s.eveningReminderMinutes
            p[Keys.appLock] = s.appLock
        }
    }

    private fun readFrom(p: Preferences): UserSettings {
        val d = UserSettings()
        return UserSettings(
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
            firstName = p[Keys.firstName] ?: d.firstName,
            currency = p[Keys.currency] ?: d.currency,
            dayStartHour = p[Keys.dayStartHour] ?: d.dayStartHour,
            firstDayOfWeek = p[Keys.firstDayOfWeek]?.let(DayOfWeek::of) ?: d.firstDayOfWeek,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            eveningReminderEnabled = p[Keys.eveningEnabled] ?: d.eveningReminderEnabled,
            eveningReminderMinutes = p[Keys.eveningMinutes] ?: d.eveningReminderMinutes,
            appLock = p[Keys.appLock] ?: d.appLock,
        )
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
