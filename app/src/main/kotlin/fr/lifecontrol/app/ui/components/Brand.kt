package fr.lifecontrol.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.lifecontrol.app.R

/**
 * Logotype de l'application : le nom en gras, suivi d'une puce colorée
 * (clin d'œil au bullet journal). Le nom vient de `app_name`.
 */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 32.sp) {
    val name = stringResource(R.string.app_name)
    val dotColor = MaterialTheme.colorScheme.secondary
    Text(
        buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = dotColor)) { append("•") }
        },
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.02).em,
        maxLines = 1,
    )
}

/** Logotype + accroche (« reprends le contrôle, rien ne quitte ton téléphone »). */
@Composable
fun BrandHeader(modifier: Modifier = Modifier, fontSize: TextUnit = 40.sp) {
    Column(
        modifier.semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BrandWordmark(fontSize = fontSize)
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
