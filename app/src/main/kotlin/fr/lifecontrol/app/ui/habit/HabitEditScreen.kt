package fr.lifecontrol.app.ui.habit

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.content.HabitVariant
import fr.lifecontrol.app.data.db.toDays
import fr.lifecontrol.app.data.repository.Reminder
import fr.lifecontrol.app.notifications.Notifications
import fr.lifecontrol.app.ui.components.CardShape
import fr.lifecontrol.app.ui.components.SectionTitle
import fr.lifecontrol.app.ui.theme.HabitPalette
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.TrackingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

val EmojiChoices = listOf(
    "🚭", "🍷", "🍺", "🥩", "🍬", "🥤", "📱", "☕", "🍪", "🎰", "🛍️", "🌿", "💅", "🎮",
    "🏃", "📚", "🥦", "🥗", "🧘", "💧", "🚶", "😴", "✍️", "🗣️", "🙏", "🍳", "🚴", "🏊",
    "💪", "🎸", "🎨", "🌱", "🌞", "🧠", "❤️", "⭐", "🎯", "🧹", "💰", "📵", "🐶", "🌍",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenHabit: (String) -> Unit = {},
    viewModel: HabitEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
    val variants by viewModel.variants.collectAsStateWithLifecycle()
    var showEmoji by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var editTimeFor by remember { mutableStateOf<Reminder?>(null) }
    var newReminder by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) { viewModel.saved.collect(onSaved) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (form.isNew) R.string.edit_title_new else R.string.edit_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = form.loaded && duplicate == null) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .background(HabitPalette.color(form.colorIndex).copy(alpha = 0.2f), CircleShape)
                        .clickable { showEmoji = true },
                    contentAlignment = Alignment.Center,
                ) { Text(form.emoji, fontSize = 32.sp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v.take(40)) } },
                    label = { Text(stringResource(R.string.field_name)) },
                    isError = FormError.NAME in errors || duplicate != null,
                    supportingText = when {
                        FormError.NAME in errors -> ({ Text(stringResource(R.string.error_name)) })
                        duplicate != null -> ({
                            val d = duplicate!!
                            Text(stringResource(if (d.isArchived) R.string.error_duplicate_archived else R.string.error_duplicate, d.name))
                        })
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            duplicate?.let { d ->
                TextButton(onClick = { onOpenHabit(d.id) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_open_existing, d.name))
                }
            }
            if (form.isNew && variants.size > 1) {
                SectionTitle(stringResource(R.string.field_variant))
                Column(Modifier.selectableGroup()) {
                    variants.forEach { variant ->
                        VariantOption(variant, selected = form.variantId == variant.id) { viewModel.selectVariant(variant.id) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitPalette.colors.forEachIndexed { i, color -> ColorSwatch(color, form.colorIndex == i) { viewModel.update { it.copy(colorIndex = i) } } }
            }

            SectionTitle(stringResource(R.string.field_direction))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Direction.QUIT to R.string.direction_quit, Direction.LIMIT to R.string.direction_limit, Direction.START to R.string.direction_start)
                    .forEachIndexed { i, (d, label) ->
                        SegmentedButton(
                            selected = form.direction == d,
                            onClick = { viewModel.update { it.copy(direction = d) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(stringResource(label)) }
                    }
            }

            SectionTitle(stringResource(R.string.field_mode))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TrackingMode.BINARY to R.string.mode_binary, TrackingMode.QUANTITY to R.string.mode_quantity, TrackingMode.DURATION to R.string.mode_duration)
                    .forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = form.trackingMode == m,
                            enabled = !(form.direction == Direction.LIMIT && m == TrackingMode.BINARY),
                            onClick = { viewModel.update { it.copy(trackingMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(stringResource(label)) }
                    }
            }
            Text(
                stringResource(modeHint(form.direction, form.trackingMode)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (form.trackingMode != TrackingMode.BINARY) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (form.trackingMode == TrackingMode.QUANTITY) {
                        OutlinedTextField(
                            value = form.unit,
                            onValueChange = { v -> viewModel.update { it.copy(unit = v.take(20)) } },
                            label = { Text(stringResource(R.string.field_unit)) },
                            placeholder = { Text(stringResource(R.string.field_unit_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = form.step,
                        onValueChange = { v -> viewModel.update { it.copy(step = v.take(6)) } },
                        label = { Text(stringResource(R.string.field_step)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (form.needsThreshold) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = form.threshold,
                    onValueChange = { v -> viewModel.update { it.copy(threshold = v.take(8)) } },
                    label = {
                        Text(
                            stringResource(if (form.direction == Direction.LIMIT) R.string.field_threshold_max else R.string.field_threshold_min) +
                                if (form.trackingMode == TrackingMode.DURATION) " (min)" else form.unit.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                        )
                    },
                    isError = FormError.THRESHOLD in errors,
                    supportingText = if (FormError.THRESHOLD in errors) ({ Text(stringResource(R.string.error_threshold)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle(stringResource(R.string.field_frequency))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrequencyKind.entries.filter { it != FrequencyKind.TIMES_PER_WEEK || form.direction == Direction.START }.forEach { kind ->
                    FilterChip(
                        selected = form.frequencyKind == kind,
                        onClick = { viewModel.update { it.copy(frequencyKind = kind) } },
                        label = {
                            Text(
                                stringResource(
                                    when (kind) {
                                        FrequencyKind.DAILY -> R.string.freq_daily
                                        FrequencyKind.TIMES_PER_WEEK -> R.string.freq_n_per_week
                                        FrequencyKind.DAYS_OF_WEEK -> R.string.freq_some_days
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            when (form.frequencyKind) {
                FrequencyKind.TIMES_PER_WEEK -> {
                    Text(pluralStringResource(R.plurals.freq_times_per_week, form.timesPerWeek, form.timesPerWeek))
                    Slider(
                        value = form.timesPerWeek.toFloat(),
                        onValueChange = { v -> viewModel.update { it.copy(timesPerWeek = v.toInt()) } },
                        valueRange = 1f..7f,
                        steps = 5,
                    )
                }
                FrequencyKind.DAYS_OF_WEEK -> {
                    DaysSelector(form.daysMask) { mask -> viewModel.update { it.copy(daysMask = mask) } }
                    if (FormError.DAYS in errors) Text(stringResource(R.string.error_days), color = MaterialTheme.colorScheme.error)
                }
                else -> Unit
            }

            SectionTitle(stringResource(R.string.field_start_date))
            OutlinedButton(onClick = { showDate = true }) { Text(Fmt.dayShort(form.startDate)) }

            if (form.allowsCost) {
                SectionTitle(stringResource(R.string.field_savings))
                Text(stringResource(R.string.field_savings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.unitCost,
                        onValueChange = { v -> viewModel.update { it.copy(unitCost = v.take(8)) } },
                        label = { Text(stringResource(R.string.field_unit_cost)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.baseline,
                        onValueChange = { v -> viewModel.update { it.copy(baseline = v.take(8)) } },
                        label = { Text(stringResource(R.string.field_baseline)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_passive), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.field_passive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = form.passiveMode, onCheckedChange = { v -> viewModel.update { it.copy(passiveMode = v) } })
                }
            }

            SectionTitle(stringResource(R.string.label_motivation))
            OutlinedTextField(
                value = form.motivation,
                onValueChange = { v -> viewModel.update { it.copy(motivation = v.take(200)) } },
                placeholder = { Text(stringResource(R.string.field_motivation_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.field_reminders))
            form.reminders.forEach { reminder ->
                Column(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editTimeFor = reminder }) {
                            Text(Fmt.time(reminder.timeMinutes), style = MaterialTheme.typography.headlineSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        Switch(checked = reminder.enabled, onCheckedChange = { viewModel.updateReminder(reminder.copy(enabled = it)) })
                        IconButton(onClick = { viewModel.removeReminder(reminder.id) }) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete))
                        }
                    }
                    DaysSelector(reminder.daysMask) { viewModel.updateReminder(reminder.copy(daysMask = it)) }
                }
            }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !Notifications.canNotify(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                newReminder = true
            }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_add_reminder))
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), enabled = form.loaded) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (showEmoji) {
        AlertDialog(
            onDismissRequest = { showEmoji = false },
            title = { Text(stringResource(R.string.field_icon)) },
            text = {
                LazyVerticalGrid(GridCells.Adaptive(48.dp), modifier = Modifier.height(320.dp)) {
                    items(EmojiChoices) { e ->
                        Box(
                            Modifier.size(48.dp).clickable {
                                viewModel.update { it.copy(emoji = e) }
                                showEmoji = false
                            },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 26.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEmoji = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.update { it.copy(startDate = date) }
                    }
                    showDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }

    if (newReminder || editTimeFor != null) {
        val initial = editTimeFor?.timeMinutes ?: (20 * 60)
        TimeDialog(
            initialMinutes = initial,
            onDismiss = {
                newReminder = false
                editTimeFor = null
            },
            onConfirm = { minutes ->
                editTimeFor?.let { viewModel.updateReminder(it.copy(timeMinutes = minutes)) } ?: viewModel.addReminder(minutes)
                newReminder = false
                editTimeFor = null
            },
        )
    }
}

/** Une façon de suivre proposée par le modèle (ex. « Compter mes repas avec viande »). */
@Composable
private fun VariantOption(variant: HabitVariant, selected: Boolean, onClick: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(variant.label, style = MaterialTheme.typography.titleSmall)
                Text(variant.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun modeHint(direction: Direction, mode: TrackingMode): Int = when {
    direction == Direction.QUIT && mode == TrackingMode.BINARY -> R.string.hint_quit_binary
    direction == Direction.QUIT -> R.string.hint_quit_quantity
    direction == Direction.LIMIT -> R.string.hint_limit
    mode == TrackingMode.BINARY -> R.string.hint_start_binary
    mode == TrackingMode.DURATION -> R.string.hint_start_duration
    else -> R.string.hint_start_quantity
}

@Composable
fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(color, CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DaysSelector(mask: Int, onChange: (Int) -> Unit) {
    val days = mask.toDays()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            FilterChip(
                selected = day in days,
                onClick = { onChange(mask xor bit) },
                label = { Text(Fmt.weekdayShort(day)) },
            )
        }
    }
}
