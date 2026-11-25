package fr.lifecontrol.app.data.repository

import fr.lifecontrol.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Jour logique courant (RG-02) : une saisie avant l'heure de début de journée compte pour la veille. */
@Singleton
class DateProvider @Inject constructor(
    settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    val today: StateFlow<LocalDate> = combine(
        settingsRepository.settings.map { it.dayStartHour }.distinctUntilChanged(),
        ticker,
    ) { hour, _ -> logicalDate(hour) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, logicalDate(UserSettings().dayStartHour))

    companion object {
        fun logicalDate(dayStartHour: Int, now: LocalDateTime = LocalDateTime.now()): LocalDate =
            now.minusHours(dayStartHour.toLong()).toLocalDate()
    }
}
