package uk.scimone.diafit.home.presentation.components

import com.patrykandpatrick.vico.core.cartesian.CartesianDrawContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasureContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis.Position
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis

class CustomAxisItemPlacer : VerticalAxis.ItemPlacer {
    override fun getShiftTopLines(context: CartesianDrawContext): Boolean = true

    override fun getLabelValues(
        context: CartesianDrawContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Position.Vertical
    ): List<Double> = listOf(70.0, 180.0)

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasureContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Position.Vertical
    ): List<Double> = listOf(70.0, 180.0)

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasureContext,
        position: Position.Vertical
    ): List<Double> = listOf(70.0, 180.0)

    override fun getLineValues(
        context: CartesianDrawContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Position.Vertical
    ): List<Double> = listOf(70.0, 180.0)

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