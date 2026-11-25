package fr.lifecontrol.app.ui.cards

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.lifecontrol.app.R
import fr.lifecontrol.app.data.content.CardRarity
import fr.lifecontrol.app.data.db.CardEntity
import fr.lifecontrol.app.data.repository.CardService
import fr.lifecontrol.app.ui.components.BrandWordmark
import fr.lifecontrol.app.ui.components.Confetti
import fr.lifecontrol.app.ui.util.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class CardsViewModel @Inject constructor(private val cardService: CardService) : ViewModel() {
    val cards = cardService.cards
    val generating = cardService.generating
    fun retry(id: String) = cardService.retry(id)
}

/** Chargement des images de cartes (fourni par MainActivity, branché sur CardService). */
val LocalCardImageLoader = staticCompositionLocalOf<suspend (CardEntity, Int) -> Bitmap?> { { _, _ -> null } }

private val RarityColors = mapOf(
    CardRarity.COMMON to Color(0xFF2E9E78),
    CardRarity.RARE to Color(0xFF3B8ED8),
    CardRarity.LEGENDARY to Color(0xFFE6A417),
)

val CardEntity.rarityValue: CardRarity get() = CardRarity.parse(rarity)

fun rarityColor(rarity: CardRarity): Color = RarityColors.getValue(rarity)

@Composable
fun rarityLabel(rarity: CardRarity): String = stringResource(
    when (rarity) {
        CardRarity.COMMON -> R.string.rarity_common
        CardRarity.RARE -> R.string.rarity_rare
        CardRarity.LEGENDARY -> R.string.rarity_legendary
    },
)

@Composable
private fun cardOrigin(card: CardEntity): String =
    if (card.successRate < 0) stringResource(R.string.card_debug)
    else stringResource(R.string.card_day_rate, (card.successRate * 100).roundToInt())

@Composable
private fun CardImage(card: CardEntity, modifier: Modifier, maxWidth: Dp) {
    val px = with(LocalDensity.current) { maxWidth.roundToPx() }
    val loader = LocalCardImageLoader.current
    val image by produceState<ImageBitmap?>(null, card.id, card.isReady, px) {
        value = if (card.isReady) loader(card, px)?.asImageBitmap() else null
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        image?.let { Image(it, contentDescription = card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Contenu de l'onglet « Cartes » (écran des badges). */
@Composable
fun CardsCollection(
    cards: List<CardEntity>,
    generating: Set<String>,
    onRetry: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<CardEntity?>(null) }
    val ready = cards.filter { it.isReady }
    val pending = cards.filter { !it.isReady }
    val themes = ready.mapNotNull { it.themeLabel }.distinct().sorted()
    val shown = ready.filter { theme == null || it.themeLabel == theme }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.cards_count, ready.size, ready.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    CardRarity.entries.reversed().forEach { r ->
                        val n = ready.count { it.rarityValue == r }
                        if (n > 0) {
                            Text(
                                "● $n",
                                color = rarityColor(r),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.cards_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (themes.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = theme == null, onClick = { theme = null }, label = { Text(stringResource(R.string.theme_all)) })
                        themes.forEach { t ->
                            FilterChip(selected = theme == t, onClick = { theme = t }, label = { Text(t) })
                        }
                    }
                }
            }
        }
        if (cards.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.cards_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        if (theme == null) {
            items(pending, key = { it.id }) { card ->
                PendingCardTile(card, generating = card.id in generating, onRetry = { onRetry(card.id) })
            }
        }
        items(shown, key = { it.id }) { card ->
            CardTile(card, Modifier.clickable { selected = card })
        }
    }

    selected?.let { CardDetailDialog(it) { selected = null } }
}

private val TileShape = RoundedCornerShape(14.dp)

@Composable
fun CardTile(card: CardEntity, modifier: Modifier = Modifier) {
    CardFrame(card.rarityValue, modifier.aspectRatio(0.72f), outer = 14.dp, thickness = 5.dp) {
        CardImage(card, Modifier.fillMaxWidth().weight(1f), maxWidth = 160.dp)
        Text(
            card.title.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

/**
 * Cadre de carte à collectionner : large bordure arrondie aux couleurs de la rareté (léger dégradé),
 * contenu inséré dans un second arrondi, comme une vraie carte.
 */
@Composable
private fun CardFrame(
    rarity: CardRarity,
    modifier: Modifier,
    outer: Dp,
    thickness: Dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val color = rarityColor(rarity)
    val outerShape = RoundedCornerShape(outer)
    val innerShape = RoundedCornerShape(outer - thickness / 2)
    Box(
        modifier
            .clip(outerShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f), color)))
            .padding(thickness),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(innerShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            content = content,
        )
    }
}

/** Carte gagnée mais pas encore tirée (hors connexion) : un appui relance le tirage. */
@Composable
private fun PendingCardTile(card: CardEntity, generating: Boolean, onRetry: () -> Unit) {
    val color = rarityColor(card.rarityValue)
    Box(
        Modifier
            .aspectRatio(0.72f)
            .clip(TileShape)
            .border(2.dp, color.copy(alpha = 0.6f), TileShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), color.copy(alpha = 0.25f))))
            .clickable(enabled = !generating, onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            if (generating) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            } else {
                Text("?", fontSize = 34.sp, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(Modifier.height(6.dp))
            Text(rarityLabel(card.rarityValue), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            Text(
                if (generating) stringResource(R.string.card_drawing) else stringResource(R.string.card_pending_tile),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Dos de carte, aux couleurs de l'application, liseré à la couleur de la rareté. */
@Composable
private fun CardBack(rarity: CardRarity, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(primary, rarityColor(rarity))))
            .border(3.dp, Color.White.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(50)) {
            BrandWordmark(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 26.sp)
        }
    }
}

/** Recto : image, rareté, titre et accroche. */
/** « Rareté · thème », dans la couleur de la rareté. */
@Composable
private fun CardHeader(card: CardEntity, style: TextStyle) {
    Text(
        listOfNotNull(rarityLabel(card.rarityValue), card.themeLabel).joinToString(" · "),
        style = style,
        color = rarityColor(card.rarityValue),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CardFace(card: CardEntity, modifier: Modifier = Modifier) {
    val color = rarityColor(card.rarityValue)
    CardFrame(card.rarityValue, modifier, outer = 22.dp, thickness = 10.dp) {
        CardImage(card, Modifier.fillMaxWidth().weight(1f), maxWidth = 320.dp)
        Column(
            Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f)).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardHeader(card, MaterialTheme.typography.labelMedium)
            Text(card.title.orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!card.summary.isNullOrBlank()) {
                Text(
                    card.summary.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Révélation de la carte du jour, en direct : le dos s'agite pendant le tirage sur Wikipédia,
 * puis la carte se retourne d'elle-même dès qu'elle est prête. Hors connexion, elle reste « à tirer ».
 */
@Composable
fun CardRevealDialog(
    card: CardEntity,
    generating: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDetail by remember(card.id) { mutableStateOf(false) }
    if (showDetail) {
        CardDetailDialog(card, onDismiss = onDismiss)
        return
    }
    val rarity = card.rarityValue
    val rotation = remember(card.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val flip: () -> Unit = {
        if (card.isReady && rotation.targetValue < 180f) scope.launch { rotation.animateTo(180f, tween(800, easing = FastOutSlowInEasing)) }
    }
    // Un court suspense, même si la carte est déjà prête.
    LaunchedEffect(card.id, card.isReady) {
        if (card.isReady) {
            delay(700)
            flip()
        }
    }
    val drawing = !card.isReady && generating
    // Le dos s'agite tant que le tirage est en cours (animation infinie seulement pendant ce temps).
    val wobble = if (drawing) {
        rememberInfiniteTransition(label = "draw").animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(260), RepeatMode.Reverse),
            label = "wobble",
        ).value
    } else 0f
    val revealed = rotation.value >= 90f
    val density = LocalDensity.current.density

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (card.source == "debug") stringResource(R.string.card_debug) else stringResource(R.string.card_of_the_day),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    val origin = if (card.successRate >= 0) cardOrigin(card) + " → " else ""
                    Text(
                        origin + rarityLabel(rarity),
                        style = MaterialTheme.typography.labelLarge,
                        color = rarityColor(rarity),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .width(260.dp)
                    .aspectRatio(0.68f)
                    .graphicsLayer {
                        rotationY = rotation.value
                        rotationZ = if (drawing) wobble else 0f
                        cameraDistance = 14f * density
                    }
                    .clickable(onClick = flip),
            ) {
                if (revealed) {
                    // Contre-rotation : le recto ne doit pas apparaître en miroir.
                    CardFace(card, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
                } else {
                    CardBack(rarity, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                revealed -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.card_later)) }
                    }
                    Button(onClick = { showDetail = true }) { Text(stringResource(R.string.card_read_more)) }
                }
                card.isReady -> Text(stringResource(R.string.card_tap_to_flip), style = MaterialTheme.typography.bodySmall, color = Color.White)
                drawing -> Text(stringResource(R.string.card_drawing), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                else -> Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.card_offline), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.card_later)) }
                            Button(onClick = onRetry) { Text(stringResource(R.string.card_retry)) }
                        }
                    }
                }
            }
        }
        if (revealed && rarity != CardRarity.COMMON) Confetti(trigger = card.id)
    }
}

/** Fiche complète d'une carte : de quoi apprendre quelque chose, avec ses crédits. */
@Composable
fun CardDetailDialog(card: CardEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(Modifier.heightIn(max = 720.dp).verticalScroll(rememberScrollState())) {
                CardImage(card, Modifier.fillMaxWidth().height(240.dp), maxWidth = 400.dp)
                Column(Modifier.padding(20.dp)) {
                    CardHeader(card, MaterialTheme.typography.labelLarge)
                    Text(card.title.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    if (!card.summary.isNullOrBlank()) {
                        Text(
                            card.summary.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(card.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    val small = MaterialTheme.typography.bodySmall
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        stringResource(R.string.card_of_day, Fmt.dayShort(LocalDate.parse(card.day))) + " · " + cardOrigin(card),
                        style = small,
                        color = muted,
                    )
                    card.views?.takeIf { it > 0 }?.let {
                        Text(stringResource(R.string.card_views, NumberFormat.getIntegerInstance(Locale.FRANCE).format(it)), style = small, color = muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.card_credits_image, card.imageArtist.orEmpty(), card.imageLicense.orEmpty()),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(stringResource(R.string.card_credits_text), style = MaterialTheme.typography.labelSmall, color = muted)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        card.url?.let { url ->
                            TextButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: ActivityNotFoundException) {
                                }
                            }) { Text(stringResource(R.string.card_open_wikipedia)) }
                        }
                        Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                    }
                }
            }
        }
    }
}
