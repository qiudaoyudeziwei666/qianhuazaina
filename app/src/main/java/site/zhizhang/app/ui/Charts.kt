package site.zhizhang.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** 轻量图表：Canvas 自绘，零第三方依赖，适配任意手机宽度，全部支持点击联动 */
object ChartPalette {
    val colors = listOf(
        Color(0xFF2D765E), Color(0xFF8C78C3), Color(0xFFE0A45F), Color(0xFF6FAFBB),
        Color(0xFF8795C7), Color(0xFFDE908B), Color(0xFFADB473), Color(0xFFB4A6DC),
        Color(0xFF88A895), Color(0xFFC3CCC4)
    )
    fun forIndex(i: Int) = colors[i % colors.size]
}

/** 迷你趋势（首页用）：折线 + 填充面积，无坐标轴 */
@Composable
fun MiniTrendChart(values: List<Long>, modifier: Modifier = Modifier) {
    val green = Green
    val line = Line
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        val w = size.width; val h = size.height
        if (values.isEmpty()) {
            drawLine(line, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f)
            return@Canvas
        }
        val maxV = max(values.maxOrNull() ?: 1L, 1L).toFloat()
        val pts = values.mapIndexed { i, v ->
            val x = if (values.size == 1) w / 2 else w * i / (values.size - 1)
            Offset(x, h - (v.toFloat() / maxV) * (h - 8f) - 4f)
        }
        val path = Path().apply {
            moveTo(pts.first().x, h)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, h)
            close()
        }
        drawPath(path, green.copy(alpha = 0.10f))
        for (i in 1 until pts.size) {
            drawLine(green, pts[i - 1], pts[i], strokeWidth = 3f)
        }
        drawCircle(green, 4f, pts.last())
    }
}

/** 月度收入 / 支出对比柱状图 */
@Composable
fun GroupedBarChart(
    labels: List<String>,
    incomes: List<Long>,
    expenses: List<Long>,
    onSelect: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val incomeColor = Color(0xFF6FAFBB)
    val expenseColor = Green
    val grid = Line
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp).pointerInput(labels) {
        detectTapGestures { offset ->
            if (labels.isNotEmpty()) onSelect((offset.x / (size.width / labels.size)).toInt().coerceIn(0, labels.size - 1))
        }
    }) {
        val n = labels.size.coerceAtLeast(1)
        val slot = size.width / n
        val maxV = max((incomes + expenses).maxOrNull() ?: 1L, 1L).toFloat()
        val baseY = size.height - 18f
        drawLine(grid, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.5f)
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val barW = (slot * 0.26f).coerceAtMost(22f)
            val ih = ((incomes.getOrNull(i) ?: 0L).toFloat() / maxV) * (baseY - 10f)
            val eh = ((expenses.getOrNull(i) ?: 0L).toFloat() / maxV) * (baseY - 10f)
            drawRect(incomeColor, Offset(cx - barW - 2f, baseY - ih), Size(barW, ih))
            drawRect(expenseColor, Offset(cx + 2f, baseY - eh), Size(barW, eh))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
    }
}

/** 消费趋势折线图 */
@Composable
fun TrendLineChart(
    values: List<Long>,
    labels: List<String>,
    onSelect: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val green = Green
    val grid = Line
    val surface = AppSurface
    Canvas(modifier = modifier.fillMaxWidth().height(150.dp).pointerInput(values) {
        detectTapGestures { offset ->
            if (values.isNotEmpty()) onSelect((offset.x / (size.width / values.size)).toInt().coerceIn(0, values.size - 1))
        }
    }) {
        val h = size.height; val w = size.width
        val maxV = max(values.maxOrNull() ?: 1L, 1L).toFloat()
        val pts = values.mapIndexed { i, v ->
            val x = if (values.size == 1) w / 2 else w * i / (values.size - 1)
            Offset(x, h - 24f - (v.toFloat() / maxV) * (h - 40f))
        }
        drawLine(grid, Offset(0f, h - 24f), Offset(w, h - 24f), strokeWidth = 1.5f)
        for (i in 1 until pts.size) drawLine(green, pts[i - 1], pts[i], strokeWidth = 3.5f)
        pts.forEach { drawCircle(surface, 5f, it); drawCircle(green, 4f, it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
    }
}

data class DonutSlice(val label: String, val value: Long, val color: Color)

/** 分类占比环形图，点击扇区回调索引 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTitle: String,
    centerValue: String,
    onSelect: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = Line
    Canvas(modifier = modifier.height(190.dp).fillMaxWidth().pointerInput(slices) {
        detectTapGestures { offset ->
            val cx = size.width / 2; val cy = size.height / 2
            val dx = offset.x - cx; val dy = offset.y - cy
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            val outer = minOf(cx, cy) * 0.92f
            val inner = outer * 0.62f
            if (r !in inner..outer) return@detectTapGestures
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
            if (deg < 0) deg += 360f
            val total = slices.sumOf { it.value }.toFloat()
            if (total <= 0f) return@detectTapGestures
            var acc = 0f
            slices.forEachIndexed { i, s ->
                val sweep = s.value / total * 360f
                if (deg >= acc && deg < acc + sweep) { onSelect(i); return@detectTapGestures }
                acc += sweep
            }
        }
    }) {
        val total = slices.sumOf { it.value }.toFloat()
        if (total <= 0f) {
            drawArc(track, 0f, 360f, false, size = Size(size.width, size.width))
            return@Canvas
        }
        val cx = size.width / 2; val cy = size.height / 2
        val r = minOf(cx, cy) * 0.92f
        val topLeft = Offset(cx - r, cy - r)
        val diameter = r * 2
        var start = -90f
        slices.forEach { s ->
            val sweep = s.value / total * 360f
            drawArc(s.color, start, sweep - 1.5f, false,
                topLeft = topLeft, size = Size(diameter, diameter),
                style = Stroke(width = r * 0.34f))
            start += sweep
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(centerTitle, style = MaterialTheme.typography.bodySmall)
        Text(centerValue, style = MaterialTheme.typography.titleLarge)
    }
}

/** 每日消费日历热力图（按天格子，颜色深浅表示金额） */
@Composable
fun CalendarHeatMap(
    daily: Map<Int, Long>,
    daysInMonth: Int,
    onSelect: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val maxV = max(daily.values.maxOrNull() ?: 1L, 1L).toFloat()
    val base = Green
    val empty = Line
    Column(modifier.fillMaxWidth()) {
        val weeks = (daysInMonth + 6) / 7
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0 until 7) {
                    val day = w * 7 + d + 1
                    val amount = daily[day] ?: 0L
                    val alpha = if (amount > 0) 0.20f + 0.75f * (amount / maxV) else 0f
                    Canvas(
                        Modifier.weight(1f).height(26.dp).pointerInput(day) {
                            detectTapGestures { if (day <= daysInMonth) onSelect(day) }
                        }
                    ) {
                        drawRect(if (alpha > 0f) base.copy(alpha = alpha) else empty.copy(alpha = 0.6f),
                            size = Size(size.width, size.height))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** 商户 / 分类排行条 */
@Composable
fun RankBars(items: List<Pair<String, Long>>, onSelect: (Int) -> Unit = {}) {
    val total = max(items.sumOf { it.second }, 1L).toFloat()
    val track = Line
    Column(Modifier.fillMaxWidth().pointerInput(items) {
        detectTapGestures { offset ->
            val rowH = size.height / items.size.coerceAtLeast(1)
            onSelect((offset.y / rowH).toInt().coerceIn(0, items.size - 1))
        }
    }) {
        items.forEachIndexed { i, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.42f), maxLines = 1)
                Canvas(Modifier.weight(0.38f).height(10.dp)) {
                    drawRect(track.copy(alpha = 0.7f), size = Size(size.width, size.height))
                    drawRect(ChartPalette.forIndex(i),
                        size = Size(size.width * (value / total), size.height))
                }
                Text("¥${money(value)}", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.20f).padding(start = 8.dp))
            }
        }
    }
}
