package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
import com.tencent.kuikly.compose.foundation.gestures.calculateCentroidSize
import com.tencent.kuikly.compose.foundation.gestures.calculateZoom
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.input.pointer.PointerEvent
import com.tencent.kuikly.compose.ui.input.pointer.PointerInputScope
import com.tencent.kuikly.compose.ui.input.pointer.positionChanged
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.compose.ui.util.fastAny
import com.tencent.kuikly.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.roundToInt

/** 图上默认展示的最近根数；全量仍由调用方保留。 */
const val VISIBLE_KLINE_BARS = 30

/** 双指放大后最少保留的根数。 */
internal const val MIN_VISIBLE_KLINE_BARS = 10

/**
 * 日 K 可见窗口，[start] 相对全量 [bars] 下标。
 */
data class KlineWindow(
    val start: Int,
    val count: Int,
) {
    fun followTotal(total: Int): KlineWindow {
        if (total <= 0) {
            return KlineWindow(0, 0)
        }
        val nextCount = if (count <= 0) {
            total.coerceAtMost(VISIBLE_KLINE_BARS)
        } else {
            count.coerceIn(minVisibleCount(total), total)
        }
        return KlineWindow(start = total - nextCount, count = nextCount)
    }

    fun slice(bars: List<KLineBar>): List<KLineBar> {
        val resolved = followTotal(bars.size)
        if (bars.isEmpty() || resolved.count <= 0) {
            return emptyList()
        }
        val from = resolved.start
        val to = (from + resolved.count).coerceAtMost(bars.size)
        return if (from >= to) emptyList() else bars.subList(from, to)
    }
}

internal fun defaultKlineWindow(total: Int): KlineWindow {
    if (total <= 0) {
        return KlineWindow(0, 0)
    }
    val count = total.coerceAtMost(VISIBLE_KLINE_BARS)
    return KlineWindow(start = total - count, count = count)
}

/**
 * 只负责画蜡烛和十字光标。可见区间由 [window] 控制；最新一根固定在右侧，双指开合只改左侧历史长度。
 */
@Composable
fun StockKlineChart(
    bars: List<KLineBar>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    window: KlineWindow = defaultKlineWindow(bars.size),
    onWindowChange: (KlineWindow) -> Unit = {},
    onLongPress: (Int) -> Unit = {},
) {
    val resolved = window.followTotal(bars.size)
    val visibleBars = resolved.slice(bars)
    if (visibleBars.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("暂无 K 线数据", fontSize = 14.sp, color = ChatComposeTheme.placeholder)
        }
        return
    }
    val parsed = visibleBars.map { it.toCandle() }
    val drawable = parsed.mapIndexedNotNull { index, candle ->
        candle?.let { index to it }
    }
    if (drawable.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("暂无 K 线数据", fontSize = 14.sp, color = ChatComposeTheme.placeholder)
        }
        return
    }
    val onSelectLatest = rememberUpdatedState(onSelect)
    val onLongPressLatest = rememberUpdatedState(onLongPress)
    val onWindowLatest = rememberUpdatedState(onWindowChange)
    val windowLatest = rememberUpdatedState(resolved)
    val totalLatest = rememberUpdatedState(bars.size)
    val longPress = rememberChartLongPress { x, _, width ->
        val win = windowLatest.value
        val local = indexFromX(x, width, win.count)
        onLongPressLatest.value(win.start + local)
    }
    val highLabel = drawable.maxOf { it.second.high }.toPlain()
    val lowLabel = drawable.minOf { it.second.low }.toPlain()
    val firstDay = visibleBars.first().day
    val lastDay = visibleBars.last().day
    val spanYears = klineYear(firstDay) != klineYear(lastDay)
    val firstLabel = formatKlineAxisDay(firstDay, withYear = spanYears)
    val lastLabel = formatKlineAxisDay(lastDay, withYear = spanYears)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp)
                    .pointerInput(bars.size) {
                        detectKlineGestures(
                            onSelectAt = { x, width ->
                                val win = windowLatest.value
                                val local = indexFromX(x, width, win.count)
                                onSelectLatest.value(win.start + local)
                            },
                            onHoldStart = { x, y, width -> longPress.start(x, y, width) },
                            onHoldCancel = { longPress.cancel() },
                            holdFilled = { longPress.completed },
                            visibleCount = { windowLatest.value.count },
                            onZoomCount = { count ->
                                val total = totalLatest.value
                                val next = KlineWindow(start = 0, count = count).followTotal(total)
                                if (next != windowLatest.value) {
                                    onWindowLatest.value(next)
                                }
                            },
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val min = drawable.minOf { it.second.low }
                    val max = drawable.maxOf { it.second.high }
                    val range = (max - min).let { if (it <= 0.0) 1.0 else it }
                    val count = visibleBars.size
                    val slot = size.width / count
                    parsed.forEachIndexed { index, candle ->
                        if (candle == null) return@forEachIndexed
                        val absolute = resolved.start + index
                        val selected = absolute == selectedIndex
                        val color = if (candle.close >= candle.open) {
                            ChatComposeTheme.rise
                        } else {
                            ChatComposeTheme.fall
                        }
                        val centerX = slot * index + slot / 2f
                        val highY = yOf(candle.high, min, range, size.height)
                        val lowY = yOf(candle.low, min, range, size.height)
                        val openY = yOf(candle.open, min, range, size.height)
                        val closeY = yOf(candle.close, min, range, size.height)
                        val bodyWidth = (slot * if (selected) 0.78f else 0.62f).coerceAtLeast(1f)
                        if (selected) {
                            drawLine(
                                color = ChatComposeTheme.title.copy(alpha = 0.28f),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, size.height),
                                strokeWidth = 1.2f,
                            )
                        }
                        drawLine(
                            color = color,
                            start = Offset(centerX, highY),
                            end = Offset(centerX, lowY),
                            strokeWidth = if (selected) 2.4f else 1.5f,
                        )
                        // Kuikly Canvas drawRect 会 closePath；原生 beginPath 失效时
                        // 最后一根会接到第一根，中间被大块 fill 盖住。实体改用粗线，只 stroke。
                        drawLine(
                            color = color,
                            start = Offset(centerX, openY),
                            end = Offset(centerX, closeY),
                            strokeWidth = bodyWidth.coerceAtLeast(2f),
                        )
                    }
                    longPress.center?.let { drawLongPressRing(it, longPress.progress) }
                }
            }
            Text(
                text = highLabel,
                fontSize = 10.sp,
                color = ChatComposeTheme.placeholder,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
            Text(
                text = lowLabel,
                fontSize = 10.sp,
                color = ChatComposeTheme.placeholder,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = firstLabel, fontSize = 10.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = lastLabel, fontSize = 10.sp, color = ChatComposeTheme.placeholder)
        }
    }
}

/**
 * 单指点选蜡烛；按住开始转圈，转满由 [rememberChartLongPress] 提问。
 * 过 slop / 双指 / 松手取消圆圈。双指开合缩放可见根数，最新一根始终贴右。
 * 手势 API：KuiklyUI docs/Compose/gesture-system.md、animation-system.md。
 */
private suspend fun PointerInputScope.detectKlineGestures(
    onSelectAt: (x: Float, width: Float) -> Unit,
    onHoldStart: (x: Float, y: Float, width: Float) -> Unit,
    onHoldCancel: () -> Unit,
    holdFilled: () -> Boolean,
    visibleCount: () -> Int,
    onZoomCount: (count: Int) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var accZoom = 1f
        var baseCount = 0
        var pastTouchSlop = false
        var isMulti = false
        var selected = false
        var holding = true
        val touchSlop = viewConfiguration.touchSlop
        val width = size.width.toFloat()
        var stillDown = true
        onHoldStart(down.position.x, down.position.y, width)

        fun dropHold() {
            if (holding) {
                onHoldCancel()
                holding = false
            }
        }

        fun handle(event: PointerEvent): Boolean {
            val canceled = event.changes.fastAny { it.isConsumed }
            if (canceled) {
                return false
            }
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                isMulti = true
            }
            if (isMulti) {
                val zoomChange = event.calculateZoom()
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    if (zoomMotion > touchSlop) {
                        pastTouchSlop = true
                        baseCount = visibleCount().coerceAtLeast(1)
                        accZoom = 1f
                    }
                }
                if (pastTouchSlop) {
                    accZoom *= zoomChange
                    if (accZoom > 0f) {
                        onZoomCount((baseCount / accZoom).roundToInt())
                    }
                    event.changes.fastForEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            } else {
                val change = event.changes.firstOrNull { it.pressed }
                    ?: event.changes.firstOrNull()
                    ?: down
                val distance = (change.position - down.position).getDistance()
                if (!pastTouchSlop && distance > touchSlop) {
                    pastTouchSlop = true
                }
                if (pastTouchSlop) {
                    selected = true
                    onSelectAt(change.position.x, width)
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }
            }
            return event.changes.fastAny { it.pressed }
        }

        while (true) {
            val event = awaitPointerEvent()
            stillDown = handle(event)
            if (isMulti || pastTouchSlop || !stillDown) {
                dropHold()
                break
            }
        }
        while (stillDown) {
            val event = awaitPointerEvent()
            stillDown = handle(event)
        }
        if (!isMulti && !selected && !holdFilled()) {
            onSelectAt(down.position.x, width)
        }
    }
}

private fun minVisibleCount(total: Int): Int {
    return MIN_VISIBLE_KLINE_BARS.coerceAtMost(total).coerceAtLeast(1)
}

internal fun formatKlineAxisDay(raw: String, withYear: Boolean = false): String {
    val date = klineDate(raw) ?: return raw
    val year = date.substring(0, 4)
    val month = date.substring(5, 7).trimStart('0').ifEmpty { "0" }
    val day = date.substring(8, 10).trimStart('0').ifEmpty { "0" }
    return if (withYear) {
        "${year}-${date.substring(5, 7)}-${date.substring(8, 10)}"
    } else {
        "${month}月${day}日"
    }
}

internal fun klineYear(raw: String): String {
    return klineDate(raw)?.substring(0, 4).orEmpty()
}

private fun klineDate(raw: String): String? {
    return when {
        raw.length >= 10 && raw[4] == '-' && raw[7] == '-' -> raw.substring(0, 10)
        raw.length >= 8 && raw.take(8).all { it.isDigit() } -> {
            "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        }
        else -> null
    }
}

private data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

private fun KLineBar.toCandle(): Candle? {
    val open = open.toDoubleOrNull() ?: return null
    val high = high.toDoubleOrNull() ?: return null
    val low = low.toDoubleOrNull() ?: return null
    val close = close.toDoubleOrNull() ?: return null
    return Candle(open = open, high = high, low = low, close = close)
}

private fun yOf(price: Double, min: Double, range: Double, height: Float): Float {
    val ratio = ((price - min) / range).toFloat()
    return height - ratio * height
}

private fun indexFromX(x: Float, width: Float, count: Int): Int {
    if (count <= 0 || width <= 0f) return 0
    return (x / width * count).toInt().coerceIn(0, count - 1)
}

private fun Double.toPlain(): String {
    val text = toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
