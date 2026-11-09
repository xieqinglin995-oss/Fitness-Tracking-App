package com.xieqinglin.fitness.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xieqinglin.fitness.MainActivity
import com.xieqinglin.fitness.FitnessApplication
import com.xieqinglin.fitness.domain.FitnessData
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
        compose.waitUntil(10_000) { compose.onAllNodesWithText("80.25 kg").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("编辑今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("80.25 kg").assertExists()
    }

    @Test fun invalidWeightKeepsInputOpenWithAnError() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("记录今日体重").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("记录今日体重").performClick()
        compose.onNode(hasSetTextAction() and hasText("体重 · kg")).performTextInput("0")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("数值必须大于 0").assertExists()
        compose.onNode(hasSetTextAction() and hasText("0")).assertExists()
    }
}
