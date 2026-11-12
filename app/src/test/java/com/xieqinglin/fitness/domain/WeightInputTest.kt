package com.xieqinglin.fitness.domain

import org.junit.Assert.*
import org.junit.Test

class WeightInputTest {
    @Test fun jinAndKgConversionPreservesFractionalGramsAcrossToggles() {
        var input = WeightInput("160.01", WeightUnit.JIN)
        repeat(100) { input = input.switchTo(WeightUnit.KG).switchTo(WeightUnit.JIN) }
        assertEquals(80005L, input.grams())
        assertEquals("160.01", input.text)
        assertEquals("80.005 kg", input.equivalent())
    }
    @Test fun editingAfterConversionUsesTheNewVisibleNumber() {
        val input = WeightInput("160.25", WeightUnit.JIN).switchTo(WeightUnit.KG)
        assertEquals(80125L, input.grams())
        assertEquals("80.13", input.text)
        assertEquals(81000L, input.edit("81").grams())
    }
    @Test fun emptySwitchAllowedButInvalidInputCannotBeReinterpreted() {
        assertEquals(WeightUnit.JIN, WeightInput("", WeightUnit.KG).switchTo(WeightUnit.JIN).unit)
        assertTrue(runCatching { WeightInput("abc", WeightUnit.KG).switchTo(WeightUnit.JIN) }.isFailure)
    }
    @Test fun bodyUnitSwitchDoesNotChangeTrainingLoadUnit() {
        val original = Settings()
        assertEquals(WeightUnit.KG, original.copy(unit = WeightUnit.JIN).loadUnit)
        assertEquals(WeightUnit.JIN, Settings(WeightUnit.JIN).loadUnit)
    }
}
