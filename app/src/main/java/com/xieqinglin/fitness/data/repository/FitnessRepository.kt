package com.xieqinglin.fitness.data.repository

import androidx.room.withTransaction
import com.xieqinglin.fitness.backup.BackupCodec
import com.xieqinglin.fitness.data.local.FitnessDatabase
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FitnessRepository(private val database: FitnessDatabase) {
    val data: Flow<FitnessData> = database.invalidationTracker
        .createFlow("weights", "plans", "items", "actuals", "templates", "settings")
        .map { database.dao().snapshot() }

    suspend fun snapshot(): FitnessData = database.dao().snapshot()

    suspend fun mutate(transform: (FitnessData) -> FitnessData) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val previous = database.dao().snapshot()
            val next = transform(previous)
            Rules.validate(next)
            database.dao().write(next, previous)
        }
    }

    suspend fun saveWeight(entry: WeightEntry, originalId: String?, overwrite: Boolean, unit: WeightUnit? = null) = mutate { data ->
        val conflict = data.weights.find { it.date == entry.date && it.id != originalId }
        require(conflict == null || overwrite) { "该日期已有体重，请确认覆盖" }
        data.copy(weights = data.weights.filterNot { it.id == originalId || it.date == entry.date } + entry,
            settings = if (unit == null) data.settings else data.settings.copy(unit = unit))
    }

    suspend fun completeTraining(date: String, ids: Set<String>): CompletionUndo {
        var token: CompletionUndo? = null
        mutate { current -> TrainingCompletion.complete(current, date, ids).let { token = it.undo; it.data } }
        return requireNotNull(token)
    }

    suspend fun undoCompletion(token: CompletionUndo) = mutate { TrainingCompletion.undo(it, token) }

    suspend fun saveExercise(date: String, exercise: Exercise) = mutate { data ->
        val plan = data.plan(date) ?: DailyPlan(date = date)
        val existing = plan.exercises.find { it.id == exercise.id }
        require(existing == null || data.execution(exercise.id) == null || existing.kind == exercise.kind) { "已有实际记录的项目不能改变记录方式，请新增项目" }
        val items = if (existing == null) plan.exercises + exercise else plan.exercises.map { if (it.id == exercise.id) exercise else it }
        data.copy(plans = data.plans.filterNot { it.date == date } + plan.copy(rest = false, exercises = items))
    }

    suspend fun saveExecution(date: String, item: Exercise, actual: Values, status: ExecutionStatus, reason: String, completedSets: List<Int>? = null) = mutate { data ->
        Rules.pastDate(date)
        val savedItem = data.plan(date)?.exercises?.find { it.id == item.id }
        require(savedItem != null) { "项目已变更，请重新打开" }
        val old = data.execution(item.id)
        val record = Execution(old?.id ?: newId(), date, item.id, old?.nameSnapshot ?: item.name,
            old?.kindSnapshot ?: item.kind, old?.targetSnapshot ?: item.target, actual, status, reason,
            completedSets = when {
                item.kind != ExerciseKind.STRENGTH || status == ExecutionStatus.SKIPPED -> emptyList()
                status == ExecutionStatus.DONE -> actual.sets.indices.toList()
                else -> (completedSets ?: if (old?.status == ExecutionStatus.DRAFT && old.actual == actual) old.completedSets else emptyList())
                    .filter { it in actual.sets.indices }.distinct().sorted()
            })
        data.copy(executions = data.executions.filterNot { it.exerciseId == item.id } + record)
    }

    suspend fun restore(text: String) = withContext(Dispatchers.IO) {
        val backup = BackupCodec.decode(text)
        database.withTransaction { database.dao().write(backup.data, null) }
    }
}
