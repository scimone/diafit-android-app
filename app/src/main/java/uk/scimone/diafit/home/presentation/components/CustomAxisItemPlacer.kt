package uk.scimone.diafit.home.presentation.components

import com.patrykandpatrick.vico.core.cartesian.CartesianDrawContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasureContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis.Position
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis

class CustomAxisItemPlacer(
    private val targetLower: Double = 70.0,
    private val targetUpper: Double= 180.0,
    private val extraLine: Double = 250.0
) : VerticalAxis.ItemPlacer {

    private val lines = listOf(targetLower, targetUpper, extraLine)

    override fun getShiftTopLines(context: CartesianDrawContext): Boolean = true

    override fun getLabelValues(
        context: CartesianDrawContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Position.Vertical
    ): List<Double> = lines

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasureContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Position.Vertical
    ): List<Double> = lines

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasureContext,
        position: Position.Vertical
    ): List<Double> = lines

    override fun getLineValues(
        context: CartesianDrawContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Position.Vertical
    ): List<Double> = lines

    override fun getTopVerticalAxisInset(
        context: CartesianMeasureContext,
        verticalLabelPosition: VerticalAxis.VerticalLabelPosition,
        maxLabelHeight: Float,
        maxLineThickness: Float
    ): Float = 0f

    override fun getBottomVerticalAxisInset(
        context: CartesianMeasureContext,
        verticalLabelPosition: VerticalAxis.VerticalLabelPosition,
        maxLabelHeight: Float,
        maxLineThickness: Float
    ): Float = 0f
}
