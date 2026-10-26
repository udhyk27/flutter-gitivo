package com.ydh.salvio.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydh.salvio.data.model.CommitWeekActivity
import com.ydh.salvio.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CommitActivityChart(activity: List<CommitWeekActivity>, modifier: Modifier = Modifier) {
    if (activity.isEmpty()) {
        Text("커밋 활동 데이터 없음", color = SalvioTheme.colors.textSecondary, fontSize = 13.sp)
        return
    }

    val recent = activity.takeLast(26)
    val maxTotal = recent.maxOfOrNull { it.total } ?: 1
    val textMeasurer = rememberTextMeasurer()
    val labelColor = SalvioTheme.colors.textSecondary
    val barColor = SalvioTheme.colors.success
    val barColorLight = SalvioTheme.colors.success.copy(alpha = 0.4f)
    val gridColor = SalvioTheme.colors.border

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val paddingBottom = 28.dp.toPx()
        val paddingTop = 8.dp.toPx()
        val chartHeight = size.height - paddingBottom - paddingTop
        val barWidth = (size.width / recent.size) * 0.6f
        val barSpacing = size.width / recent.size

        // 가이드라인
        val guideValues = listOf(0, maxTotal / 2, maxTotal)
        guideValues.forEach { value ->
            val y = paddingTop + chartHeight - (chartHeight * value.toFloat() / maxTotal)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // 바 그리기
        recent.forEachIndexed { index, week ->
            val barHeight = if (maxTotal > 0) chartHeight * week.total.toFloat() / maxTotal else 0f
            val x = index * barSpacing + (barSpacing - barWidth) / 2
            val y = paddingTop + chartHeight - barHeight

            drawRoundRect(
                color = if (week.total > 0) barColor else barColorLight,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // 월 라벨 (4주 간격)
            if (index % 4 == 0) {
                val labelText = try {
                    val instant = Instant.ofEpochSecond(week.week)
                    DateTimeFormatter.ofPattern("M/d").withZone(ZoneId.systemDefault()).format(instant)
                } catch (e: Exception) {
                    ""
                }

                val measured = textMeasurer.measure(
                    labelText,
                    style = TextStyle(color = labelColor, fontSize = 9.sp)
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (x + barWidth / 2 - measured.size.width / 2).coerceIn(0f, size.width - measured.size.width),
                        y = size.height - paddingBottom + 6.dp.toPx()
                    )
                )
            }
        }
    }
}
