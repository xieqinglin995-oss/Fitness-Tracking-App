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
    @Test fun bulkCompletionDoesNotReplaceUnfiledTimerWithTargetValues() {
        val date = java.time.LocalDate.now().toString()
        val item = Exercise(name = "计时有氧", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0))
        val repo = (compose.activity.application as FitnessApplication).repository
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.xieqinglin.fitness.presentation.FitnessViewModel::class.java]
        runBlocking { repo.mutate { FitnessData(plans = listOf(DailyPlan(date = date, exercises = listOf(item)))) } }
        try {
            compose.runOnUiThread { vm.startActivity(item.id); vm.pauseActivity(); vm.completeTraining(date, setOf(item.id)) }
            compose.waitUntil(10_000) { !vm.busy.value }
            org.junit.Assert.assertNull(runBlocking { repo.snapshot().execution(item.id) })
            org.junit.Assert.assertEquals(item.id, vm.activityTimer.value.exerciseId)
        } finally { compose.runOnUiThread { vm.clearActivity() } }
    }
    @Test fun movingFromStrengthToCardioShowsTheTimerAtTheTop() {
        val date = java.time.LocalDate.now().toString()
        val strength = Exercise(name = "力量", kind = ExerciseKind.STRENGTH, target = Values(sets = List(3) { SetValue(10, 0) }))
        val cardio = Exercise(name = "有氧", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0))
        val repo = (compose.activity.application as FitnessApplication).repository
        runBlocking { repo.mutate { FitnessData(plans = listOf(DailyPlan(date = date, exercises = listOf(strength, cardio)))) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("开始训练").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("开始训练").performClick()
        compose.onNodeWithTag("training-list").performScrollToNode(hasText("下一个动作"))
        compose.onNodeWithText("下一个动作").performClick()
        compose.onNodeWithText("开始正计时").assertIsDisplayed()
    }
    @Test fun ordinaryActualFormProtectsUnsavedInputOnBack() {
        val date = java.time.LocalDate.now().toString()
        val item = Exercise(name = "拉伸", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0))
        val repo = (compose.activity.application as FitnessApplication).repository
        runBlocking { repo.mutate { FitnessData(plans = listOf(DailyPlan(date = date, exercises = listOf(item)))) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("开始训练").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("填写实际 / 跳过"))
        compose.onNodeWithText("填写实际 / 跳过").performClick()
        compose.onNode(hasSetTextAction() and hasText("分钟")).performTextReplacement("21")
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        if (compose.onAllNodesWithText("放弃未保存的修改？").fetchSemanticsNodes().isEmpty()) instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("放弃未保存的修改？").assertExists()
        compose.onAllNodesWithText("取消").onLast().performClick()
        compose.onNode(hasSetTextAction() and hasText("21")).assertExists()
        org.junit.Assert.assertTrue(runBlocking { repo.snapshot().executions.isEmpty() })
    }

    @Test fun removingUnconfirmedMiddleSetKeepsLastConfirmedSetIdentity() {
        val date = java.time.LocalDate.now().toString()
        val item = Exercise(name = "深蹲", kind = ExerciseKind.STRENGTH,
            target = Values(sets = listOf(SetValue(10, 0, "a"), SetValue(12, 0, "b"), SetValue(8, 0, "c"))))
        val repo = (compose.activity.application as FitnessApplication).repository
        runBlocking { repo.mutate { FitnessData(plans = listOf(DailyPlan(date = date, exercises = listOf(item)))) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("开始训练").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("开始训练").performClick()
        compose.onNodeWithTag("training-list").performScrollToNode(hasContentDescription("完成第3组"))
        compose.onNodeWithContentDescription("完成第3组").performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().execution(item.id)?.completedSetIds == listOf("c") } }
        compose.onNodeWithTag("training-list").performScrollToNode(hasContentDescription("第2组快捷填写"))
        compose.onNodeWithContentDescription("第2组快捷填写").performClick()
        compose.onNodeWithTag("training-list").performScrollToNode(hasContentDescription("移除第2组"))
        compose.onNodeWithContentDescription("移除第2组").performClick()
        compose.onNodeWithTag("training-list").performScrollToNode(hasText("保存草稿"))
        compose.onNodeWithText("保存草稿").performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().execution(item.id)?.actual?.sets?.size == 2 } }
        val saved = runBlocking { repo.snapshot().execution(item.id)!! }
        org.junit.Assert.assertEquals(listOf("a", "c"), saved.actual.sets.map { it.id })
        org.junit.Assert.assertEquals(listOf(1), saved.completedSets)
        org.junit.Assert.assertEquals(listOf("c"), saved.completedSetIds)
    }

    @Test fun cardioTimerAndPartialEndNeverCompleteTheExerciseAutomatically() {
        val date = java.time.LocalDate.now().toString()
        val item = Exercise(name = "跑步", kind = ExerciseKind.DISTANCE, target = Values(seconds = 600.0, kilometers = 3.0))
        val repo = (compose.activity.application as FitnessApplication).repository
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.xieqinglin.fitness.presentation.FitnessViewModel::class.java]
        compose.runOnUiThread { vm.clearActivity() }
        runBlocking { repo.mutate { FitnessData(plans = listOf(DailyPlan(date = date, exercises = listOf(item)))) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("开始训练").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("开始训练").performClick()
        compose.onNodeWithText("开始正计时").performClick()
        compose.waitUntil(5_000) { vm.activityTimer.value.elapsed(android.os.SystemClock.elapsedRealtime()) >= 1_000 }
        compose.onNodeWithText("暂停运动").performClick()
        compose.onNodeWithTag("training-list").performScrollToNode(hasText("完成动作"))
        compose.onNodeWithText("完成动作").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("请先暂停并填入计时用时，或清除计时后手动记录").fetchSemanticsNodes().isNotEmpty() }
        org.junit.Assert.assertNull(runBlocking { repo.snapshot().execution(item.id) })
        compose.onNodeWithTag("training-list").performScrollToNode(hasText("填入用时"))
        compose.onNodeWithText("填入用时").performClick()
        compose.onNodeWithTag("training-list").performScrollToNode(hasText("保存草稿"))
        compose.onNodeWithText("保存草稿").performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().execution(item.id) != null } }
        val actual = runBlocking { repo.snapshot().execution(item.id)!! }
        org.junit.Assert.assertEquals(ExecutionStatus.DRAFT, actual.status)
        org.junit.Assert.assertTrue(actual.actual.seconds!! in 1.0..10.0)
        compose.onNodeWithTag("training-list").performScrollToNode(hasText("结束本次训练"))
        compose.onNodeWithText("结束本次训练").performClick()
        compose.onNodeWithText("保留部分并结束").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { repo.snapshot().settings.sessions.isNotEmpty() } }
        org.junit.Assert.assertEquals(ExecutionStatus.DRAFT, runBlocking { repo.snapshot().execution(item.id)!!.status })
    }
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
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("training-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("完成第1组").performScrollTo().assertIsOn()
        compose.onNodeWithContentDescription("完成第2组").performScrollTo().performClick()
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
