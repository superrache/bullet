package fr.lifecontrol.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.repository.BadgeView
import fr.lifecontrol.app.ui.badges.badgeProgressText
import fr.lifecontrol.app.ui.components.BadgeMedal
import fr.lifecontrol.app.ui.components.BrandWordmark
import fr.lifecontrol.app.ui.components.CardShape
import fr.lifecontrol.app.ui.components.Confetti
import fr.lifecontrol.app.ui.components.EmptyState
import fr.lifecontrol.app.ui.components.RoundedProgress
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.model.Habit

@Composable
fun TodayScreen(
    onOpenHabit: (String) -> Unit,
    onAddHabit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenHelp: () -> Unit,
    onBack: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sheetHabitId by remember { mutableStateOf<String?>(null) }
    var reorder by remember { mutableStateOf(false) }
    var celebration by remember { mutableIntStateOf(0) }
    var slip by remember { mutableStateOf<TodayEvent.Slip?>(null) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                TodayEvent.Celebrate -> celebration++
                is TodayEvent.Slip -> slip = event
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            val s = state
                            val greeting = when {
                                s == null -> ""
                                !s.isToday -> stringResource(R.string.today_past_day)
                                s.firstName.isNotBlank() -> stringResource(R.string.today_hello_name, s.firstName)
                                else -> stringResource(R.string.today_hello)
                            }
                            Row {
                                if (onBack == null) {
                                    BrandWordmark(Modifier.alignByBaseline(), fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    greeting,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.alignByBaseline(),
                                    maxLines = 1,
                                )
                            }
                            Text(s?.date?.let { Fmt.dayLong(it) } ?: "", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                            }
                        }
                    },
                    actions = {
                        if (state?.hasHabits == true) {
                            IconButton(onClick = { reorder = !reorder }) {
                                Icon(
                                    if (reorder) Icons.Rounded.Check else Icons.Rounded.SwapVert,
                                    stringResource(R.string.cd_reorder),
                                )
                            }
                        }
                        if (onBack == null) {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            val s = state
            if (s == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DayNavigator(
                        canGoNext = s.date < s.today,
                        showToday = !s.isToday && onBack == null,
                        onPrevious = viewModel::previousDay,
                        onNext = viewModel::nextDay,
                        onToday = viewModel::goToToday,
                    )
                }
                if (!s.hasHabits) {
                    item {
                        EmptyState(
                            emoji = "🌱",
                            title = stringResource(R.string.today_empty_title),
                            body = stringResource(R.string.today_empty_body),
                        ) {
                            Button(onClick = onAddHabit) { Text(stringResource(R.string.action_add_habit)) }
                        }
                    }
                } else {
                    item { SummaryCard(s.successCount, s.scheduled.size, s.isToday, s.nearestBadge) }
                    items(s.scheduled, key = { it.habit.id }) { card ->
                        HabitCard(
                            state = card,
                            reorderMode = reorder,
                            onOpen = { sheetHabitId = card.habit.id },
                            onDone = { viewModel.setDone(card.habit, it) },
                            onIncrement = { viewModel.increment(card.habit, it) },
                            onMove = { viewModel.move(card.habit, it) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (s.unscheduled.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.today_not_scheduled),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        items(s.unscheduled, key = { it.habit.id }) { card ->
                            HabitCard(
                                state = card,
                                reorderMode = reorder,
                                onOpen = { sheetHabitId = card.habit.id },
                                onDone = { viewModel.setDone(card.habit, it) },
                                onIncrement = { viewModel.increment(card.habit, it) },
                                onMove = { viewModel.move(card.habit, it) },
                                dimmed = true,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            val sheetCard = s.scheduled.plus(s.unscheduled).firstOrNull { it.habit.id == sheetHabitId }
            if (sheetCard != null) {
                EntrySheet(
                    state = sheetCard,
                    date = s.date,
                    triggers = s.triggers,
                    onDismiss = { sheetHabitId = null },
                    onSave = { done, quantity, note, mood, trigger ->
                        viewModel.saveDetails(sheetCard.habit, done, quantity, note, mood, trigger)
                        sheetHabitId = null
                    },
                    onClear = {
                        viewModel.clear(sheetCard.habit)
                        sheetHabitId = null
                    },
                    onOpenHabit = {
                        sheetHabitId = null
                        onOpenHabit(sheetCard.habit.id)
                    },
                )
            }
        }
        if (celebration > 0) Confetti(trigger = celebration)
    }

    slip?.let { event ->
        SlipDialog(
            habit = event.habit,
            encouragement = event.encouragement,
            hasHelp = event.hasHelp,
            hasArticle = event.hasArticle,
            onDismiss = { slip = null },
            onHelp = {
                slip = null
                onOpenHelp()
            },
            onArticle = {
                slip = null
                event.habit.templateId?.let(onOpenArticle)
            },
        )
    }
}

@Composable
private fun DayNavigator(
    canGoNext: Boolean,
    showToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
            Text(stringResource(R.string.day_previous))
        }
        Spacer(Modifier.weight(1f))
        if (showToday) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.day_today)) }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onNext, enabled = canGoNext) {
            Text(stringResource(R.string.day_next))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

/** Résumé du jour et badge le plus proche (F-11). */
@Composable
private fun SummaryCard(success: Int, total: Int, isToday: Boolean, badge: BadgeView?) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (total == 0) 0f else success.toFloat() / total },
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    )
                    Text("$success/$total", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.today_summary, total, success, total),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(
                            when {
                                total == 0 -> R.string.today_summary_rest
                                success == total -> R.string.today_summary_all
                                isToday -> R.string.today_summary_keep_going
                                else -> R.string.today_summary_past
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (badge != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BadgeMedal(badge.meta.emoji, badge.level, badge.fraction, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.today_next_badge, badge.meta.title) +
                                (badge.habitName?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        RoundedProgress(badge.fraction)
                        Text(
                            badgeProgressText(badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/** Message bienveillant après un écart (F-10). */
@Composable
private fun SlipDialog(
    habit: Habit,
    encouragement: String,
    hasHelp: Boolean,
    hasArticle: Boolean,
    onDismiss: () -> Unit,
    onHelp: () -> Unit,
    onArticle: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🌱", fontSize = 40.sp) },
        title = { Text(stringResource(R.string.slip_title), textAlign = TextAlign.Center) },
        text = {
            Column {
                Text(encouragement)
                habit.motivation?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.slip_your_motivation), style = MaterialTheme.typography.labelLarge)
                    Text("« $it »", style = MaterialTheme.typography.bodyMedium)
                }
                if (hasArticle) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onArticle) { Text(stringResource(R.string.slip_read_tips)) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.slip_ok)) } },
        dismissButton = {
            if (hasHelp) TextButton(onClick = onHelp) { Text(stringResource(R.string.help_title)) }
        },
    )
}
