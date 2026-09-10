package com.hfad.stockapplication.data.chat

/**
 * 日 K 均线。与可见窗口对齐后交给 Canvas 描线。
 */
object KlineIndicators {

    val PERIODS: List<Int> = listOf(5, 10, 20)

    fun sma(bars: List<KLineBar>, period: Int): List<Double?> {
        if (period <= 0 || bars.isEmpty()) {
            return List(bars.size) { null }
        }
        val closes = bars.map { it.close.toDoubleOrNull() }
        return closes.indices.map { index ->
            if (index + 1 < period) {
                return@map null
            }
            var sum = 0.0
            for (i in (index - period + 1)..index) {
                val price = closes[i] ?: return@map null
                sum += price
            }
            sum / period
        }
    }
}
