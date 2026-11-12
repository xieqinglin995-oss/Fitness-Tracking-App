package com.xieqinglin.fitness.backup

import com.xieqinglin.fitness.domain.*
import org.junit.Assert.*
import org.junit.Test

class V4BackupTest {
    @Test fun stableGroupsAndAllNewMetadataRoundTrip() {
        val item = Exercise(id = "squat", name = "深蹲", kind = ExerciseKind.STRENGTH,
            target = Values(sets = listOf(SetValue(10, 12005, "a"), SetValue(12, 12005, "c"))), definitionId = "definition")
        val record = Execution(id = "actual", date = "2026-01-01", exerciseId = item.id, nameSnapshot = item.name,
            kindSnapshot = item.kind, targetSnapshot = item.target, actual = item.target, status = ExecutionStatus.DRAFT,
            completedSets = listOf(0), completedSetIds = listOf("a"), note = "本次备注")
        val data = FitnessData(plans = listOf(DailyPlan(id = "plan", date = record.date, exercises = listOf(item))), executions = listOf(record),
            settings = Settings(library = listOf(ExerciseDefinition("definition", "深蹲", item.kind, "座椅4档")),
                sessions = listOf(SessionRecord(record.date, "2026-01-01T08:00:00+08:00", "部分结束")), sources = listOf(PlanSource(record.date, "template", "batch"))))
        val backup = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(4, backup.schemaVersion); assertEquals(data, backup.data)
        assertEquals(1, backup.counts.definitions); assertEquals(1, backup.counts.sessions)
        val completed = TrainingCompletion.complete(data, record.date, setOf(item.id)).data
        assertEquals(listOf("a", "c"), completed.executions.single().completedSetIds)
        Rules.validate(completed)
        val malformed = BackupCodec.encode(data).replace("\"completedSetIds\":[\"a\"]", "\"completedSetIds\":[\"c\"]")
        assertTrue(runCatching { BackupCodec.decode(malformed) }.isFailure)
    }
    @Test fun oldV3ExportHasNoNewMetadataAndKeepsOriginalValues() {
        val text = javaClass.getResource("/legacy-v3.json")!!.readText()
        val data = BackupCodec.decode(text).data
        assertEquals(30, data.weights.size); assertEquals(3, data.plans.size)
        assertEquals(WeightUnit.JIN, data.settings.unit); assertEquals(WeightUnit.KG, data.settings.loadUnit)
        assertTrue(data.settings.sessions.isEmpty())
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)).data)
    }
    @Test fun futureDatedHistoryRequiresExplicitRestoreModeAndDoesNotMoveDates() {
        val data = FitnessData(weights = listOf(WeightEntry(date = "2026-01-02", grams = 80000)))
        val clock = java.time.LocalDate.parse("2026-01-01")
        val text = BackupCodec.encode(data)
        assertTrue(runCatching { BackupCodec.decode(text, clock) }.isFailure)
        assertEquals("2026-01-02", BackupCodec.decode(text, clock, allowFutureHistory = true).data.weights.single().date)
    }
}
