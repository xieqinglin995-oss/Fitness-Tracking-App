package com.xieqinglin.fitness.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatisticsTest {
    private val today = LocalDate.parse("2026-11-09")
    private fun entry(day: String, grams: Long) = WeightEntry(date = day, grams = grams)

    @Test fun calendarWindowExampleDoesNotFillMissingDates() {
        val rows = listOf(entry("2026-11-01", 80000), entry("2026-11-03", 79600), entry("2026-11-07", 79400))
        val points = Statistics.points(rows, to = today)
        assertEquals(3, points.size)
        assertNull(points[0].averageGrams)
        assertNull(points[1].averageGrams)
        assertEquals(79666.666666, points.last().averageGrams!!, .0001)
        assertEquals(3, points.last().samples)
    }
    @Test fun oldRecordsCannotGenerateTodaysTrend() {
        val rows = listOf(entry("2026-11-01", 80000), entry("2026-11-02", 79000), entry("2026-11-03", 78000), entry("2026-11-09", 77000))
        val point = Statistics.points(rows, today, today).single()
        assertNull(point.averageGrams)
        assertEquals(2, point.samples)
    }
    @Test fun rangeChangesDoNotChangeWindow() {
        val rows = (1..9).map { entry("2026-11-0$it", 80000L - it * 100) }
        assertEquals(Statistics.points(rows, to = today).last(), Statistics.points(rows, today, today).single())
    }
    @Test fun yearBoundaryUsesCalendarDays() {
        val rows = listOf(entry("2025-12-29", 80000), entry("2025-12-31", 79000), entry("2026-01-04", 78000))
        assertEquals(79000.0, Statistics.points(rows, to = LocalDate.parse("2026-01-04")).last().averageGrams!!, .001)
    }
    @Test fun leapDayIsPartOfWindow() {
        val rows = listOf(entry("2024-02-24", 81000), entry("2024-02-29", 80000), entry("2024-03-01", 79000))
        assertEquals(80000.0, Statistics.points(rows, to = LocalDate.parse("2024-03-01")).last().averageGrams!!, .001)
    }
    @Test fun emptyAndSingleRecordHaveNoInventedChanges() {
        assertTrue(Statistics.points(emptyList()).isEmpty())
        val stats = Statistics.stats(listOf(entry("2026-11-09", 80250)), null, today)
        assertNull(stats.change); assertNull(stats.sincePrevious)
        assertEquals(80250.0, stats.mean!!, .001)
    }
    @Test fun summaryUsesFirstAndLastInsideRange() {
        val rows = listOf(entry("2026-10-01", 90000), entry("2026-11-01", 80000), entry("2026-11-08", 79000), entry("2026-11-09", 79500))
        val stats = Statistics.stats(rows, today.minusDays(6), today)
        assertEquals(500.0, stats.change!!, .001)
        assertEquals(500.0, stats.sincePrevious!!, .001)
        assertEquals("2026-11-09", stats.latest!!.date)
        assertEquals(2, stats.entries.size)
    }
}
