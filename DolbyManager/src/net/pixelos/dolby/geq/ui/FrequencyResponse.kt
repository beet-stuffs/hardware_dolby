/*
 * Copyright (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.geq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.pixelos.dolby.R
import com.android.settingslib.spa.framework.theme.SettingsSpace

private const val MaxDb = 10f

/**
 * Smooth curve through the band gains, as a preview of the frequency response. Band i is drawn
 * at the center of the i-th of [gainsDb] equal columns, so it lines up with [EqualizerBands].
 * [gainsDb] is read while drawing, so dragging a slider only redraws the curve.
 */
@Composable
fun FrequencyResponse(
    gainsDb: () -> List<Float>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.16f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.dolby_geq_frequency_response)
    val maxLabel = stringResource(R.string.dolby_geq_db, "+10")
    val minLabel = stringResource(R.string.dolby_geq_db, "-10")

    Column(modifier = modifier.fillMaxWidth()) {
        AxisLabel(maxLabel)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(vertical = SettingsSpace.extraSmall4)
                .semantics { contentDescription = description },
        ) {
            val gains = gainsDb()
            if (gains.isEmpty()) return@Canvas
            val width = size.width
            val height = size.height
            val middle = height / 2f
            fun y(db: Float) = middle - db.coerceIn(-MaxDb, MaxDb) / MaxDb * middle

            // Grid: 0 dB, and dashed lines at +-10 dB.
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawLine(gridColor, Offset(0f, 0f), Offset(width, 0f), 1.dp.toPx(), pathEffect = dash)
            drawLine(gridColor, Offset(0f, height), Offset(width, height), 1.dp.toPx(), pathEffect = dash)
            drawLine(gridColor, Offset(0f, middle), Offset(width, middle), 1.dp.toPx())

            val step = width / gains.size
            val points = gains.mapIndexed { i, db -> Offset(step * (i + 0.5f), y(db)) }

            // Catmull-Rom spline through the points, as cubic Beziers, flat towards the edges.
            fun Path.addCurve() {
                lineTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[maxOf(i - 1, 0)]
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = points[minOf(i + 2, points.size - 1)]
                    cubicTo(
                        p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                        p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                        p2.x, p2.y,
                    )
                }
                lineTo(width, points.last().y)
            }
            val curve = Path().apply {
                moveTo(0f, points.first().y)
                addCurve()
            }
            val area = Path().apply {
                moveTo(0f, middle)
                lineTo(0f, points.first().y)
                addCurve()
                lineTo(width, middle)
                close()
            }
            drawPath(area, fillColor)
            drawPath(
                curve,
                lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            points.forEach { drawCircle(lineColor, radius = 3.dp.toPx(), center = it) }
        }
        AxisLabel(minLabel)
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
}
