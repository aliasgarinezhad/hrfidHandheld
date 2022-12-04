package com.jeanwest.reader.activities

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceRegisterTest {
    @get:Rule
    var activity = createAndroidComposeRule<DeviceRegister>()

    @Test
    fun test() {
        waitForFinishLoading()
        activity.onAllNodesWithTag("TextField").assertCountEquals(2)
        activity.onAllNodesWithTag("TextField")[0].performTextClearance()
        activity.onAllNodesWithTag("TextField")[0].performTextInput("rfid")
        activity.onAllNodesWithTag("TextField")[1].performTextClearance()
        activity.onAllNodesWithTag("TextField")[1].performTextInput("rfid123456")
        activity.onNodeWithText("ورود").performClick()
        waitForFinishLoading()
        activity.onAllNodesWithTag("TextField").assertCountEquals(1)
        activity.onNodeWithTag("FilterDropDownList").assertExists()
        activity.onNodeWithTag("FilterDropDownList").assertTextContains("تهران ايران مال 1")
        activity.onNodeWithTag("back").performClick()
        activity.waitForIdle()
        activity.onAllNodesWithTag("TextField").assertCountEquals(2)
    }

    private fun waitForFinishLoading() {
        activity.waitForIdle()
        while (activity.activity.loading) {
            Thread.sleep(200)
            activity.waitForIdle()
        }
    }
}