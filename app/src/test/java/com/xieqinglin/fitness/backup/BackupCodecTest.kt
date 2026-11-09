package com.xieqinglin.fitness.backup

import com.xieqinglin.fitness.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {
    private val today = LocalDate.parse("2026-11-09")
    private val data = FitnessData(weights = listOf(WeightEntry(date = "2026-11-01", grams = 80250)), settings = Settings(WeightUnit.JIN, 75000))
    private fun rejected(data: FitnessData) = assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(data), today) }.isFailure)
    @Test fun allFieldsRoundTrip() { assertEquals(data, BackupCodec.decode(BackupCodec.encode(data), today).data) }
    @Test fun futureSchemaIsRejected() { assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(data).replace("\"schemaVersion\":1", "\"schemaVersion\":2"), today) }.isFailure) }
    @Test fun brokenJsonIsRejected() { assertTrue(runCatching { BackupCodec.decode("{broken}", today) }.isFailure) }
    @Test fun countMismatchIsRejected() { assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(data).replace("\"weights\":1", "\"weights\":2"), today) }.isFailure) }
    @Test fun duplicateDatesAreRejected() { rejected(data.copy(weights = data.weights + data.weights.single().copy(id = newId()))) }
    @Test fun orphanExecutionIsRejected() {
        rejected(data.copy(executions = listOf(Execution(date = "2026-11-01", exerciseId = "missing", nameSnapshot = "拉伸", kindSnapshot = ExerciseKind.DURATION,
            targetSnapshot = Values(seconds = 60.0), actual = Values(seconds = 50.0), status = ExecutionStatus.DONE))))
    }
    @Test fun futureWeightIsRejected() { rejected(data.copy(weights = listOf(WeightEntry(date = "2026-11-10", grams = 80000)))) }
    @Test fun restWithExercisesIsRejected() {
        val item = Exercise(name = "拉伸", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0))
        rejected(data.copy(plans = listOf(DailyPlan(date = "2026-11-01", rest = true, exercises = listOf(item)))))
    }
    @Test fun invalidUnitAndMissingRequiredValuesAreRejected() {
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(data).replace("\"JIN\"", "\"LB\""), today) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(data).replace("\"grams\":80250,", ""), today) }.isFailure)
    }
    @Test fun quotedNumberIsNotAcceptedAsNumericField() {
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(data).replace("\"grams\":80250", "\"grams\":\"80250\""), today) }.isFailure)
    }
    @Test fun missingIdCannotSilentlyGenerateANewIdentity() {
        val text = BackupCodec.encode(data).replace("\"id\":\"${data.weights.single().id}\",", "")
        assertTrue(runCatching { BackupCodec.decode(text, today) }.isFailure)
    }
}
