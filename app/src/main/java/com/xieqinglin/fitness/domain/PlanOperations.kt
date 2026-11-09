package com.xieqinglin.fitness.domain

import java.time.LocalDate

enum class ConflictChoice { SKIP, REPLACE, APPEND }

object PlanOperations {
    fun apply(data: FitnessData, date: String, items: List<Exercise>, name: String, choice: ConflictChoice): FitnessData {
        Rules.date(date)
        val existing = data.plan(date)
        if (existing != null && choice == ConflictChoice.SKIP) return data
        val protected = existing?.exercises.orEmpty().filter { data.execution(it.id) != null }
        val kept = if (choice == ConflictChoice.APPEND) existing?.exercises.orEmpty() else protected
        val plan = DailyPlan(existing?.id ?: newId(), date, name.ifBlank { "当日训练" }, false, kept + items.map { it.freshCopy() })
        return data.copy(plans = data.plans.filterNot { it.date == date } + plan)
    }

    fun rest(data: FitnessData, date: String, rest: Boolean): FitnessData {
        val existing = data.plan(date)
        require(existing?.exercises.orEmpty().none { data.execution(it.id) != null }) { "当天已有实际记录，请先明确删除实际记录后再设为休息日" }
        val plans = data.plans.filterNot { it.date == date }
        return data.copy(plans = if (rest) plans + DailyPlan(existing?.id ?: newId(), date, rest = true) else plans)
    }

    fun copyWeek(data: FitnessData, sourceMonday: LocalDate, targetMonday: LocalDate, choices: Map<String, ConflictChoice>): FitnessData {
        require(sourceMonday == weekStart(sourceMonday) && targetMonday == weekStart(targetMonday)) { "请选择目标周的周一" }
        require(sourceMonday != targetMonday) { "目标周不能与来源周相同" }
        val sources = (0L..6L).mapNotNull { offset -> data.plan(sourceMonday.plusDays(offset).toString())?.let { offset to it } }
        return sources.fold(data) { result, (offset, source) ->
            val date = targetMonday.plusDays(offset).toString()
            val choice = choices[date] ?: ConflictChoice.SKIP
            if (result.plan(date) != null && choice == ConflictChoice.SKIP) result
            else if (source.rest) rest(result, date, true)
            else apply(result, date, source.exercises, source.name, choice)
        }
    }

    fun move(data: FitnessData, sourceDate: String, targetDate: String, choice: ConflictChoice): FitnessData {
        require(sourceDate != targetDate) { "请选择不同的目标日期" }
        val source = requireNotNull(data.plan(sourceDate)) { "来源日期没有计划" }
        if (data.plan(targetDate) != null && choice == ConflictChoice.SKIP) return data
        val hasActual = source.exercises.any { data.execution(it.id) != null }
        val pending = if (hasActual) source.exercises.filter { data.execution(it.id)?.status !in listOf(ExecutionStatus.DONE, ExecutionStatus.SKIPPED) } else source.exercises
        require(source.rest || pending.isNotEmpty()) { "没有尚未完成的项目可改期" }
        val result = if (source.rest) rest(data, targetDate, true) else apply(data, targetDate, pending, source.name, choice)
        return if (hasActual) result else result.copy(plans = result.plans.filterNot { it.date == sourceDate })
    }
}
