package com.jeanwest.reader.checkIn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import org.junit.Rule
import org.junit.Test

class GetCheckInPropertiesActivityTest {


    @get:Rule
    var getCheckInPropertiesActivityTest =
        createAndroidComposeRule<GetCheckInPropertiesActivity>()

    // enter two check-in-number and check statistics
    @Test
    fun getBarcodesByCheckInNumberActivityTest1() {
        val productCode0 = "114028"
        val productCode1 = "114052"

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        getCheckInPropertiesActivityTest.waitForIdle()

        getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
            .performTextClearance()
        getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
            .performTextInput(productCode0)
        getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
            .performImeAction()
        getCheckInPropertiesActivityTest.waitForIdle()

        Thread.sleep(2000)
        getCheckInPropertiesActivityTest.waitForIdle()
        Thread.sleep(2000)

        getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
            .performTextClearance()
        getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
            .performTextInput(productCode1)
        getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
            .performImeAction()
        getCheckInPropertiesActivityTest.waitForIdle()

        Thread.sleep(2000)
        getCheckInPropertiesActivityTest.waitForIdle()
        Thread.sleep(2000)

        getCheckInPropertiesActivityTest.onNodeWithText("حواله: 114028").assertExists()
        getCheckInPropertiesActivityTest.onNodeWithText("حواله: 114052").assertExists()
        getCheckInPropertiesActivityTest.onNodeWithText("تعداد کالاها: 5").assertExists()
        getCheckInPropertiesActivityTest.onNodeWithText("تعداد کالاها: 131").assertExists()
        getCheckInPropertiesActivityTest.waitForIdle()
    }
}
