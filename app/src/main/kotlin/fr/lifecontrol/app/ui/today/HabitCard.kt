package fr.lifecontrol.app.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.lifecontrol.app.R
import fr.lifecontrol.app.ui.components.CardShape
import fr.lifecontrol.app.ui.components.EmojiCircle
import fr.lifecontrol.app.ui.components.RoundedProgress
import fr.lifecontrol.app.ui.components.StatusDot
import fr.lifecontrol.app.ui.theme.HabitPalette
import fr.lifecontrol.app.ui.theme.LocalStatusColors
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.app.ui.util.goalLabel
import fr.lifecontrol.app.ui.util.streakLabel
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode

@Composable
fun statusText(status: DayStatus): String = stringResource(
    when (status) {
        DayStatus.SUCCESS -> R.string.status_success
        DayStatus.FAILURE -> R.string.status_failure
        DayStatus.MISSING -> R.string.status_missing
        DayStatus.PENDING -> R.string.status_pending
        DayStatus.NEUTRAL -> R.string.status_neutral
        DayStatus.FUTURE -> R.string.status_future
    },
)

/** Carte de saisie rapide d'une habitude (F-07). */
@Composable
fun HabitCard(
    state: HabitCardState,
    reorderMode: Boolean,
    onOpen: () -> Unit,
    onDone: (Boolean) -> Unit,
    onIncrement: (Double) -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val habit = state.habit
    val habitColor = HabitPalette.color(habit.colorIndex)
    val statusColors = LocalStatusColors.current
    val container by animateColorAsState(
        when (state.status) {
            DayStatus.SUCCESS -> statusColors.success.copy(alpha = 0.14f)
            DayStatus.FAILURE -> statusColors.failure.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "cardColor",
    )
    val statusLabel = statusText(state.status)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (dimmed) Modifier.alpha(0.6f) else Modifier)
            .semantics(mergeDescendants = false) { contentDescription = "${habit.name}, $statusLabel" },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface),
        onClick = onOpen,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiCircle(habit.emoji, habitColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            habit.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusDot(state.status, 16.dp)
                    }
                    Text(
                        goalLabel(habit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.streak.current > 0) {
                        Text(
                            "🔥 " + streakLabel(state.streak.current, state.streak.unit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (reorderMode) {
                    Column {
                        IconButton(onClick = { onMove(-1) }) {
                            Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.cd_move_up))
                        }
                        IconButton(onClick = { onMove(1) }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.cd_move_down))
                        }
                    }
                } else {
                    QuickControl(habit, state, habitColor, onDone, onIncrement, onOpen)
                }
            }
            state.dayProgress?.let {
                Spacer(Modifier.height(10.dp))
                RoundedProgress(it, color = if (it >= 1f) statusColors.success else habitColor)
            }
        }
    }
}

@Composable
private fun QuickControl(
    habit: Habit,
    state: HabitCardState,
    habitColor: Color,
    onDone: (Boolean) -> Unit,
    onIncrement: (Double) -> Unit,
    onOpen: () -> Unit,
) {
    val statusColors = LocalStatusColors.current
    when {
        habit.trackingMode == TrackingMode.BINARY && habit.direction == Direction.START -> {
            val done = state.entry?.done == true
            FilledIconButton(
                onClick = { onDone(true) },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (done) statusColors.success else habitColor.copy(alpha = 0.15f),
                    contentColor = if (done) statusColors.onSuccess else habitColor,
                ),
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(if (done) R.string.cd_undo_done else R.string.action_done),
                )
            }
        }
        habit.trackingMode == TrackingMode.BINARY -> {
            val value = state.entry?.done
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FilterChip(
                    selected = value == true,
                    onClick = { onDone(true) },
                    label = { Text(stringResource(R.string.action_held)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = statusColors.success,
                        selectedLabelColor = statusColors.onSuccess,
                    ),
                )
                FilterChip(
                    selected = value == false,
                    onClick = { onDone(false) },
                    label = { Text(stringResource(R.string.action_slipped)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = statusColors.failure,
                        selectedLabelColor = statusColors.onFailure,
                    ),
                )
            }
        }
        else -> {
            val quantity = state.entry?.quantity
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { onIncrement(-habit.step) }, enabled = (quantity ?: 0.0) > 0.0) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.cd_decrement))
                }
                val zeroShortcut = quantity == null && habit.direction != Direction.START
                val zeroLabel = stringResource(R.string.cd_record_zero)
                Box(
                    Modifier
                        .widthIn(min = 48.dp)
                        .clickable(onClick = if (zeroShortcut) ({ onIncrement(0.0) }) else onOpen)
                        .padding(horizontal = 4.dp)
                        .then(if (zeroShortcut) Modifier.semantics { contentDescription = zeroLabel } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        quantity?.let { if (habit.trackingMode == TrackingMode.DURATION) Fmt.minutes(it) else Fmt.number(it) }
                            ?: if (zeroShortcut) "0 ?" else "–",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (zeroShortcut) statusColors.success else Color.Unspecified,
                    )
                }
                FilledTonalIconButton(onClick = { onIncrement(habit.step) }) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.cd_increment))
                }
            }
        }
    }
}

@Composable
fun MoodPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    val moods = listOf("😞", "😕", "😐", "🙂", "😄")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        moods.forEachIndexed { i, emoji ->
            val value = i + 1
            val isSelected = selected == value
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        CircleShape,
                    )
                    .clickable { onSelect(if (isSelected) null else value) }
                    .semantics { contentDescription = "Humeur $value sur 5" },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
