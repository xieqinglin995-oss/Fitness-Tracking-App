package com.xieqinglin.fitness.domain

import java.time.LocalDate

data class CompletionUndo(val before: List<Execution>, val after: List<Execution>)
data class CompletionResult(val data: FitnessData, val undo: CompletionUndo)

object TrainingCompletion {
    fun eligible(data: FitnessData, date: String): List<Exercise> = data.plan(date)?.exercises.orEmpty().filter {
        data.execution(it.id)?.status.let { status -> status == null || status == ExecutionStatus.DRAFT }
    }

    fun complete(data: FitnessData, date: String, selected: Set<String>, today: LocalDate = LocalDate.now()): CompletionResult {
        Rules.pastDate(date, today)
        require(selected.isNotEmpty()) { "请至少选择一个项目" }
        val plan = requireNotNull(data.plan(date)) { "当天没有训练计划" }
        require(selected.all { id -> plan.exercises.any { it.id == id } }) { "训练项目已变更，请重新选择" }
        val items = eligible(data, date).filter { it.id in selected }
        require(items.isNotEmpty()) { "所选项目已经完成或跳过，无需重复操作" }
        val before = items.mapNotNull { data.execution(it.id) }
        val after = items.map { item ->
            data.execution(item.id)?.copy(status = ExecutionStatus.DONE, updatedAt = now())
                ?: Execution(date = date, exerciseId = item.id, nameSnapshot = item.name,
                    kindSnapshot = item.kind, targetSnapshot = item.target, actual = item.target, status = ExecutionStatus.DONE)
        }
        val affected = after.map { it.exerciseId }.toSet()
        return CompletionResult(data.copy(executions = data.executions.filterNot { it.exerciseId in affected } + after), CompletionUndo(before, after))
    }

    fun undo(data: FitnessData, token: CompletionUndo): FitnessData {
        require(token.after.isNotEmpty() && token.after.all { data.execution(it.exerciseId) == it }) {
            "这些记录已经修改，无法批量撤销；请逐项修正"
        }
        val affected = token.after.map { it.exerciseId }.toSet()
        return data.copy(executions = data.executions.filterNot { it.exerciseId in affected } + token.before)
    }
}

fun FitnessData.previousExecution(item: Exercise, beforeDate: String): Execution? = executions
    .filter { it.date < beforeDate && it.status == ExecutionStatus.DONE &&
        it.kindSnapshot == item.kind && it.nameSnapshot.trim().equals(item.name.trim(), ignoreCase = true) }
    .maxWithOrNull(compareBy<Execution> { it.date }.thenBy { it.updatedAt })
