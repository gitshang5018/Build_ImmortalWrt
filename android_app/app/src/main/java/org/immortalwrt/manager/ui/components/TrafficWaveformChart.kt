package org.immortalwrt.manager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.immortalwrt.manager.domain.model.RealtimeTraffic
import org.immortalwrt.manager.ui.theme.PrimaryBlue
import org.immortalwrt.manager.ui.theme.SecondaryCyan
import kotlin.math.max

@Composable
fun TrafficWaveformChart(
    rxHistory: List<Long>,
    txHistory: List<Long>,
    currentTraffic: RealtimeTraffic?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "实时流量趋势 (30秒)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(PrimaryBlue, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "下行: ${currentTraffic?.formattedDownloadSpeed ?: "0 B/s"}",
                        fontSize = 11.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(SecondaryCyan, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "上行: ${currentTraffic?.formattedUploadSpeed ?: "0 B/s"}",
                        fontSize = 11.sp,
                        color = SecondaryCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val width = size.width
            val height = size.height

            // 绘制网格背景水平线
            val gridLines = 3
            for (i in 1..gridLines) {
                val y = height * (i.toFloat() / (gridLines + 1))
                drawLine(
                    color = Color.Gray.copy(alpha = 0.15f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }

            val maxSpeed = max(
                (rxHistory.maxOrNull() ?: 1024L),
                (txHistory.maxOrNull() ?: 1024L)
            ).coerceAtLeast(1024L).toFloat()

            // 绘制下行曲线 (PrimaryBlue)
            if (rxHistory.size >= 2) {
                drawWaveform(
                    history = rxHistory,
                    maxVal = maxSpeed,
                    lineColor = PrimaryBlue,
                    fillGradient = Brush.verticalGradient(
                        colors = listOf(PrimaryBlue.copy(alpha = 0.35f), PrimaryBlue.copy(alpha = 0.02f))
                    )
                )
            }

            // 绘制上行曲线 (SecondaryCyan)
            if (txHistory.size >= 2) {
                drawWaveform(
                    history = txHistory,
                    maxVal = maxSpeed,
                    lineColor = SecondaryCyan,
                    fillGradient = Brush.verticalGradient(
                        colors = listOf(SecondaryCyan.copy(alpha = 0.3f), SecondaryCyan.copy(alpha = 0.01f))
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawWaveform(
    history: List<Long>,
    maxVal: Float,
    lineColor: Color,
    fillGradient: Brush
) {
    val count = history.size
    val stepX = size.width / (count - 1).coerceAtLeast(1)
    val path = Path()
    val fillPath = Path()

    val points = history.mapIndexed { index, value ->
        val x = index * stepX
        val normalized = (value.toFloat() / maxVal).coerceIn(0f, 1f)
        val y = size.height - (normalized * (size.height - 10f)) - 5f
        Offset(x, y)
    }

    if (points.isNotEmpty()) {
        path.moveTo(points[0].x, points[0].y)
        fillPath.moveTo(points[0].x, size.height)
        fillPath.lineTo(points[0].x, points[0].y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val controlX1 = (p0.x + p1.x) / 2f
            val controlY1 = p0.y
            val controlX2 = (p0.x + p1.x) / 2f
            val controlY2 = p1.y

            path.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
        }

        fillPath.lineTo(points.last().x, size.height)
        fillPath.close()

        drawPath(path = fillPath, brush = fillGradient)
        drawPath(path = path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
