package fr.lifecontrol.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Couleurs des paliers : bronze, argent, or, platine, puis diamant. */
val TierColors = listOf(
    Color(0xFFCD7F32),
    Color(0xFFA8B2BD),
    Color(0xFFE6B422),
    Color(0xFF7FD1C7),
    Color(0xFFB28DFF),
    Color(0xFFFF8FB1),
    Color(0xFF5B4FD6),
)

fun tierColor(level: Int): Color = TierColors[(level - 1).coerceIn(0, TierColors.lastIndex)]

/**
 * Médaille d'un badge : anneau de progression vers le prochain palier, couleur du palier atteint.
 * Verrouillée : silhouette grisée.
 */
@Composable
fun BadgeMedal(
    emoji: String,
    level: Int,
    fraction: Float,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier,
) {
    val unlocked = level > 0
    val ring = if (unlocked) tierColor(level) else MaterialTheme.colorScheme.outlineVariant
    val next = tierColor(level + 1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.07f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (unlocked) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(ring.copy(alpha = 0.35f), ring.copy(alpha = 0.1f))),
                    radius = this.size.minDimension / 2 - stroke,
                )
            } else {
                drawCircle(track, radius = this.size.minDimension / 2 - stroke)
            }
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (unlocked) {
                drawArc(ring, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke * 0.6f))
            }
            if (fraction > 0f && fraction < 1f) {
                drawArc(next, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
        }
        Text(
            emoji,
            fontSize = (size.value * 0.42f).sp,
            modifier = if (unlocked) Modifier else Modifier.alpha(0.35f),
            style = MaterialTheme.typography.bodyLarge.copy(color = if (unlocked) Color.Unspecified else Color.Gray),
        )
    }
}

private data class Particle(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
    val spin: Float,
    val wobble: Float,
)

/** Confettis de célébration (objectif atteint, badge débloqué). */
@Composable
fun Confetti(trigger: Any, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val progress = remember(trigger) { Animatable(0f) }
    val particles = remember(trigger) {
        val palette = listOf(
            Color(0xFF5B4FD6), Color(0xFFE07A3F), Color(0xFF2E9E78), Color(0xFFE6B422), Color(0xFFD94F7A), Color(0xFF3B8ED8),
        )
        List(90) {
            Particle(
                angle = (Random.nextFloat() * 160f + 190f) * (PI / 180f).toFloat(),
                speed = Random.nextFloat() * 0.9f + 0.6f,
                color = palette.random(),
                size = Random.nextFloat() * 10f + 8f,
                spin = Random.nextFloat() * 720f - 360f,
                wobble = Random.nextFloat() * 2f - 1f,
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1800, easing = LinearEasing))
        onFinished()
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val origin = Offset(size.width / 2, size.height * 0.85f)
        val gravity = size.height * 1.6f
        particles.forEach { p ->
            val v = p.speed * size.height * 1.25f
            val x = origin.x + cos(p.angle) * v * t + p.wobble * 40f * sin(t * 12f)
            val y = origin.y + sin(p.angle) * v * t + 0.5f * gravity * t * t
            rotate(p.spin * t, Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    topLeft = Offset(x - p.size / 2, y - p.size / 4),
                    size = Size(p.size, p.size / 2),
                )
            }
        }
    }
}

/** Petite animation d'apparition « pop » pour les médailles. */
@Composable
fun PopIn(key: Any, content: @Composable (Float) -> Unit) {
    val scale = remember(key) { Animatable(0.3f) }
    LaunchedEffect(key) { scale.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    content(scale.value)
}
