package com.jeanwest.reader.activities

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    var activity = createAndroidComposeRule<MainActivity>()

    @Test
    fun test() {
        waitForFinishLoading()
        if (MainActivity.token != "") {
            activity.onNodeWithTag("account").performClick()
            activity.waitForIdle()
            activity.onNodeWithText("خروج از حساب").performClick()
            activity.waitForIdle()
            restart()
        }
        activity.onAllNodesWithTag("TextField").assertCountEquals(2)
        activity.onAllNodesWithTag("TextField")[0].performTextClearance()
        activity.onAllNodesWithTag("TextField")[0].performTextInput("4016")
        activity.onAllNodesWithTag("TextField")[1].performTextClearance()
        activity.onAllNodesWithTag("TextField")[1].performTextInput("40164016")
        activity.onNodeWithText("ورود").performClick()
        waitForFinishLoading()
        activity.onNodeWithTag("account").assertExists()
        restart()
        activity.onNodeWithTag("account").assertExists()
        activity.onNodeWithTag("account").performClick()
        activity.waitForIdle()
        activity.onNodeWithText("خروج از حساب").performClick()
        activity.waitForIdle()
        activity.onAllNodesWithTag("TextField").assertCountEquals(2)
        restart()
        activity.onAllNodesWithTag("TextField").assertCountEquals(2)
    }

    private fun restart() {

        activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()
    }

    private fun waitForFinishLoading() {
        activity.waitForIdle()
        while (activity.activity.loading) {
            Thread.sleep(200)
            activity.waitForIdle()
        }
    }
}