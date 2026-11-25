package fr.lifecontrol.app.ui.badges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.repository.BadgeService
import fr.lifecontrol.app.data.repository.BadgeView
import fr.lifecontrol.app.ui.cards.CardsCollection
import fr.lifecontrol.app.ui.cards.CardsViewModel
import fr.lifecontrol.app.ui.components.BadgeMedal
import fr.lifecontrol.app.ui.components.Confetti
import fr.lifecontrol.app.ui.components.PopIn
import fr.lifecontrol.app.ui.components.RoundedProgress
import fr.lifecontrol.app.ui.components.tierColor
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.badge.BadgeUnit
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class BadgesViewModel @Inject constructor(badgeService: BadgeService) : ViewModel() {
    val badges = badgeService.badges
}

private enum class BadgeFilter { ALL, UNLOCKED, IN_PROGRESS, CLOSEST }

@Composable
fun unitLabel(unit: BadgeUnit, count: Long, habitUnit: String? = null): String {
    val n = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return when (unit) {
        BadgeUnit.COUNT -> ""
        BadgeUnit.UNITS -> habitUnit.orEmpty()
        BadgeUnit.DAYS -> pluralStringResource(R.plurals.unit_days, n)
        BadgeUnit.WEEKS -> pluralStringResource(R.plurals.unit_weeks, n)
        BadgeUnit.MONTHS -> pluralStringResource(R.plurals.unit_months, n)
        BadgeUnit.QUARTERS -> pluralStringResource(R.plurals.unit_quarters, n)
        BadgeUnit.YEARS -> pluralStringResource(R.plurals.unit_years, n)
        BadgeUnit.EUROS -> "€"
        BadgeUnit.HOURS -> "h"
    }
}

/** « 12 / 30 jours » (F-17). */
@Composable
fun badgeProgressText(badge: BadgeView): String {
    val target = badge.nextTarget ?: return stringResource(R.string.badge_all_tiers)
    val unit = unitLabel(badge.unit, target, badge.habitUnit)
    return "${badge.current} / $target $unit".trim()
}

@Composable
fun badgeDescription(badge: BadgeView): String {
    val target = badge.nextTarget ?: badge.tiers.lastOrNull() ?: 0
    return badge.meta.description
        .replace("{n}", target.toString())
        .replace("{unit}", unitLabel(badge.unit, target, badge.habitUnit))
        .replace("  ", " ")
}

@Composable
fun BadgesScreen(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: BadgesViewModel = hiltViewModel(),
    cardsViewModel: CardsViewModel = hiltViewModel(),
) {
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val cards by cardsViewModel.cards.collectAsStateWithLifecycle()
    val generatingCards by cardsViewModel.generating.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(BadgeFilter.ALL) }
    var selected by remember { mutableStateOf<BadgeView?>(null) }

    val shown = when (filter) {
        BadgeFilter.ALL -> badges
        BadgeFilter.UNLOCKED -> badges.filter { it.isUnlocked }
        BadgeFilter.IN_PROGRESS -> badges.filter { !it.isComplete }
        BadgeFilter.CLOSEST -> badges.filter { !it.isComplete }.sortedByDescending { it.fraction }
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_badges)) }) },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.badges_tab_badges)) })
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.badges_tab_cards, cards.count { it.isReady })) },
                )
            }
            val gridPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
            if (tab == 1) {
                CardsCollection(cards, generatingCards, cardsViewModel::retry, gridPadding)
            } else LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                contentPadding = gridPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        val unlocked = badges.count { it.isUnlocked }
                        Text(
                            pluralStringResource(R.plurals.badges_count, unlocked, unlocked, badges.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BadgeFilter.entries.forEach { f ->
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filter = f },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (f) {
                                                    BadgeFilter.ALL -> R.string.filter_all
                                                    BadgeFilter.UNLOCKED -> R.string.filter_unlocked
                                                    BadgeFilter.IN_PROGRESS -> R.string.filter_in_progress
                                                    BadgeFilter.CLOSEST -> R.string.filter_closest
                                                },
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.badges_empty),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(shown, key = { it.key }) { badge ->
                    Column(
                        Modifier
                            .clickable { selected = badge }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BadgeMedal(badge.meta.emoji, badge.level, badge.fraction, size = 76.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            badge.meta.title,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        badge.habitName?.let {
                            Text(
                                listOfNotNull(badge.habitEmoji, it).joinToString(" "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (badge.tiers.isNotEmpty() && badge.tiers.any { it > 0 }) {
                            Text(
                                badgeProgressText(badge),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { BadgeDetailDialog(it) { selected = null } }
}

@Composable
fun BadgeDetailDialog(badge: BadgeView, celebrate: Boolean = false, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            PopIn(badge.key) { scale ->
                BadgeMedal(badge.meta.emoji, badge.level, badge.fraction, size = 110.dp, modifier = Modifier.scale(scale))
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (celebrate) {
                    Text(stringResource(R.string.badge_unlocked), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(badge.meta.title, textAlign = TextAlign.Center)
                badge.habitName?.let {
                    Text(listOfNotNull(badge.habitEmoji, it).joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        text = {
            Column {
                if (badge.tiers.any { it > 0 }) {
                    Text(badgeDescription(badge), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    if (!badge.isComplete) {
                        RoundedProgress(badge.fraction)
                        Text(badgeProgressText(badge), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                    if (badge.tiers.size == 1) {
                        badge.unlockDates[1]?.let { date ->
                            Text(
                                stringResource(R.string.badge_unlocked_on, Fmt.dayShort(Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate())),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else badge.tiers.forEachIndexed { i, target ->
                        val level = i + 1
                        val date = badge.unlockDates[level]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(if (level <= badge.level) "●" else "○", color = tierColor(level), fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("$target ${unitLabel(badge.unit, target, badge.habitUnit)}".trim(), modifier = Modifier.weight(1f))
                            if (date != null) {
                                Text(
                                    Fmt.dayShort(Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.badge_kept_forever), textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
    if (celebrate) Confetti(trigger = badge.key)
}
