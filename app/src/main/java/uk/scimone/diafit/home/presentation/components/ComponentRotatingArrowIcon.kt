package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp


@Composable
fun ComponentRotatingArrowIcon(inputValue: Float?) {
    if (inputValue != null) {
        val rotationAngle = (-inputValue * 180f) // Scale inputValue to [0, 180] range
        val color = MaterialTheme.colorScheme.onSurface

        Canvas(
            modifier = Modifier
                .size(50.dp)
                .rotate(rotationAngle)
        ) {
            drawArrow(color = color)
        }
    }
}

fun DrawScope.drawArrow(color: Color) {
    val center = size.minDimension / 2f
    val radius = center - 10.dp.toPx()
    val arrowStrokeWidth = 2.5.dp.toPx()

    // Draw outlined circle
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(center, center),
        style = Stroke(width = arrowStrokeWidth)
    )

    // Draw arrow line
    val arrowLength = radius * 1.1f

    val arrowStartX = center
    val arrowEndX = center

    val arrowStartY = center + arrowLength / 2
    val arrowEndY = center - arrowLength / 2

    drawLine(
        color = color,
        start = Offset(arrowStartX, arrowStartY - radius * 0.25f),
        end = Offset(arrowEndX, arrowEndY),
        strokeWidth = arrowStrokeWidth
    )

    // Draw arrowhead (triangle)
    drawArrowhead(Offset(arrowStartX, arrowStartY), color, radius)
}

fun DrawScope.drawArrowhead(position: Offset, color: Color, radius: Float) {
    val triangleHeight = radius * 0.35f
    val triangleBaseHalf = radius * 0.5f

    val path = Path().apply {
        moveTo(position.x, position.y) // Tip of the arrowhead
        lineTo(
            position.x + triangleHeight,
            position.y - triangleBaseHalf
        ) // Bottom right of the arrowhead
        lineTo(
            position.x - triangleHeight,
            position.y - triangleBaseHalf
        ) // Bottom left of the arrowhead
        close()
    }

    drawIntoCanvas { canvas ->
        canvas.drawOutline(
            outline = Outline.Generic(path),
            paint = Paint().apply {
                this.color = color
                pathEffect = PathEffect.cornerPathEffect(radius * 0.1f) // Use a fraction of the radius for corner rounding
            }
        )
    }
}
