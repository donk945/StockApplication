package com.hfad.stockapplication.data.chat

/**
 * 一根日 K / 分时点，给自制图组件使用。
 */
data class KLineBar(
    val day: String,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
)
