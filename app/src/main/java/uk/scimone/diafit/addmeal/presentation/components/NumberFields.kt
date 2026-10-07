package uk.scimone.diafit.addmeal.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A bare digits-only field filling its row: the value in [style] with its [unit] right after it, a muted "0" while empty. */
@Composable
fun InlineNumberField(
    value: Int?,
    onValueChange: (String) -> Unit,
    unit: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unitStyle = SpanStyle(color = muted, fontSize = MaterialTheme.typography.labelLarge.fontSize, fontWeight = FontWeight.Normal)
    BasicTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(5)) },
        singleLine = true,
        textStyle = style.copy(color = color, fontWeight = FontWeight.Bold, textAlign = align),
        cursorBrush = SolidColor(color),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        // The unit is drawn as part of the text so it always sits right next to the number.
        visualTransformation = { text ->
            val shown = buildAnnotatedString {
                if (text.isEmpty()) withStyle(SpanStyle(color = muted.copy(alpha = 0.4f))) { append("0") } else append(text)
                withStyle(unitStyle) { append(" $unit") }
            }
            val len = text.length
            TransformedText(shown, object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset
                override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(len)
            })
        },
        modifier = modifier.fillMaxWidth()
    )
}

/** A small tile with a label on top and an inline number below. */
@Composable
fun MacroTile(
    label: String,
    value: Int?,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.titleMedium
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            InlineNumberField(value = value, onValueChange = onValueChange, unit = unit, style = style, color = color)
        }
    }
}
