package com.jeanwest.reader.checkIn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import org.junit.Rule
import org.junit.Test

class GetBarcodesByCheckInNumberActivityTest {


    @get:Rule
    var getBarcodesByCheckInNumberActivityTest =
        createAndroidComposeRule<GetBarcodesByCheckInNumberActivity>()

    // enter two check-in-number and check statistics
    @Test
    fun getBarcodesByCheckInNumberActivityTest1() {
        val productCode0 = "114028"
        val productCode1 = "114052"

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        getBarcodesByCheckInNumberActivityTest.waitForIdle()

        getBarcodesByCheckInNumberActivityTest.onNodeWithTag("GetBarcodesByCheckInNumberTextField")
            .performTextClearance()
        getBarcodesByCheckInNumberActivityTest.onNodeWithTag("GetBarcodesByCheckInNumberTextField")
            .performTextInput(productCode0)
        getBarcodesByCheckInNumberActivityTest.onNodeWithTag("GetBarcodesByCheckInNumberTextField")
            .performImeAction()
        getBarcodesByCheckInNumberActivityTest.waitForIdle()

        Thread.sleep(2000)
        getBarcodesByCheckInNumberActivityTest.waitForIdle()
        Thread.sleep(2000)

        getBarcodesByCheckInNumberActivityTest.onNodeWithTag("GetBarcodesByCheckInNumberTextField")
            .performTextClearance()
        getBarcodesByCheckInNumberActivityTest.onNodeWithTag("GetBarcodesByCheckInNumberTextField")
            .performTextInput(productCode1)
        getBarcodesByCheckInNumberActivityTest.onNodeWithTag("GetBarcodesByCheckInNumberTextField")
            .performImeAction()
        getBarcodesByCheckInNumberActivityTest.waitForIdle()

        Thread.sleep(2000)
        getBarcodesByCheckInNumberActivityTest.waitForIdle()
        Thread.sleep(2000)

        getBarcodesByCheckInNumberActivityTest.onNodeWithText("حواله: 114028").assertExists()
        getBarcodesByCheckInNumberActivityTest.onNodeWithText("حواله: 114052").assertExists()
        getBarcodesByCheckInNumberActivityTest.onNodeWithText("تعداد کالاها: 5").assertExists()
        getBarcodesByCheckInNumberActivityTest.onNodeWithText("تعداد کالاها: 131").assertExists()
        getBarcodesByCheckInNumberActivityTest.waitForIdle()
    }
}
