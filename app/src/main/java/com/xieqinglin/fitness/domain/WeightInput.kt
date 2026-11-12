package com.xieqinglin.fitness.domain

import java.math.BigDecimal

// Keep the exact grams through unit toggles, even when the displayed value rounds.
data class WeightInput(val text: String, val unit: WeightUnit, val exactGrams: Long? = null) {
    fun grams(): Long = exactGrams ?: Rules.weight(text, unit)
    fun edit(text: String) = copy(text = text, exactGrams = null)
    fun adjust(tenths: Int): WeightInput {
        val next = Math.addExact(grams(), tenths * if (unit == WeightUnit.KG) 100L else 50L)
        require(next > 0) { "体重须大于 0" }
        return copy(text = formatWeight(next, unit), exactGrams = next)
    }
    fun switchTo(next: WeightUnit): WeightInput {
        if (next == unit) return this
        if (text.isBlank()) return WeightInput("", next)
        val grams = grams()
        return WeightInput(formatWeight(grams, next), next, grams)
    }
    fun equivalent(): String? = runCatching {
        val other = if (unit == WeightUnit.KG) WeightUnit.JIN else WeightUnit.KG
        BigDecimal(grams()).divide(BigDecimal(if (other == WeightUnit.KG) 1000 else 500))
            .stripTrailingZeros().toPlainString() + " ${other.label}"
    }.getOrNull()
}
