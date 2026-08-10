package com.amazecc.app.shared.utils

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

fun Double.toFixed(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (this * factor).roundToLong()
    val intPart = rounded / factor.toLong()
    if (decimals == 0) return intPart.toString()
    val fracPart = abs(rounded % factor.toLong())
    return "$intPart.${fracPart.toString().padStart(decimals, '0')}"
}

fun Double.toFixed(decimals: Int, suffix: String): String = "${toFixed(decimals)}$suffix"

fun Float.toFixed(decimals: Int): String = toDouble().toFixed(decimals)

fun Float.toFixed(decimals: Int, suffix: String): String = "${toFixed(decimals)}$suffix"
