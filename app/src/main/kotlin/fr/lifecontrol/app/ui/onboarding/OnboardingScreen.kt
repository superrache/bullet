package fr.lifecontrol.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.content.CatalogRepository
import fr.lifecontrol.app.data.content.HabitTemplate
import fr.lifecontrol.app.data.repository.DateProvider
import fr.lifecontrol.app.data.repository.HabitDraft
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.SettingsRepository
import fr.lifecontrol.app.notifications.ReminderScheduler
import fr.lifecontrol.app.ui.components.BrandHeader
import fr.lifecontrol.app.ui.components.EmojiCircle
import fr.lifecontrol.app.ui.components.SectionTitle
import fr.lifecontrol.app.ui.theme.HabitPalette
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val selected: List<String> = emptyList(),
    val thresholds: Map<String, String> = emptyMap(),
    val firstName: String = "",
    val eveningReminder: Boolean = true,
    val finished: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val habitRepository: HabitRepository,
    private val settingsRepository: SettingsRepository,
    private val dateProvider: DateProvider,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val templates: List<HabitTemplate> = catalogRepository.catalog.templates

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun toggle(template: HabitTemplate) = _state.update { s ->
        if (template.id in s.selected) s.copy(selected = s.selected - template.id)
        else s.copy(
            selected = s.selected + template.id,
            thresholds = s.thresholds + (template.id to (template.threshold?.let(Fmt::number) ?: "")),
        )
    }

    fun setThreshold(id: String, value: String) = _state.update { it.copy(thresholds = it.thresholds + (id to value.take(8))) }
    fun setFirstName(value: String) = _state.update { it.copy(firstName = value.take(30)) }
    fun setEveningReminder(value: Boolean) = _state.update { it.copy(eveningReminder = value) }

    fun finish() {
        val s = _state.value
        viewModelScope.launch {
            val today = dateProvider.today.value
            s.selected.mapNotNull(catalogRepository::template).forEach { t ->
                // Pas de doublon si l'habitude existe déjà (onboarding relancé).
                if (habitRepository.findDuplicate(t.name, t.id, null) != null) return@forEach
                val threshold = s.thresholds[t.id]?.replace(',', '.')?.toDoubleOrNull() ?: t.threshold
                habitRepository.saveHabit(
                    HabitDraft(
                        templateId = t.id,
                        name = t.name,
                        emoji = t.emoji,
                        colorIndex = t.color,
                        direction = t.direction,
                        trackingMode = t.mode,
                        unit = t.unit,
                        step = t.step,
                        startDate = today,
                        unitCostCents = t.unitCostCents,
                        baselinePerDay = t.baselinePerDay,
                        motivation = null,
                        passiveMode = false,
                        frequency = t.defaultFrequency,
                        threshold = threshold,
                    ),
                    today,
                )
            }
            settingsRepository.update {
                it.copy(onboardingDone = true, firstName = s.firstName.trim(), eveningReminderEnabled = s.eveningReminder)
            }
            scheduler.scheduleEvening()
            _state.update { it.copy(finished = true) }
        }
    }

    fun needsThreshold(t: HabitTemplate) =
        t.direction == Direction.LIMIT || (t.direction == Direction.START && t.mode != TrackingMode.BINARY)

    fun template(id: String) = catalogRepository.template(id)
}

private data class IntroPage(val emojis: List<String>, val title: Int, val body: Int, val color: Color)

private val introPages = listOf(
    IntroPage(listOf("📓", "✅", "🌱"), R.string.onboarding_1_title, R.string.onboarding_1_body, Color(0xFF5B4FD6)),
    IntroPage(listOf("📅", "📈", "🔥"), R.string.onboarding_2_title, R.string.onboarding_2_body, Color(0xFFE07A3F)),
    IntroPage(listOf("🏅", "🎖️", "🏆"), R.string.onboarding_3_title, R.string.onboarding_3_body, Color(0xFF2E9E78)),
)

/** Parcours de premier lancement (F-01). */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.finish() }

    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        if (step < introPages.size) {
            BrandHeader(Modifier.fillMaxWidth().padding(top = 24.dp))
        }
        Box(Modifier.weight(1f)) {
            AnimatedContent(targetState = step, label = "onboarding") { current ->
                when {
                    current < introPages.size -> IntroContent(introPages[current])
                    current == introPages.size -> ChooseHabits(viewModel, state)
                    else -> Configure(viewModel, state)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step < introPages.size) {
                TextButton(onClick = { step = introPages.size }) { Text(stringResource(R.string.onboarding_skip)) }
                Spacer(Modifier.weight(1f))
                Dots(step, introPages.size)
                Spacer(Modifier.weight(1f))
                Button(onClick = { step++ }) { Text(stringResource(R.string.onboarding_next)) }
            } else if (step == introPages.size) {
                TextButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { step++ }) {
                    Text(stringResource(if (state.selected.isEmpty()) R.string.onboarding_later else R.string.onboarding_next))
                }
            } else {
                TextButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (state.eveningReminder && Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.finish()
                    }
                }) { Text(stringResource(R.string.onboarding_start)) }
            }
        }
    }
}

@Composable
private fun Dots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        }
    }
}

@Composable
private fun IntroContent(page: IntroPage) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Illustration(page.emojis, page.color)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Illustration vectorielle légère : formes douces et emojis. */
@Composable
fun Illustration(emojis: List<String>, color: Color) {
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color.copy(alpha = 0.12f), radius = size.minDimension / 2)
            drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension / 3, center = Offset(size.width * 0.62f, size.height * 0.42f))
            drawCircle(color.copy(alpha = 0.22f), radius = size.minDimension / 7, center = Offset(size.width * 0.25f, size.height * 0.72f))
            drawCircle(color.copy(alpha = 0.3f), radius = size.minDimension / 14, center = Offset(size.width * 0.82f, size.height * 0.8f))
        }
        Text(emojis[1], fontSize = 96.sp)
        Text(emojis[0], fontSize = 44.sp, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
        Text(emojis[2], fontSize = 44.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp))
    }
}

@Composable
private fun ChooseHabits(viewModel: OnboardingViewModel, state: OnboardingState) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.onboarding_choose_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.onboarding_choose_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        listOf(
            R.string.catalog_section_stop to listOf(Direction.QUIT, Direction.LIMIT),
            R.string.catalog_section_start to listOf(Direction.START),
        ).forEach { (title, directions) ->
            item { SectionTitle(stringResource(title)) }
            items(viewModel.templates.filter { it.direction in directions }, key = { it.id }) { t ->
                val checked = t.id in state.selected
                Card(
                    onClick = { viewModel.toggle(t) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (checked) HabitPalette.color(t.color).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiCircle(t.emoji, HabitPalette.color(t.color), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            Text(t.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = checked, onCheckedChange = { viewModel.toggle(t) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Configure(viewModel: OnboardingViewModel, state: OnboardingState) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.onboarding_config_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.firstName,
            onValueChange = viewModel::setFirstName,
            label = { Text(stringResource(R.string.settings_first_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val withThreshold = state.selected.mapNotNull(viewModel::template).filter(viewModel::needsThreshold)
        if (withThreshold.isNotEmpty()) {
            SectionTitle(stringResource(R.string.onboarding_goals))
            withThreshold.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("${t.emoji} ${t.name}", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = state.thresholds[t.id].orEmpty(),
                        onValueChange = { viewModel.setThreshold(t.id, it) },
                        prefix = { Text(if (t.direction == Direction.LIMIT) "≤ " else "≥ ") },
                        suffix = { Text(if (t.mode == TrackingMode.DURATION) "min" else t.unit.orEmpty()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
            Text(stringResource(R.string.onboarding_goals_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionTitle(stringResource(R.string.field_reminders))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_evening_reminder))
                Text(stringResource(R.string.settings_evening_reminder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.eveningReminder, onCheckedChange = viewModel::setEveningReminder)
        }
    }
}

