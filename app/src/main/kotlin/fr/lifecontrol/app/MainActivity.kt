package fr.lifecontrol.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.data.repository.BadgeService
import fr.lifecontrol.app.data.repository.BadgeView
import fr.lifecontrol.app.data.repository.CardService
import androidx.compose.runtime.CompositionLocalProvider
import fr.lifecontrol.app.ui.cards.LocalCardImageLoader
import fr.lifecontrol.app.ui.cards.CardRevealDialog
import androidx.compose.runtime.key
import fr.lifecontrol.app.data.repository.SettingsRepository
import fr.lifecontrol.app.data.repository.UserSettings
import fr.lifecontrol.app.ui.badges.BadgeDetailDialog
import fr.lifecontrol.app.ui.navigation.AppNavigation
import fr.lifecontrol.app.ui.lock.AppLock
import fr.lifecontrol.app.ui.lock.LockScreen
import fr.lifecontrol.app.ui.theme.BulletTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    badgeService: BadgeService,
    val cardService: CardService,
) : ViewModel() {
    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val unlocks = badgeService.unlockEvents
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var appLock: AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val unlocked by appLock.unlocked.collectAsStateWithLifecycle()
            val s = settings
            val locked = s?.appLock == true && !unlocked
            // Verrou actif : la miniature des applis récentes ne montre pas le journal (Android 13+).
            LaunchedEffect(s?.appLock) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(s?.appLock != true)
            }
            BulletTheme(themeMode = s?.theme ?: fr.lifecontrol.app.data.repository.ThemeMode.SYSTEM, dynamicColor = s?.dynamicColor == true) {
                val queue = remember { mutableStateListOf<BadgeView>() }
                LaunchedEffect(Unit) { viewModel.unlocks.collect { queue.addAll(it) } }
                val cardService = viewModel.cardService
                val cardQueue = remember { mutableStateListOf<String>() }
                LaunchedEffect(Unit) { cardService.reveals.collect { if (it !in cardQueue) cardQueue.add(it) } }
                val cards by cardService.cards.collectAsStateWithLifecycle()
                val generatingCards by cardService.generating.collectAsStateWithLifecycle()
                // Surface (et non Box + background) : fournit aussi la couleur de contenu (onBackground),
                // sinon tout texte hors Scaffold retombe sur le noir par défaut, illisible en mode sombre.
                CompositionLocalProvider(LocalCardImageLoader provides cardService::image) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (s != null && locked) {
                        LockScreen(onUnlocked = appLock::markUnlocked)
                    } else if (s != null) {
                        AppNavigation(onboardingDone = s.onboardingDone)
                        // Animation de déblocage d'un badge (F-18), un à la fois.
                        queue.firstOrNull()?.let { badge ->
                            if (s.onboardingDone) BadgeDetailDialog(badge, celebrate = true) { queue.removeAt(0) }
                        }
                        // Carte gagnée : révélée après les badges, une à la fois.
                        if (queue.isEmpty() && s.onboardingDone) {
                            cardQueue.firstOrNull()?.let { id ->
                                val card = cards.firstOrNull { it.id == id }
                                if (card != null) key(id) {
                                    CardRevealDialog(
                                        card = card,
                                        generating = id in generatingCards,
                                        onRetry = { cardService.retry(id) },
                                        onDismiss = { cardQueue.remove(id) },
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
        // Carte du jour restée à tirer (pas de connexion la dernière fois) : nouvel essai.
        viewModel.cardService.retryPending()
    }

    override fun onStop() {
        super.onStop()
        appLock.onBackground()
    }
}
