package com.xieqinglin.fitness.domain

import java.time.LocalDate

data class TrendPoint(val date: LocalDate, val grams: Long, val averageGrams: Double?, val samples: Int)
data class WeightStats(val entries: List<WeightEntry>) {
    val latest = entries.lastOrNull()
    val previous = entries.dropLast(1).lastOrNull()
    val change = if (entries.size >= 2) (entries.last().grams - entries.first().grams).toDouble() else null
    val sincePrevious = previous?.let { latest!!.grams.toDouble() - it.grams }
    val mean = entries.takeIf { it.isNotEmpty() }?.map { it.grams.toDouble() }?.average()
}

object Statistics {
    data class Comparison(val recent: WeightStats, val previous: WeightStats) {
        val delta = if (recent.entries.size >= 3 && previous.entries.size >= 3) recent.mean!! - previous.mean!! else null
    }
    fun comparison(entries: List<WeightEntry>, today: LocalDate): Comparison = Comparison(
        stats(entries, today.minusDays(6), today), stats(entries, today.minusDays(13), today.minusDays(7)))
    // Compute against all history before filtering, so range changes cannot change a trend value.
    fun points(entries: List<WeightEntry>, from: LocalDate? = null, to: LocalDate = LocalDate.now()): List<TrendPoint> {
        val sorted = entries.sortedBy { it.date }
        var left = 0
        var sum = 0.0
        return buildList {
            sorted.forEachIndexed { index, entry ->
                val date = LocalDate.parse(entry.date)
                sum += entry.grams
                while (LocalDate.parse(sorted[left].date).isBefore(date.minusDays(6))) {
                    sum -= sorted[left].grams
                    left++
                }
                val count = index - left + 1
                if ((from == null || !date.isBefore(from)) && !date.isAfter(to)) {
                    add(TrendPoint(date, entry.grams, if (count >= 3) sum / count else null, count))
                }
            }
        }
    }

    fun stats(entries: List<WeightEntry>, from: LocalDate?, to: LocalDate): WeightStats = WeightStats(
        entries.filter { (from == null || it.date >= from.toString()) && it.date <= to.toString() }.sortedBy { it.date }
    )
}
