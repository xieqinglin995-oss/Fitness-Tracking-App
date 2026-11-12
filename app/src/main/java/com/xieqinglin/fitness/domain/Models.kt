package com.xieqinglin.fitness.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun now(): String = OffsetDateTime.now().toString()

@Serializable enum class WeightUnit(val label: String) { KG("kg"), JIN("斤") }
@Serializable enum class ExerciseKind(val label: String) { STRENGTH("次数型"), DURATION("时长型"), DISTANCE("距离型") }
@Serializable enum class ExecutionStatus { DRAFT, DONE, SKIPPED }
@Serializable data class SetValue(val reps: Int, val loadGrams: Long? = null, val id: String = "")
@Serializable data class Values(
    val sets: List<SetValue> = emptyList(),
    val seconds: Double? = null,
    val kilometers: Double? = null,
)
@Serializable data class WeightEntry(
    val id: String = newId(), val date: String, val grams: Long,
    val note: String = "", val createdAt: String = now(), val updatedAt: String = now(),
)
@Serializable data class Exercise(
    val id: String = newId(), val name: String, val kind: ExerciseKind,
    val target: Values, val note: String = "", val temporary: Boolean = false, val definitionId: String? = null,
)
@Serializable data class DailyPlan(
    val id: String = newId(), val date: String, val name: String = "当日训练",
    val rest: Boolean = false, val exercises: List<Exercise> = emptyList(),
)
@Serializable data class Execution(
    val id: String = newId(), val date: String, val exerciseId: String,
    val nameSnapshot: String, val kindSnapshot: ExerciseKind, val targetSnapshot: Values,
    val actual: Values, val status: ExecutionStatus, val skipReason: String = "",
    val updatedAt: String = now(),
    val completedSets: List<Int> = if (kindSnapshot == ExerciseKind.STRENGTH && status == ExecutionStatus.DONE) actual.sets.indices.toList() else emptyList(),
    val completedSetIds: List<String>? = null, val note: String = "",
)
@Serializable data class WorkoutTemplate(
    val id: String = newId(), val name: String, val exercises: List<Exercise>,
)
@Serializable data class Settings(
    val unit: WeightUnit = WeightUnit.KG,
    val goalGrams: Long? = null,
    // Old installations used one unit for both. Preserve that choice on upgrade.
    val loadUnit: WeightUnit = unit,
    val library: List<ExerciseDefinition> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    val sources: List<PlanSource> = emptyList(),
)
@Serializable data class FitnessData(
    val weights: List<WeightEntry> = emptyList(), val plans: List<DailyPlan> = emptyList(),
    val executions: List<Execution> = emptyList(), val templates: List<WorkoutTemplate> = emptyList(),
    val settings: Settings = Settings(),
) {
    @kotlinx.serialization.Transient private val planIndex = plans.associateBy { it.date }
    @kotlinx.serialization.Transient private val executionIndex = executions.associateBy { it.exerciseId }
    @kotlinx.serialization.Transient private val itemIndex = plans.flatMap { it.exercises }.associateBy { it.id }
    fun plan(date: String): DailyPlan? = planIndex[date]
    fun execution(id: String): Execution? = executionIndex[id]
    fun item(id: String): Exercise? = itemIndex[id]
}

@Serializable data class ExerciseDefinition(val id: String = newId(), val name: String, val kind: ExerciseKind, val cue: String = "")
@Serializable data class SessionRecord(val date: String, val endedAt: String, val note: String = "")
@Serializable data class PlanSource(val date: String, val templateId: String, val batchId: String = "")

enum class DayStatus(val label: String) {
    EMPTY("未安排"), REST("休息日"), PENDING("待完成"), ACTIVE("进行中"), DONE("已完成"), ENDED("已结束")
}

fun FitnessData.dayStatus(date: String): DayStatus {
    val plan = plan(date) ?: return DayStatus.EMPTY
    if (plan.rest) return DayStatus.REST
    if (plan.exercises.isEmpty()) return DayStatus.EMPTY
    val records = plan.exercises.map { execution(it.id) }
    if (records.all { it?.status == ExecutionStatus.DONE }) return DayStatus.DONE
    if (records.all { it != null && it.status != ExecutionStatus.DRAFT }) return DayStatus.ENDED
    if (records.any { it != null }) return DayStatus.ACTIVE
    return DayStatus.PENDING
}

fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

fun Exercise.freshCopy() = copy(id = newId(), temporary = false)
