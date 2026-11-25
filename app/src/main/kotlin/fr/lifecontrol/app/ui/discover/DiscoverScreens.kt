package fr.lifecontrol.app.ui.discover

import android.content.Intent
import androidx.core.net.toUri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.content.Catalog
import fr.lifecontrol.app.data.content.CatalogRepository
import fr.lifecontrol.app.data.content.HabitTemplate
import fr.lifecontrol.app.data.content.HelpResource
import fr.lifecontrol.app.data.content.canonicalTemplateId
import fr.lifecontrol.app.data.repository.BadgeService
import fr.lifecontrol.app.data.repository.JournalRepository
import fr.lifecontrol.app.ui.components.CardShape
import fr.lifecontrol.app.ui.components.EmojiCircle
import fr.lifecontrol.app.ui.components.SectionTitle
import fr.lifecontrol.app.ui.theme.HabitPalette
import fr.lifecontrol.domain.model.Direction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    journalRepository: JournalRepository,
) : ViewModel() {
    val catalog: Catalog = catalogRepository.catalog

    /** Modèles déjà suivis (habitudes actives). */
    val tracked: StateFlow<Set<String>> = journalRepository.journal
        .map { j -> j?.activeHabits?.mapNotNull { canonicalTemplateId(it.templateId) }?.toSet().orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}

/**
 * Catalogue (F-19). En mode [pickMode], toucher un modèle ouvre directement le formulaire de création ;
 * sinon il ouvre la fiche documentée.
 */
@Composable
fun CatalogScreen(
    pickMode: Boolean,
    onOpenTemplate: (String) -> Unit,
    onCreateCustom: () -> Unit,
    onOpenHelp: () -> Unit,
    onBack: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val tracked by viewModel.tracked.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = viewModel.catalog

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (pickMode) R.string.action_add_habit else R.string.tab_discover)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    onClick = onCreateCustom,
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.catalog_custom), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.catalog_custom_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (!pickMode) {
                item {
                    Card(
                        onClick = onOpenHelp,
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("🤝", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.help_card_hint), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.filter_all)) })
                    }
                    items(catalog.categories) { c ->
                        FilterChip(selected = category == c.id, onClick = { category = c.id }, label = { Text("${c.emoji} ${c.name}") })
                    }
                }
            }
            listOf(
                R.string.catalog_section_stop to listOf(Direction.QUIT, Direction.LIMIT),
                R.string.catalog_section_start to listOf(Direction.START),
            ).forEach { (title, directions) ->
                val templates = catalog.templates.filter { it.direction in directions && (category == null || it.category == category) }
                if (templates.isNotEmpty()) {
                    item { SectionTitle(stringResource(title)) }
                    items(templates, key = { it.id }) { t ->
                        TemplateRow(t, t.id in tracked) { onOpenTemplate(t.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: HabitTemplate, tracked: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiCircle(template.emoji, HabitPalette.color(template.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium)
                Text(template.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tracked) {
                Icon(Icons.Rounded.Check, stringResource(R.string.catalog_tracked), tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@HiltViewModel
class ArticleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    badgeService: BadgeService,
) : ViewModel() {
    private val templateId: String = checkNotNull(savedStateHandle["templateId"])
    val template: HabitTemplate? = catalogRepository.template(templateId)
    val help: List<HelpResource> = catalogRepository.help(template?.help.orEmpty())

    init {
        viewModelScope.launch { badgeService.markArticleRead(templateId) }
    }
}

/** Fiche documentée d'une habitude (F-20, §8.1). */
@Composable
fun ArticleScreen(
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    viewModel: ArticleViewModel = hiltViewModel(),
) {
    val template = viewModel.template ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            val color = HabitPalette.color(template.color)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✨", fontSize = 28.sp)
                            Text(template.emoji, fontSize = 72.sp)
                            Text("🌱", fontSize = 28.sp)
                        }
                    }
                }
            }
            Text(template.summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = { onAdd(template.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_add_this_habit))
            }

            template.article.sections.forEach { section ->
                SectionTitle(section.title)
                section.paragraphs.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                section.bullets.forEach {
                    Row(Modifier.padding(bottom = 6.dp)) {
                        Text("•", color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                section.timeline.forEach { item ->
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            item.time,
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                            modifier = Modifier.width(88.dp),
                        )
                        Text(item.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (viewModel.help.isNotEmpty()) {
                SectionTitle(stringResource(R.string.help_title))
                viewModel.help.forEach { HelpCard(it) }
            }

            if (template.article.sources.isNotEmpty()) {
                SectionTitle(stringResource(R.string.article_sources))
                template.article.sources.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.article_updated, template.article.updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Disclaimer()
        }
    }
}

@Composable
fun Disclaimer() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Text(
            stringResource(R.string.medical_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun HelpCard(resource: HelpResource) {
    val context = LocalContext.current
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(resource.name, style = MaterialTheme.typography.titleMedium)
            Text(resource.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                resource.phone?.let { phone ->
                    FilledTonalButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, ("tel:" + phone.filter { it.isDigit() || it == '+' }).toUri()))
                    }) {
                        Icon(Icons.Rounded.Call, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(phone)
                    }
                }
                resource.url?.let { url ->
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    }) {
                        Icon(Icons.Rounded.Language, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.help_website))
                    }
                }
            }
        }
    }
}

/** Page « Besoin d'aide ? » (F-21). */
@Composable
fun HelpScreen(onBack: () -> Unit, viewModel: DiscoverViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
        ) {
            item {
                Text(stringResource(R.string.help_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
            }
            items(viewModel.catalog.help) { HelpCard(it) }
            item {
                Spacer(Modifier.height(8.dp))
                Disclaimer()
            }
        }
    }
}
