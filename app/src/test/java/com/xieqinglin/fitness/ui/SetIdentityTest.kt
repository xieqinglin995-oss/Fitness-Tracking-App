package com.xieqinglin.fitness.ui

import com.xieqinglin.fitness.domain.*
import org.junit.Assert.*
import org.junit.Test

class SetIdentityTest {
    @Test fun legacyGroupsHaveDeterministicIdsAndRemovingMiddleDoesNotMoveChecks() {
        val old = Values(sets = listOf(SetValue(10), SetValue(12), SetValue(8)))
        val draft = ValuesDraft.from(old, WeightUnit.KG, "exercise")
        assertEquals(draft.rowIds(), ValuesDraft.from(old, WeightUnit.KG, "exercise").rowIds())
        val confirmed = listOf(draft.rowIds()[0], draft.rowIds()[2])
        val removed = draft.remove(1)
        val actual = removed.parse(ExerciseKind.STRENGTH, WeightUnit.KG, true)
        assertEquals(confirmed, actual.sets.map { it.id })
        assertEquals(listOf(10, 8), actual.sets.map { it.reps })
        val appended = removed.append(1)
        assertEquals(3, appended.rowIds().distinct().size)
        assertEquals(confirmed, appended.rowIds().take(2))
    }
    @Test fun openingAndSavingUntouchedLoadDoesNotRoundAwayGrams() {
        val source = Values(sets = listOf(SetValue(10, 12555, "set")))
        val draft = ValuesDraft.from(source, WeightUnit.KG)
        assertEquals(source, draft.parse(ExerciseKind.STRENGTH, WeightUnit.KG, true))
        val two = ValuesDraft.from(Values(sets = listOf(SetValue(10, 12555, "a"), SetValue(12, 12556, "b"))), WeightUnit.KG)
        val copied = two.copyRow(0, 1).parse(ExerciseKind.STRENGTH, WeightUnit.KG, true)
        assertEquals(listOf(12555L, 12555L), copied.sets.map { it.loadGrams })
        assertEquals(listOf("a", "b"), copied.sets.map { it.id })
    }
}
