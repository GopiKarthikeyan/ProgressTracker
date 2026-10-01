package com.forge.hypertrophy.ui.screens.cardio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.ui.theme.NeonAccent

@Composable
fun RouteCanvas(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        if (points.size < 2) return@Canvas
        val lats = points.map { it.first }
        val lons = points.map { it.second }
        var minLat = lats.min()
        var maxLat = lats.max()
        var minLon = lons.min()
        var maxLon = lons.max()
        if (maxLat - minLat < 0.0001) {
            minLat -= 0.0001
            maxLat += 0.0001
        }
        if (maxLon - minLon < 0.0001) {
            minLon -= 0.0001
            maxLon += 0.0001
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.second - minLon) / (maxLon - minLon)).toFloat() * size.width
            val y = ((maxLat - point.first) / (maxLat - minLat)).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, NeonAccent, style = Stroke(width = 6f))
        val last = points.last()
        val endX = ((last.second - minLon) / (maxLon - minLon)).toFloat() * size.width
        val endY = ((maxLat - last.first) / (maxLat - minLat)).toFloat() * size.height
        drawCircle(NeonAccent, radius = 8f, center = Offset(endX, endY))
    }
}
