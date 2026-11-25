package fr.lifecontrol.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.repository.Trigger
import fr.lifecontrol.app.ui.components.EmojiCircle
import fr.lifecontrol.app.ui.theme.HabitPalette
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.app.ui.util.goalLabel
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import java.time.LocalDate

/** Détail d'une entrée : valeur, note, humeur, déclencheur (F-08). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntrySheet(
    state: HabitCardState,
    date: LocalDate,
    triggers: List<Trigger>,
    onDismiss: () -> Unit,
    onSave: (done: Boolean?, quantity: Double?, note: String?, mood: Int?, triggerId: String?) -> Unit,
    onClear: () -> Unit,
    onOpenHabit: () -> Unit,
) {
    val habit: Habit = state.habit
    val entry = state.entry
    var done by remember { mutableStateOf(entry?.done) }
    var quantityText by remember { mutableStateOf(entry?.quantity?.let { Fmt.number(it) } ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    var mood by remember { mutableStateOf(entry?.mood) }
    var trigger by remember { mutableStateOf(entry?.triggerId) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiCircle(habit.emoji, HabitPalette.color(habit.colorIndex))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        Fmt.dayLong(date) + " · " + goalLabel(habit, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            if (habit.trackingMode == TrackingMode.BINARY) {
                val labels = if (habit.direction == Direction.START) {
                    stringResource(R.string.action_done) to stringResource(R.string.action_not_done)
                } else {
                    stringResource(R.string.action_held) to stringResource(R.string.action_slipped)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = done == true,
                        onClick = { done = if (done == true) null else true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(labels.first) }
                    SegmentedButton(
                        selected = done == false,
                        onClick = { done = if (done == false) null else false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(labels.second) }
                }
            } else {
                val parsed = quantityText.replace(',', '.').toDoubleOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        quantityText = Fmt.number(((parsed ?: 0.0) - habit.step).coerceAtLeast(0.0))
                    }) { Icon(Icons.Rounded.Remove, stringResource(R.string.cd_decrement)) }
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) },
                        label = {
                            Text(
                                if (habit.trackingMode == TrackingMode.DURATION) stringResource(R.string.label_minutes)
                                else habit.unit ?: stringResource(R.string.label_quantity),
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    FilledTonalIconButton(onClick = {
                        quantityText = Fmt.number((parsed ?: 0.0) + habit.step)
                    }) { Icon(Icons.Rounded.Add, stringResource(R.string.cd_increment)) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.label_mood), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            MoodPicker(mood) { mood = it }

            if (habit.direction != Direction.START) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.label_trigger), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    triggers.forEach { t ->
                        FilterChip(
                            selected = trigger == t.id,
                            onClick = { trigger = if (trigger == t.id) null else t.id },
                            label = { Text(t.label) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(TodayViewModel.MAX_NOTE) },
                label = { Text(stringResource(R.string.label_note)) },
                supportingText = { Text("${note.length} / ${TodayViewModel.MAX_NOTE}") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenHabit) { Text(stringResource(R.string.action_see_habit)) }
                Spacer(Modifier.weight(1f))
                if (entry != null) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                }
                Button(onClick = {
                    val q = if (habit.trackingMode == TrackingMode.BINARY) null else quantityText.replace(',', '.').toDoubleOrNull()
                    onSave(if (habit.trackingMode == TrackingMode.BINARY) done else null, q, note, mood, trigger)
                }) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
