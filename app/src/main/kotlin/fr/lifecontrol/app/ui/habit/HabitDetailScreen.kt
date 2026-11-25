package fr.lifecontrol.app.ui.habit

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.content.CatalogRepository
import fr.lifecontrol.app.data.content.HabitTemplate
import fr.lifecontrol.app.data.repository.BadgeService
import fr.lifecontrol.app.data.repository.BadgeView
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.Journal
import fr.lifecontrol.app.data.repository.JournalRepository
import fr.lifecontrol.app.notifications.ReminderScheduler
import fr.lifecontrol.app.ui.badges.BadgeDetailDialog
import fr.lifecontrol.app.ui.components.BadgeMedal
import fr.lifecontrol.app.ui.components.CardShape
import fr.lifecontrol.app.ui.components.EmojiCircle
import fr.lifecontrol.app.ui.components.MonthGrid
import fr.lifecontrol.app.ui.components.MonthHeader
import fr.lifecontrol.app.ui.components.SectionTitle
import fr.lifecontrol.app.ui.components.StatusDayCell
import fr.lifecontrol.app.ui.theme.HabitPalette
import fr.lifecontrol.app.ui.today.statusText
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.app.ui.util.goalLabel
import fr.lifecontrol.domain.eval.HabitHistory
import fr.lifecontrol.domain.eval.HabitStats
import fr.lifecontrol.domain.eval.StatsCalculator
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.roundToInt

data class HabitDetailState(
    val journal: Journal,
    val habit: Habit,
    val history: HabitHistory,
    val stats: HabitStats,
    val month: YearMonth,
    val badges: List<BadgeView>,
    val template: HabitTemplate?,
)

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    journalRepository: JournalRepository,
    badgeService: BadgeService,
    private val catalogRepository: CatalogRepository,
    private val habitRepository: HabitRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    private val habitId: String = checkNotNull(savedStateHandle["habitId"])
    private val month = MutableStateFlow(YearMonth.now())
    val deleted = MutableStateFlow(false)

    val state: StateFlow<HabitDetailState?> = combine(
        journalRepository.journal.filterNotNull(), month, badgeService.badges,
    ) { journal, month, badges ->
        val habit = journal.habit(habitId) ?: return@combine null
        val history = journal.history(habitId)!!
        HabitDetailState(
            journal = journal,
            habit = habit,
            history = history,
            stats = StatsCalculator.compute(history, habit.startDate, history.lastDay),
            month = month,
            badges = badges.filter { it.habitId == habitId },
            template = catalogRepository.template(habit.templateId),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftMonth(offset: Long) = month.update { it.plusMonths(offset) }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            val today = state.value?.journal?.today ?: LocalDate.now()
            habitRepository.setArchived(habitId, if (archived) today else null)
            habitRepository.remindersFor(habitId).forEach {
                if (archived) scheduler.cancel(it.id) else scheduler.schedule(it)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            habitRepository.remindersFor(habitId).forEach { scheduler.cancel(it.id) }
            habitRepository.deleteHabit(habitId)
            deleted.value = true
        }
    }
}

@Composable
fun HabitDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenHelp: () -> Unit,
    viewModel: HabitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedBadge by remember { mutableStateOf<BadgeView?>(null) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.habit?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
                },
                actions = {
                    val s = state ?: return@TopAppBar
                    IconButton(onClick = { onEdit(s.habit.id) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (s.habit.isArchived) R.string.action_unarchive else R.string.action_archive)) },
                                onClick = {
                                    menu = false
                                    viewModel.setArchived(!s.habit.isArchived)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val habit = s.habit
        val color = HabitPalette.color(habit.colorIndex)
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiCircle(habit.emoji, color, 72.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(goalLabel(habit), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.habit_since, Fmt.dayShort(habit.startDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (habit.isArchived) {
                        Text(stringResource(R.string.habit_archived_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            habit.motivation?.let {
                Spacer(Modifier.height(16.dp))
                Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.label_motivation), style = MaterialTheme.typography.labelLarge)
                        Text("« $it »", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            SectionTitle(stringResource(R.string.habit_since_start))
            HabitStatsBlock(habit, s.history.streak, s.stats, null, s.journal.settings.currency)

            SectionTitle(stringResource(R.string.tab_calendar))
            MonthHeader(s.month, { viewModel.shiftMonth(-1) }, { viewModel.shiftMonth(1) }, s.month.atEndOfMonth() < s.journal.today)
            MonthGrid(s.month, s.journal.settings.firstDayOfWeek) { date, modifier ->
                val status = s.history.status(date)
                val value = s.history.entry(date)?.quantity?.takeIf { habit.trackingMode != TrackingMode.BINARY }?.let {
                    if (habit.trackingMode == TrackingMode.DURATION) "${it.roundToInt()}′" else Fmt.number(it)
                }
                StatusDayCell(date, status, value, date == s.journal.today, statusText(status), modifier) { onOpenDay(date) }
            }

            if (s.badges.isNotEmpty()) {
                SectionTitle(stringResource(R.string.badges_tab_badges))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.badges.forEach { badge ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
                            IconButton(onClick = { selectedBadge = badge }, modifier = Modifier.height(68.dp).width(68.dp)) {
                                BadgeMedal(badge.meta.emoji, badge.level, badge.fraction, size = 64.dp)
                            }
                            Text(badge.meta.title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }

            if (s.template != null) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { onOpenArticle(s.template.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_read_article))
                }
                if (s.template.help.isNotEmpty()) {
                    TextButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.help_title)) }
                }
            }

            // Archiver / supprimer : aussi dans le menu ⋮, mais visibles ici pour qu'on les trouve.
            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.setArchived(!habit.isArchived) }, modifier = Modifier.weight(1f)) {
                    Icon(if (habit.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (habit.isArchived) R.string.action_unarchive else R.string.action_archive))
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteConfirmDialog(
            text = stringResource(R.string.delete_habit_text),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    selectedBadge?.let { BadgeDetailDialog(it) { selectedBadge = null } }
}

/** Confirmation explicite par saisie du mot SUPPRIMER (F-05). */
@Composable
fun DeleteConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val keyword = stringResource(R.string.delete_keyword)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column {
                Text(text)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.delete_type_keyword, keyword)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = typed.trim().equals(keyword, ignoreCase = true),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
