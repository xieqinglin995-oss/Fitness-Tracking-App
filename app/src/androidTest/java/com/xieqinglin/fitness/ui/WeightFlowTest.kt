package com.xieqinglin.fitness.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xieqinglin.fitness.MainActivity
import com.xieqinglin.fitness.FitnessApplication
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WeightFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun clearTestData() = runBlocking {
        (compose.activity.application as FitnessApplication).repository.mutate { FitnessData() }
    }

    @Test fun recordsWeightAndRecreatesActivityWithoutLosingIt() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("记录今日体重").performClick()
        compose.onNode(hasSetTextAction() and hasText("体重 · kg")).performTextInput("80.25")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("80.25").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("编辑今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("80.25").assertExists()
    }

    @Test fun invalidWeightKeepsInputOpenWithAnError() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("记录今日体重").performClick()
        compose.onNode(hasSetTextAction() and hasText("体重 · kg")).performTextInput("0")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("数值必须大于 0").assertExists()
        compose.onNode(hasSetTextAction() and hasText("0")).assertExists()
    }

    @Test fun jinInputConvertsWithoutDriftAndRemembersBodyUnitOnly() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("记录今日体重").performClick()
        compose.onNodeWithText("斤").performClick()
        compose.onNode(hasSetTextAction() and hasText("体重 · 斤")).performTextInput("160.25")
        compose.onNodeWithText("公斤 kg").performClick()
        compose.onNodeWithText("斤").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("160.25").fetchSemanticsNodes().isNotEmpty() }
        val repo = (compose.activity.application as FitnessApplication).repository
        val saved = runBlocking { repo.snapshot() }
        org.junit.Assert.assertEquals(80125L, saved.weights.single().grams)
        org.junit.Assert.assertEquals(WeightUnit.JIN, saved.settings.unit)
        org.junit.Assert.assertEquals(WeightUnit.KG, saved.settings.loadUnit)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("编辑今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("编辑今日体重").performClick()
        compose.onNode(hasSetTextAction() and hasText("体重 · 斤")).assertExists()
    }

    @Test fun batchCompletionAndUndoPreserveExistingActualAndSkippedValues() {
        val date = java.time.LocalDate.now().toString()
        val items = (1..4).map { Exercise(name = "验收动作 $it", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0)) }
        fun record(i: Int, status: ExecutionStatus) = Execution(date = date, exerciseId = items[i].id, nameSnapshot = items[i].name,
            kindSnapshot = items[i].kind, targetSnapshot = items[i].target, actual = Values(seconds = 480.0), status = status)
        val data = FitnessData(plans = listOf(DailyPlan(date = date, exercises = items)),
            executions = listOf(record(1, ExecutionStatus.DRAFT), record(2, ExecutionStatus.DONE), record(3, ExecutionStatus.SKIPPED)))
        val repo = (compose.activity.application as FitnessApplication).repository
        runBlocking { repo.mutate { data } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("批量完成").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("批量完成").performClick()
        compose.onNodeWithText("完成所选（2 项）").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().execution(items[0].id)?.status == ExecutionStatus.DONE } }
        val completed = runBlocking { repo.snapshot() }
        org.junit.Assert.assertEquals(480.0, completed.execution(items[1].id)!!.actual.seconds!!, .001)
        org.junit.Assert.assertEquals(data.execution(items[3].id), completed.execution(items[3].id))
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().executions.toSet() == data.executions.toSet() } }
    }

    @Test fun focusedTrainingPersistsConfirmedSetsAcrossRecreation() {
        val date = java.time.LocalDate.now().toString()
        val item = Exercise(name = "深蹲", kind = ExerciseKind.STRENGTH, target = Values(sets = listOf(SetValue(10, 0), SetValue(10, 0))))
        val repo = (compose.activity.application as FitnessApplication).repository
        runBlocking { repo.mutate { FitnessData(plans = listOf(DailyPlan(date = date, exercises = listOf(item)))) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("开始训练").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("开始训练").performScrollTo().performClick()
        compose.onNodeWithContentDescription("完成第1组").performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().execution(item.id)?.completedSets == listOf(0) } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("专注训练").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("完成第1组").assertIsOn()
        compose.onNodeWithContentDescription("完成第2组").performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().execution(item.id)?.status == ExecutionStatus.DONE } }
    }

    @Test fun trendsRangeIsRetainedAfterVisitingSettings() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("趋势").performClick()
        compose.onNodeWithText("近 90 天").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithContentDescription("返回上一页").performClick()
        compose.onNodeWithText("近 90 天").assertIsSelected()
    }
}
