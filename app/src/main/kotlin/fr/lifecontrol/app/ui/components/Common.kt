package fr.lifecontrol.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lifecontrol.app.ui.theme.LocalStatusColors
import fr.lifecontrol.domain.model.DayStatus

/** Pastille ronde colorée contenant un emoji (icône d'habitude). */
@Composable
fun EmojiCircle(emoji: String, color: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content(Modifier.weight(1f))
    }
}

@Composable
fun RoundedProgress(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = color,
        trackColor = color.copy(alpha = 0.15f),
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
    )
}

fun statusColor(status: DayStatus, colors: fr.lifecontrol.app.ui.theme.StatusColors): Color = when (status) {
    DayStatus.SUCCESS -> colors.success
    DayStatus.FAILURE -> colors.failure
    DayStatus.MISSING -> colors.missing
    DayStatus.PENDING -> colors.pending
    DayStatus.NEUTRAL, DayStatus.FUTURE -> colors.neutral
}

/** Pastille de statut : la couleur est toujours doublée d'un symbole (accessibilité). */
@Composable
fun StatusDot(status: DayStatus, size: Dp = 18.dp) {
    val colors = LocalStatusColors.current
    val color = statusColor(status, colors)
    Box(
        Modifier
            .size(size)
            .then(
                if (status == DayStatus.PENDING) Modifier.border(2.dp, color, CircleShape)
                else Modifier.background(color, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when (status) {
            DayStatus.SUCCESS -> Icons.Rounded.Check
            DayStatus.FAILURE -> Icons.Rounded.Close
            DayStatus.MISSING -> Icons.Rounded.Remove
            else -> null
        }
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (status == DayStatus.MISSING) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        action()
    }
}

val CardShape = RoundedCornerShape(20.dp)
