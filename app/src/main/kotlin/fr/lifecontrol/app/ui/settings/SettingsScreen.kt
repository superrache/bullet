package fr.lifecontrol.app.ui.settings

import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import fr.lifecontrol.app.data.repository.BackupCrypto
import fr.lifecontrol.app.data.repository.WrongPasswordException
import fr.lifecontrol.app.ui.lock.AppLock
import fr.lifecontrol.app.ui.lock.DeviceAuth
import fr.lifecontrol.app.ui.lock.LockAvailability
import fr.lifecontrol.app.ui.lock.findActivity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.lifecontrol.app.ui.components.BrandHeader
import fr.lifecontrol.app.BuildConfig
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.repository.ExportFile
import fr.lifecontrol.app.data.repository.ExportRepository
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.ImportException
import fr.lifecontrol.app.data.repository.ImportMode
import fr.lifecontrol.app.data.repository.SettingsRepository
import fr.lifecontrol.app.data.repository.ThemeMode
import fr.lifecontrol.app.data.repository.Trigger
import fr.lifecontrol.app.data.repository.UserSettings
import fr.lifecontrol.app.di.SeedCallback
import fr.lifecontrol.app.notifications.ReminderScheduler
import fr.lifecontrol.app.ui.discover.Disclaimer
import fr.lifecontrol.app.ui.habit.DeleteConfirmDialog
import fr.lifecontrol.app.ui.habit.TimeDialog
import fr.lifecontrol.app.ui.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

sealed interface SettingsMessage {
    data object Exported : SettingsMessage
    data object Imported : SettingsMessage
    data object Deleted : SettingsMessage
    data class Error(val text: String) : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val exportRepository: ExportRepository,
    private val habitRepository: HabitRepository,
    private val scheduler: ReminderScheduler,
    private val db: AppDatabase,
    private val appLock: AppLock,
    private val cardService: fr.lifecontrol.app.data.repository.CardService,
) : ViewModel() {
    fun debugDrawCard(rarity: fr.lifecontrol.app.data.content.CardRarity) = cardService.debugDraw(rarity)
    fun debugResetCards() = cardService.debugReset()

    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val triggers: StateFlow<List<Trigger>> = habitRepository.triggers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Fichier lu, en attente de confirmation d'import. */
    val pendingImport = MutableStateFlow<ExportFile?>(null)

    /** Export chiffré en attente de mot de passe ; [importPasswordError] si le mot de passe était faux. */
    val pendingEncryptedImport = MutableStateFlow<String?>(null)
    val importPasswordError = MutableStateFlow(false)
    val busy = MutableStateFlow(false)

    /** Mot de passe choisi avant d'ouvrir le sélecteur de fichier (null = export en clair). Gardé en mémoire seulement. */
    private var exportPassword: CharArray? = null

    fun prepareExport(password: CharArray?) {
        exportPassword?.fill('\u0000')
        exportPassword = password
    }

    fun setAppLock(enabled: Boolean) {
        if (enabled) appLock.markUnlocked()
        viewModelScope.launch { settingsRepository.update { it.copy(appLock = enabled) } }
    }

    private val messageChannel = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            scheduler.scheduleEvening()
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            val password = exportPassword
            exportPassword = null
            runCatching {
                val plain = exportRepository.export()
                val text = if (password == null) plain else withContext(Dispatchers.Default) { BackupCrypto.encrypt(plain, password) }
                password?.fill('\u0000')
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                }
            }.onSuccess { messageChannel.send(SettingsMessage.Exported) }
                .onFailure { messageChannel.send(SettingsMessage.Error(it.message ?: "")) }
            busy.value = false
        }
    }

    fun readImport(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw ImportException("Fichier vide")
                if (BackupCrypto.isEncrypted(text)) {
                    importPasswordError.value = false
                    pendingEncryptedImport.value = text
                } else {
                    pendingImport.value = exportRepository.parse(text)
                }
            } catch (e: Exception) {
                messageChannel.send(SettingsMessage.Error(e.message ?: ""))
            }
        }
    }

    fun decryptImport(password: CharArray) {
        val text = pendingEncryptedImport.value ?: return
        viewModelScope.launch {
            busy.value = true
            try {
                val plain = withContext(Dispatchers.Default) { BackupCrypto.decrypt(text, password) }
                pendingImport.value = exportRepository.parse(plain)
                pendingEncryptedImport.value = null
            } catch (e: WrongPasswordException) {
                importPasswordError.value = true
            } catch (e: Exception) {
                pendingEncryptedImport.value = null
                messageChannel.send(SettingsMessage.Error(e.message ?: ""))
            } finally {
                password.fill('\u0000')
                busy.value = false
            }
        }
    }

    fun cancelEncryptedImport() {
        pendingEncryptedImport.value = null
    }

    fun confirmImport(mode: ImportMode) {
        val file = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            runCatching {
                exportRepository.import(file, mode)
                ensureDefaultTriggers()
                scheduler.rescheduleAll()
            }.onSuccess { messageChannel.send(SettingsMessage.Imported) }
                .onFailure { messageChannel.send(SettingsMessage.Error(it.message ?: "")) }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            exportRepository.deleteAll()
            ensureDefaultTriggers()
            scheduler.rescheduleAll()
            settingsRepository.update { it.copy(onboardingDone = true) }
            messageChannel.send(SettingsMessage.Deleted)
        }
    }

    fun replayOnboarding() = update { it.copy(onboardingDone = false) }

    fun saveTriggers(list: List<Pair<String?, String>>) {
        viewModelScope.launch { habitRepository.saveTriggers(list.filter { it.second.isNotBlank() }) }
    }

    private suspend fun ensureDefaultTriggers() = withContext(Dispatchers.IO) {
        if (habitRepository.triggersCount() == 0) {
            SeedCallback.seedTriggers(context, db.openHelper.writableDatabase)
        }
    }

    fun exportFileName(): String = "bullet-${LocalDate.now()}.json"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenHelp: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val triggers by viewModel.triggers.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val pendingEncryptedImport by viewModel.pendingEncryptedImport.collectAsStateWithLifecycle()
    val importPasswordError by viewModel.importPasswordError.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var askExportPassword by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lockAvailability = remember { DeviceAuth.availability(context) }
    val lockTitle = stringResource(R.string.lock_prompt_title)
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var editTime by remember { mutableStateOf(false) }
    var editTriggers by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val exportedText = stringResource(R.string.settings_exported)
    val importedText = stringResource(R.string.settings_imported)
    val deletedText = stringResource(R.string.settings_deleted)
    val errorText = stringResource(R.string.settings_error)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readImport)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    SettingsMessage.Exported -> exportedText
                    SettingsMessage.Imported -> importedText
                    SettingsMessage.Deleted -> deletedText
                    is SettingsMessage.Error -> errorText.format(message.text)
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Group(stringResource(R.string.settings_profile))
            var name by remember(s.firstName) { mutableStateOf(s.firstName) }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(30)
                    viewModel.update { st -> st.copy(firstName = name) }
                },
                label = { Text(stringResource(R.string.settings_first_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Label(stringResource(R.string.settings_currency))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("€", "$", "£", "CHF").forEach { c ->
                    FilterChip(selected = s.currency == c, onClick = { viewModel.update { it.copy(currency = c) } }, label = { Text(c) })
                }
            }

            Group(stringResource(R.string.settings_journal))
            Label(stringResource(R.string.settings_day_start))
            Text(stringResource(R.string.settings_day_start_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).forEach { h ->
                    FilterChip(selected = s.dayStartHour == h, onClick = { viewModel.update { it.copy(dayStartHour = h) } }, label = { Text("${h}h") })
                }
            }
            Spacer(Modifier.height(8.dp))
            Label(stringResource(R.string.settings_first_day))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { d ->
                    FilterChip(
                        selected = s.firstDayOfWeek == d,
                        onClick = { viewModel.update { it.copy(firstDayOfWeek = d) } },
                        label = { Text(Fmt.weekdayFull(d)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { editTriggers = true }) { Text(stringResource(R.string.settings_triggers)) }

            Group(stringResource(R.string.settings_appearance))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (t, label) ->
                    FilterChip(selected = s.theme == t, onClick = { viewModel.update { it.copy(theme = t) } }, label = { Text(stringResource(label)) })
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(stringResource(R.string.settings_dynamic_color), null, s.dynamicColor) { v -> viewModel.update { it.copy(dynamicColor = v) } }
            }

            Group(stringResource(R.string.field_reminders))
            SwitchRow(
                stringResource(R.string.settings_evening_reminder),
                stringResource(R.string.settings_evening_reminder_hint),
                s.eveningReminderEnabled,
            ) { v -> viewModel.update { it.copy(eveningReminderEnabled = v) } }
            if (s.eveningReminderEnabled) {
                TextButton(onClick = { editTime = true }) { Text(stringResource(R.string.settings_time, Fmt.time(s.eveningReminderMinutes))) }
            }

            Group(stringResource(R.string.settings_security))
            SwitchRow(
                stringResource(R.string.settings_app_lock),
                stringResource(
                    when (lockAvailability) {
                        LockAvailability.AVAILABLE ->
                            if (DeviceAuth.hasBiometrics(context)) R.string.settings_app_lock_hint else R.string.settings_app_lock_hint_no_fingerprint
                        LockAvailability.NO_SCREEN_LOCK -> R.string.settings_app_lock_no_screen_lock
                        LockAvailability.UNSUPPORTED_ANDROID -> R.string.settings_app_lock_unsupported
                    },
                ),
                s.appLock,
                enabled = lockAvailability == LockAvailability.AVAILABLE || s.appLock,
            ) { v ->
                // Activer comme désactiver demande de s'authentifier : on vérifie que ça marche, et un tiers ne peut pas l'enlever.
                context.findActivity()?.let { activity ->
                    DeviceAuth.authenticate(activity, lockTitle, null, onSuccess = { viewModel.setAppLock(v) })
                }
            }

            Group(stringResource(R.string.settings_data))
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { askExportPassword = true }, enabled = !busy) { Text(stringResource(R.string.settings_export)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, enabled = !busy) { Text(stringResource(R.string.settings_import)) }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.settings_delete_all), color = MaterialTheme.colorScheme.error)
            }

            if (BuildConfig.DEBUG) {
                // Outils de test, absents de la version publiée.
                Group(stringResource(R.string.settings_debug))
                Label(stringResource(R.string.debug_draw_card))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fr.lifecontrol.app.data.content.CardRarity.entries.forEach { rarity ->
                        OutlinedButton(onClick = { viewModel.debugDrawCard(rarity) }) {
                            Text(fr.lifecontrol.app.ui.cards.rarityLabel(rarity))
                        }
                    }
                }
                TextButton(onClick = viewModel::debugResetCards) { Text(stringResource(R.string.debug_reset_cards)) }
            }

            Group(stringResource(R.string.settings_about))
            TextButton(onClick = onOpenHelp) { Text(stringResource(R.string.help_title)) }
            TextButton(onClick = viewModel::replayOnboarding) { Text(stringResource(R.string.settings_replay_onboarding)) }
            TextButton(onClick = { showAbout = true }) { Text(stringResource(R.string.settings_about_app)) }
            Spacer(Modifier.height(8.dp))
            Disclaimer()
        }
    }

    if (askExportPassword) {
        ExportPasswordDialog(
            onDismiss = { askExportPassword = false },
            onConfirm = { password ->
                askExportPassword = false
                viewModel.prepareExport(password)
                exportLauncher.launch(viewModel.exportFileName())
            },
        )
    }

    if (pendingEncryptedImport != null) {
        ImportPasswordDialog(
            wrongPassword = importPasswordError,
            busy = busy,
            onDismiss = viewModel::cancelEncryptedImport,
            onConfirm = viewModel::decryptImport,
        )
    }

    pendingImport?.let { file ->
        AlertDialog(
            onDismissRequest = { viewModel.pendingImport.value = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.import_preview, file.habits.size, file.habits.size) + "\n\n" +
                        stringResource(R.string.import_modes),
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = { viewModel.confirmImport(ImportMode.MERGE) }) { Text(stringResource(R.string.import_merge)) }
                    TextButton(onClick = { viewModel.confirmImport(ImportMode.REPLACE) }) {
                        Text(stringResource(R.string.import_replace), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { viewModel.pendingImport.value = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }

    if (confirmDelete) {
        DeleteConfirmDialog(
            text = stringResource(R.string.settings_delete_all_text),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAll()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (editTime) {
        TimeDialog(
            initialMinutes = settings?.eveningReminderMinutes ?: (21 * 60),
            onDismiss = { editTime = false },
            onConfirm = { m ->
                viewModel.update { it.copy(eveningReminderMinutes = m) }
                editTime = false
            },
        )
    }

    if (editTriggers) {
        TriggersDialog(triggers, onDismiss = { editTriggers = false }) {
            viewModel.saveTriggers(it)
            editTriggers = false
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { BrandHeader(Modifier.fillMaxWidth(), fontSize = 36.sp) },
            text = { Text(stringResource(R.string.about_text, BuildConfig.VERSION_NAME)) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun TriggersDialog(triggers: List<Trigger>, onDismiss: () -> Unit, onSave: (List<Pair<String?, String>>) -> Unit) {
    val items = remember { mutableStateListOf<Pair<String?, String>>().apply { addAll(triggers.map { it.id to it.label }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_triggers)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { i, (id, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { items[i] = id to it.take(30) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                    }
                }
                TextButton(onClick = { items.add(null to "") }) { Text(stringResource(R.string.action_add)) }
            }
        },
        confirmButton = { Button(onClick = { onSave(items.toList()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Choix du mot de passe d'export (chiffrement), ou export en clair sur demande explicite. */
@Composable
private fun ExportPasswordDialog(onDismiss: () -> Unit, onConfirm: (CharArray?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val tooShort = password.length < BackupCrypto.MIN_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(stringResource(R.string.export_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.export_password_text), style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, stringResource(R.string.field_password), visible, { visible = !visible },
                    supporting = stringResource(R.string.export_password_min, BackupCrypto.MIN_PASSWORD_LENGTH))
                PasswordField(confirm, { confirm = it }, stringResource(R.string.field_password_confirm), visible, { visible = !visible },
                    error = if (mismatch) stringResource(R.string.export_password_mismatch) else null)
                TextButton(onClick = { onConfirm(null) }) {
                    Text(stringResource(R.string.export_unencrypted), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password.toCharArray()) }, enabled = !tooShort && confirm == password) {
                Text(stringResource(R.string.export_encrypt))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ImportPasswordDialog(wrongPassword: Boolean, busy: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(stringResource(R.string.import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_password_text), style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, stringResource(R.string.field_password), visible, { visible = !visible },
                    error = if (wrongPassword) stringResource(R.string.import_password_wrong) else null)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password.toCharArray()) }, enabled = password.isNotEmpty() && !busy) {
                Text(stringResource(R.string.import_decrypt))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    supporting: String? = null,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(128)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { t -> { Text(t) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
