package com.xieqinglin.fitness.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    @Test fun unitsUseIntegerGramsWithoutDrift() {
        val grams = Rules.weight("80.25", WeightUnit.KG)
        repeat(1000) {
            assertEquals("160.50", formatWeight(grams, WeightUnit.JIN))
            assertEquals("80.25", formatWeight(grams, WeightUnit.KG))
        }
        assertEquals(80005L, Rules.weight("160.01", WeightUnit.JIN))
    }
    @Test fun invalidWeightInputsAreRejected() {
        listOf("", "0", "-1", "abc", "NaN", "Infinity", "1.234", "1e2", "9".repeat(100)).forEach { text ->
            assertTrue("Should reject $text", runCatching { Rules.weight(text, WeightUnit.KG) }.isFailure)
        }
    }
    @Test fun futureAndMalformedDatesAreRejected() {
        assertTrue(runCatching { Rules.pastDate("2026-11-10", LocalDate.parse("2026-11-09")) }.isFailure)
        listOf("2026-02-30", "2026-1-01", "text").forEach { assertTrue(runCatching { Rules.date(it) }.isFailure) }
    }
    @Test fun negativeZeroIsNotShown() {
        assertEquals("0.00", formatDelta(-.1, WeightUnit.KG))
        assertEquals("+0.50", formatDelta(500.0, WeightUnit.KG))
    }
    @Test fun loadZeroIsDifferentFromUnset() {
        val first = Values(sets = listOf(SetValue(10, 0), SetValue(10, null)))
        Rules.values(ExerciseKind.STRENGTH, first)
        assertTrue(first.description(ExerciseKind.STRENGTH, WeightUnit.KG).contains("0.00 kg"))
        assertNull(first.sets[1].loadGrams)
    }
    @Test fun actualZeroAllowedButPlanZeroRejected() {
        val zero = Values(sets = listOf(SetValue(0)))
        Rules.values(ExerciseKind.STRENGTH, zero, true)
        assertTrue(runCatching { Rules.values(ExerciseKind.STRENGTH, zero) }.isFailure)
    }
    @Test fun unexpectedValueFieldsAreRejected() {
        assertTrue(runCatching { Rules.values(ExerciseKind.DURATION, Values(seconds = 60.0, kilometers = 1.0)) }.isFailure)
        assertTrue(runCatching { Rules.values(ExerciseKind.DISTANCE, Values(kilometers = Double.POSITIVE_INFINITY)) }.isFailure)
    }
}
