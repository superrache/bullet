package fr.lifecontrol.app.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.lifecontrol.app.R
import fr.lifecontrol.app.ui.components.BrandHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État du verrou de l'application (option « Verrouiller l'application »).
 * Verrouillée au démarrage, puis à nouveau après [RELOCK_DELAY_MS] passées en arrière-plan
 * (le délai évite de reverrouiller au retour du sélecteur de fichiers d'export, par exemple).
 */
@Singleton
class AppLock @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var backgroundSince: Long? = null

    fun markUnlocked() {
        _unlocked.value = true
    }

    fun onBackground() {
        backgroundSince = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (SystemClock.elapsedRealtime() - since > RELOCK_DELAY_MS) _unlocked.value = false
    }

    companion object {
        const val RELOCK_DELAY_MS = 30_000L
    }
}

enum class LockAvailability { AVAILABLE, NO_SCREEN_LOCK, UNSUPPORTED_ANDROID }

/**
 * Authentification par empreinte (ou visage), avec repli sur le code / schéma de l'appareil
 * pour ne jamais rester bloqué dehors. API système (Android 10+), sans dépendance supplémentaire.
 */
object DeviceAuth {

    fun availability(context: Context): LockAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return LockAvailability.UNSUPPORTED_ANDROID
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return if (keyguard?.isDeviceSecure == true) LockAvailability.AVAILABLE else LockAvailability.NO_SCREEN_LOCK
    }

    /** Une empreinte (ou autre biométrie) est-elle enregistrée ? Sinon seul le code de l'appareil sera proposé. */
    fun hasBiometrics(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            manager.canAuthenticate()
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onError("")
            return
        }
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        subtitle?.let(builder::setSubtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
            },
        )
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Écran affiché tant que l'application est verrouillée : rien du journal n'est composé derrière. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = {
        context.findActivity()?.let { activity ->
            DeviceAuth.authenticate(activity, title, subtitle, onSuccess = onUnlocked, onError = { error = it.ifBlank { null } })
        }
    }
    // Propose l'empreinte dès l'affichage.
    LaunchedEffect(Unit) { prompt() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandHeader()
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lock_locked), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { prompt() }) {
            Icon(Icons.Rounded.Fingerprint, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.lock_unlock))
        }
    }
}
