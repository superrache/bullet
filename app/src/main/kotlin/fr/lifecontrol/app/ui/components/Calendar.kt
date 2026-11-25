package fr.lifecontrol.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lifecontrol.app.R
import fr.lifecontrol.app.ui.theme.LocalStatusColors
import fr.lifecontrol.app.ui.util.Fmt
import fr.lifecontrol.domain.eval.DayAggregate
import fr.lifecontrol.domain.model.DayStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@Composable
fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, canGoNext: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.cd_previous))
        }
        Text(
            Fmt.month(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.cd_next))
        }
    }
}

/** Grille d'un mois (F-12). Le contenu de chaque case est fourni par l'appelant. */
@Composable
fun MonthGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    dayContent: @Composable (LocalDate, Modifier) -> Unit,
) {
    val weekDays = Fmt.orderedWeek(firstDayOfWeek)
    val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDayOfWeek.minus(1)))
    val weeks = generateSequence(gridStart) { it.plusWeeks(1) }.takeWhile { !it.isAfter(gridEnd) }.toList()

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach {
                Text(
                    Fmt.weekdayNarrow(it),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        weeks.forEach { weekStart ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0L until 7L).forEach { offset ->
                    val date = weekStart.plusDays(offset)
                    val cellModifier = Modifier.weight(1f).aspectRatio(1f)
                    if (YearMonth.from(date) == month) {
                        dayContent(date, cellModifier)
                    } else {
                        Spacer(cellModifier)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Case de calendrier pour une habitude : couleur du statut + symbole + valeur éventuelle. */
@Composable
fun StatusDayCell(
    date: LocalDate,
    status: DayStatus,
    valueText: String?,
    isToday: Boolean,
    statusDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalStatusColors.current
    val bg = when (status) {
        DayStatus.SUCCESS -> colors.success
        DayStatus.FAILURE -> colors.failure
        DayStatus.MISSING -> colors.missing
        else -> Color.Transparent
    }
    val fg = when (status) {
        DayStatus.SUCCESS -> colors.onSuccess
        DayStatus.FAILURE -> colors.onFailure
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .then(if (status == DayStatus.PENDING) Modifier.border(2.dp, colors.pending, RoundedCornerShape(10.dp)) else Modifier)
            .then(if (onClick != null && status != DayStatus.FUTURE) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "${Fmt.dayLong(date)} : $statusDescription" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (status == DayStatus.FUTURE || status == DayStatus.NEUTRAL) fg.copy(alpha = 0.45f) else fg,
            )
            val mark = when (status) {
                DayStatus.SUCCESS -> "✓"
                DayStatus.FAILURE -> "✕"
                else -> null
            }
            val sub = valueText ?: mark
            if (sub != null) Text(sub, fontSize = 9.sp, color = fg, maxLines = 1)
        }
    }
}

/** Case de calendrier « toutes habitudes » : fond proportionnel au taux + pastilles. */
@Composable
fun AggregateDayCell(
    date: LocalDate,
    statuses: List<Pair<Color, DayStatus>>,
    isToday: Boolean,
    isFuture: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalStatusColors.current
    val evaluated = statuses.filter { it.second in setOf(DayStatus.SUCCESS, DayStatus.FAILURE, DayStatus.MISSING) }
    val ratio = if (evaluated.isEmpty()) null else evaluated.count { it.second == DayStatus.SUCCESS }.toFloat() / evaluated.size
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ratio?.let { colors.success.copy(alpha = 0.12f + 0.6f * it) } ?: Color.Transparent)
            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .then(if (onClick != null && !isFuture) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), fontSize = 13.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                statuses.filter { it.second != DayStatus.NEUTRAL && it.second != DayStatus.FUTURE }.take(5).forEach { (_, status) ->
                    Canvas(Modifier.size(5.dp)) {
                        when (status) {
                            DayStatus.SUCCESS -> drawCircle(colors.success)
                            DayStatus.FAILURE -> drawCircle(colors.failure)
                            DayStatus.PENDING -> drawCircle(colors.pending, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                            else -> drawCircle(colors.missing)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Carte de chaleur façon « contributions » (F-14) : une colonne par semaine, une case par jour.
 * [cellColor] renvoie la couleur d'un jour.
 */
@Composable
fun Heatmap(
    from: LocalDate,
    to: LocalDate,
    firstDayOfWeek: DayOfWeek,
    cellColor: (LocalDate) -> Color,
    modifier: Modifier = Modifier,
    cellOutlined: (LocalDate) -> Boolean = { false },
) {
    val start = from.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val weeks = generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(to) }.toList()
    val scroll = rememberScrollState()
    LaunchedEffect(weeks.size) { scroll.scrollTo(scroll.maxValue) }
    val cell = if (weeks.size <= 6) 22.dp else 13.dp
    val gap = 3.dp
    Row(modifier.horizontalScroll(scroll)) {
        Column(Modifier.padding(end = 4.dp)) {
            Fmt.orderedWeek(firstDayOfWeek).forEachIndexed { i, d ->
                Box(Modifier.height(cell + gap), contentAlignment = Alignment.CenterStart) {
                    if (i % 2 == 0) Text(Fmt.weekdayNarrow(d), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Canvas(Modifier.width((cell + gap) * weeks.size).height((cell + gap) * 7)) {
            val c = cell.toPx()
            val g = gap.toPx()
            weeks.forEachIndexed { w, weekStart ->
                for (d in 0 until 7) {
                    val date = weekStart.plusDays(d.toLong())
                    if (date.isBefore(from) || date.isAfter(to)) continue
                    if (cellOutlined(date)) {
                        // Case « creuse » : distinguée par la forme, pas seulement par la couleur (§9).
                        val sw = (c * 0.16f).coerceAtLeast(1.5f)
                        drawRoundRect(
                            color = cellColor(date),
                            topLeft = Offset(w * (c + g) + sw / 2, d * (c + g) + sw / 2),
                            size = Size(c - sw, c - sw),
                            cornerRadius = CornerRadius(c * 0.25f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(sw),
                        )
                    } else {
                        drawRoundRect(
                            color = cellColor(date),
                            topLeft = Offset(w * (c + g), d * (c + g)),
                            size = Size(c, c),
                            cornerRadius = CornerRadius(c * 0.25f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vue pixel d'une habitude, dans sa couleur : case pleine = réussi, case creuse = écart,
 * case neutre = rien à évaluer ce jour-là.
 */
@Composable
fun HabitPixels(
    from: LocalDate,
    to: LocalDate,
    firstDayOfWeek: DayOfWeek,
    habitColor: Color,
    status: (LocalDate) -> DayStatus,
    modifier: Modifier = Modifier,
) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Heatmap(
        from, to, firstDayOfWeek,
        cellColor = { date ->
            when (status(date)) {
                DayStatus.SUCCESS, DayStatus.FAILURE -> habitColor
                else -> empty
            }
        },
        cellOutlined = { status(it) == DayStatus.FAILURE },
        modifier = modifier,
    )
}

@Composable
fun HabitPixelsLegend(color: Color) {
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
        Text(stringResource(R.string.legend_pixels_success), fontSize = 11.sp, color = label)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(11.dp).border(2.dp, color, RoundedCornerShape(3.dp)))
        Text(stringResource(R.string.legend_pixels_failure), fontSize = 11.sp, color = label)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(11.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp)))
        Text(stringResource(R.string.legend_pixels_none), fontSize = 11.sp, color = label)
    }
}

/** Couleur de carte de chaleur pour un agrégat journalier. */
@Composable
fun aggregateColorFn(aggregates: Map<LocalDate, DayAggregate>): (LocalDate) -> Color {
    val colors = LocalStatusColors.current
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    return { date ->
        val ratio = aggregates[date]?.ratio
        when {
            ratio == null -> empty
            ratio == 0.0 -> colors.failure.copy(alpha = 0.55f)
            else -> colors.success.copy(alpha = (0.25f + 0.75f * ratio.toFloat()))
        }
    }
}

@Composable
fun HeatmapLegend() {
    val colors = LocalStatusColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.legend_less), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(colors.failure.copy(alpha = 0.55f), colors.success.copy(alpha = 0.35f), colors.success.copy(alpha = 0.65f), colors.success)
            .forEach { Box(Modifier.size(11.dp).background(it, RoundedCornerShape(3.dp))) }
        Text(stringResource(R.string.legend_more), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Histogramme simple (évolution sur la période). */
@Composable
fun BarChart(
    bars: List<Pair<String, Float>>,
    color: Color,
    modifier: Modifier = Modifier,
    reference: Float? = null,
    fixedMax: Float? = null,
) {
    val max = fixedMax ?: maxOf(bars.maxOfOrNull { it.second } ?: 0f, reference ?: 0f).takeIf { it > 0f } ?: 1f
    val refColor = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Text(
            "max " + fr.lifecontrol.app.ui.util.Fmt.number(max.toDouble()),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = bars.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = (slot * 0.7f).coerceAtMost(28.dp.toPx())
            bars.forEachIndexed { i, (_, v) ->
                val h = size.height * (v / max)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            reference?.let {
                val y = size.height * (1 - it / max)
                drawLine(refColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        if (bars.size <= 14) {
            Row(Modifier.fillMaxWidth()) {
                bars.forEach { (label, _) ->
                    Text(
                        label, fontSize = 9.sp, maxLines = 1, modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                Text(bars.first().first, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(bars.last().first, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Barres horizontales libellées (déclencheurs, jours de la semaine). */
@Composable
fun HorizontalBars(items: List<Pair<String, Int>>, color: Color) {
    val max = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(value.toFloat() / max)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(4.dp)),
                    )
                }
                Text(value.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp).width(24.dp))
            }
        }
    }
}
