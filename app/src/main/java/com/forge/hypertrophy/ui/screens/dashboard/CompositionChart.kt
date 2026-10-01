package com.forge.hypertrophy.ui.screens.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.time.LocalDate

@Composable
fun CompositionChart(
    samples: List<ChartPoint>,
    average: List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(samples, average) {
        producer.runTransaction {
            lineModel {
                series(
                    x = samples.map { it.date.toEpochDay().toDouble() },
                    y = samples.map { it.value },
                )
                if (average.size >= 2) {
                    series(
                        x = average.map { it.date.toEpochDay().toDouble() },
                        y = average.map { it.value },
                    )
                }
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { _, value, _ ->
                    LocalDate.ofEpochDay(value.toLong()).toString()
                },
            ),
        ),
        modelProducer = producer,
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}
