package com.xieqinglin.fitness.domain

import java.math.BigDecimal

// Keep the exact grams through unit toggles, even when the displayed value rounds.
data class WeightInput(val text: String, val unit: WeightUnit, val exactGrams: Long? = null) {
    fun grams(): Long = exactGrams ?: Rules.weight(text, unit)
    fun edit(text: String) = copy(text = text, exactGrams = null)
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
