package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.animation.core.AnimationEndReason
import com.tencent.kuikly.compose.animation.core.LinearEasing
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.drawscope.DrawScope
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.unit.dp

/** 与 Kuikly `viewConfiguration.longPressTimeoutMillis` 默认 500ms 对齐。 */
internal const val CHART_LONG_PRESS_MS = 500

/**
 * 图上长按进度。按住开始转圈，松手 / 滑出 slop / 双指则取消；转满才回调 [onFilled]。
 */
internal class ChartLongPressState {
    var center by mutableStateOf<Offset?>(null)
        private set
    var width by mutableStateOf(0f)
        private set
    var epoch by mutableStateOf(0)
        private set
    var completed by mutableStateOf(false)
        private set
    val anim = Animatable(0f)
    val progress: Float
        get() = anim.value

    fun start(x: Float, y: Float, width: Float) {
        center = Offset(x, y)
        this.width = width
        completed = false
        epoch += 1
    }

    fun cancel() {
        if (completed) {
            return
        }
        if (center == null && epoch == 0) {
            return
        }
        center = null
        epoch += 1
    }

    fun markFilled() {
        completed = true
    }
}

@Composable
internal fun rememberChartLongPress(
    onFilled: (x: Float, y: Float, width: Float) -> Unit,
): ChartLongPressState {
    val state = remember { ChartLongPressState() }
    val onFilledLatest = rememberUpdatedState(onFilled)
    LaunchedEffect(state.epoch) {
        val point = state.center
        if (point == null) {
            state.anim.snapTo(0f)
            return@LaunchedEffect
        }
        state.anim.snapTo(0f)
        val result = state.anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = CHART_LONG_PRESS_MS,
                easing = LinearEasing,
            ),
        )
        if (result.endReason == AnimationEndReason.Finished && state.center == point) {
            state.markFilled()
            onFilledLatest.value(point.x, point.y, state.width)
        }
    }
    return state
}

internal fun DrawScope.drawLongPressRing(center: Offset, progress: Float) {
    val amount = progress.coerceIn(0f, 1f)
    val radius = 22.dp.toPx()
    val stroke = 3.5.dp.toPx()
    drawCircle(
        color = ChatComposeTheme.accent.copy(alpha = 0.10f + 0.10f * amount),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = ChatComposeTheme.accent.copy(alpha = 0.22f),
        radius = radius,
        center = center,
        style = Stroke(width = stroke),
    )
    if (amount <= 0f) {
        return
    }
    val diameter = radius * 2f
    drawArc(
        color = ChatComposeTheme.accent,
        startAngle = -90f,
        sweepAngle = 360f * amount,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(diameter, diameter),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}
