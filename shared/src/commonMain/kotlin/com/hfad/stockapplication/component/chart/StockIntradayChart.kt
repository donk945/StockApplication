package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
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

/** 沪深分时横轴：09:30–11:30 + 13:00–15:00，共 241 个分钟槽。 */
private const val MINUTE_SLOTS = 241

private data class MinutePoint(
    val barIndex: Int,
    val slot: Int,
    val price: Double,
)

/**
 * 当日分时折线。按交易时段落点；拖动十字光标，选中分钟下标回传 [onSelect]。
 */
@Composable
fun StockIntradayChart(
    bars: List<KLineBar>,
    prevClose: String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (Int) -> Unit = {},
) {
    val sessionDate = bars.mapNotNull { bar ->
        bar.day.takeIf { it.length >= 10 && it[4] == '-' }?.substring(0, 10)
    }.maxOrNull()
    val points = bars.mapIndexedNotNull { index, bar ->
        if (sessionDate != null && !bar.day.startsWith(sessionDate)) {
            return@mapIndexedNotNull null
        }
        val price = bar.close.toDoubleOrNull() ?: return@mapIndexedNotNull null
        val slot = minuteSlot(bar.day) ?: return@mapIndexedNotNull null
        MinutePoint(barIndex = index, slot = slot, price = price)
    }
    if (points.size < 2) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无分时数据", fontSize = 14.sp, color = ChatComposeTheme.placeholder)
        }
        return
    }
    val base = prevClose.toDoubleOrNull() ?: points.first().price
    val minPrice = minOf(points.minOf { it.price }, base)
    val maxPrice = maxOf(points.maxOf { it.price }, base)
    val span = (maxPrice - minPrice).let { raw ->
        if (raw <= 0.0) abs(base) * 0.01 + 0.01 else raw
    }
    val pad = span * 0.08
    val low = minPrice - pad
    val high = maxPrice + pad
    val range = (high - low).let { if (it <= 0.0) 1.0 else it }
    val last = points.maxBy { it.slot }.price
    val lineColor = when {
        last > base -> ChatComposeTheme.rise
        last < base -> ChatComposeTheme.fall
        else -> ChatComposeTheme.title
    }
    val onSelectLatest = rememberUpdatedState(onSelect)
    val onLongPressLatest = rememberUpdatedState(onLongPress)
    val ordered = points.sortedBy { it.slot }
    val pointsLatest = rememberUpdatedState(ordered)
    val longPress = rememberChartLongPress { x, _, width ->
        onLongPressLatest.value(indexFromSlotX(x, width, pointsLatest.value))
    }

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
                    .pointerInput(points.size) {
                        detectMinuteGestures(
                            onSelectAt = { x, width ->
                                onSelectLatest.value(
                                    indexFromSlotX(x, width, pointsLatest.value),
                                )
                            },
                            onHoldStart = { x, y, width -> longPress.start(x, y, width) },
                            onHoldCancel = { longPress.cancel() },
                            holdFilled = { longPress.completed },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseY = yOf(base, low, range, size.height)
                    drawLine(
                        color = ChatComposeTheme.hairline,
                        start = Offset(0f, baseY),
                        end = Offset(size.width, baseY),
                        strokeWidth = 1f,
                    )
                    var prev: Offset? = null
                    var prevSlot = -1
                    ordered.forEach { point ->
                        val x = size.width * point.slot / (MINUTE_SLOTS - 1).toFloat()
                        val y = yOf(point.price, low, range, size.height)
                        val next = Offset(x, y)
                        val lastPoint = prev
                        if (lastPoint != null && point.slot >= prevSlot) {
                            drawLine(
                                color = lineColor,
                                start = lastPoint,
                                end = next,
                                strokeWidth = 2f,
                            )
                        }
                        prev = next
                        prevSlot = point.slot
                    }
                    val selected = ordered.firstOrNull { it.barIndex == selectedIndex }
                    if (selected != null) {
                        val x = size.width * selected.slot / (MINUTE_SLOTS - 1).toFloat()
                        val y = yOf(selected.price, low, range, size.height)
                        drawLine(
                            color = ChatComposeTheme.title.copy(alpha = 0.28f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.2f,
                        )
                        val tick = 4f
                        drawLine(
                            color = lineColor,
                            start = Offset(x - tick, y),
                            end = Offset(x + tick, y),
                            strokeWidth = 2f,
                        )
                        drawLine(
                            color = lineColor,
                            start = Offset(x, y - tick),
                            end = Offset(x, y + tick),
                            strokeWidth = 2f,
                        )
                    }
                    longPress.center?.let { drawLongPressRing(it, longPress.progress) }
                }
            }
            Text(
                text = high.toPlain(),
                fontSize = 10.sp,
                color = ChatComposeTheme.placeholder,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
            Text(
                text = low.toPlain(),
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
            Text("09:30", fontSize = 10.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.weight(1f))
            Text("11:30/13:00", fontSize = 10.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.weight(1f))
            Text("15:00", fontSize = 10.sp, color = ChatComposeTheme.placeholder)
        }
    }
}

/**
 * 短拖选点；按住开始转圈，转满由 [rememberChartLongPress] 提问。
 * 过 slop / 双指 / 松手取消圆圈。手势 API：KuiklyUI docs/Compose/gesture-system.md、animation-system.md。
 */
private suspend fun PointerInputScope.detectMinuteGestures(
    onSelectAt: (x: Float, width: Float) -> Unit,
    onHoldStart: (x: Float, y: Float, width: Float) -> Unit,
    onHoldCancel: () -> Unit,
    holdFilled: () -> Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
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
                return event.changes.fastAny { it.pressed }
            }
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

internal fun minuteSessionDateLabel(bars: List<KLineBar>): String {
    val raw = bars.lastOrNull()?.day.orEmpty()
    val date = when {
        raw.length >= 10 && raw[4] == '-' -> raw.substring(0, 10)
        else -> return ""
    }
    val month = date.substring(5, 7).toIntOrNull() ?: return date
    val day = date.substring(8, 10).toIntOrNull() ?: return date
    return "${month}月${day}日"
}

private fun clockOf(label: String): String? {
    return when {
        label.length >= 16 && label[10] == ' ' && label[13] == ':' -> label.substring(11, 16)
        label.length >= 5 && label[2] == ':' -> label.substring(0, 5)
        else -> null
    }
}

private fun minuteSlot(label: String): Int? {
    val clock = clockOf(label) ?: return null
    val hour = clock.substring(0, 2).toIntOrNull() ?: return null
    val minute = clock.substring(3, 5).toIntOrNull() ?: return null
    val t = hour * 60 + minute
    val morningStart = 9 * 60 + 30
    val morningEnd = 11 * 60 + 30
    val afternoonStart = 13 * 60
    return when {
        t <= morningEnd -> (t - morningStart).coerceIn(0, 120)
        else -> (121 + (t - afternoonStart)).coerceIn(121, MINUTE_SLOTS - 1)
    }
}

private fun yOf(price: Double, min: Double, range: Double, height: Float): Float {
    val ratio = ((price - min) / range).toFloat()
    return height - ratio * height
}

private fun indexFromSlotX(x: Float, width: Float, points: List<MinutePoint>): Int {
    if (points.isEmpty() || width <= 0f) return 0
    val target = (x / width * (MINUTE_SLOTS - 1)).roundToInt()
        .coerceIn(0, MINUTE_SLOTS - 1)
    var best = points.first()
    var bestDist = abs(best.slot - target)
    for (point in points) {
        val dist = abs(point.slot - target)
        if (dist < bestDist) {
            best = point
            bestDist = dist
        }
    }
    return best.barIndex
}

private fun Double.toPlain(): String {
    val rounded = (this * 100.0).toLong() / 100.0
    val text = rounded.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
