package com.jeanwest.reader.checkIn

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class)
class CheckInActivityTest {
    private val epcs = mutableListOf(
        "30C01901C99103C000003D22",
        "30C019400055A140000ABF29",
        "30C01940005D6980000ABF28",
        "30C01940005D6A00000ABF24",
        "30C01940006310C0000ABF26",
        "30C01940007F3C80000ABF22",
        "30C01940007F3C80000ABF27",
        "30C01940009D7D8000000004",
        "30C01940009D7D8000000007",
        "30C01940009D7D8000000008",
        "30C01940009D7D8000000009",
        "30C0194000DC20C000017A1C",
        "30C41901C97349C0000003B1",
        "30C41901C99378000000B19C"
    )

    val barcodes = mutableListOf(
        "11581831J-2520-36",
        "54822101J-8030-XL",
        "54822102J-8010-M",
        "54822102J-8580-L",
        "62822105J-8730-L",
        "11852002J-2430-F",
        "64822109J-8010-F",
        "64822109J-8010-F",
        "64822109J-8010-F",
        "91273501-8420-S-1",
        "91273501-8420-S-1",
        "91273501-8420-S-1",
        "11852002J-2430-F",
        "01551701J-2530-XL",
        "01631542-8511-140-1",
    )

    @get:Rule
    val checkInActivity = createAndroidComposeRule<CheckInActivity>()

    // open a file and check results
    @Test
    fun countActivityTest1() {

        val shortagesNumber = 178
        val shortageCodesNumber = 63
        val additionalNumber = 0
        val additionalCodesNumber = 0

        MainActivity.token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        checkInActivity.waitForIdle()
        Thread.sleep(1000)
        checkInActivity.waitForIdle()

        checkInActivity.onNodeWithText("کسری ها(یکتا): $shortagesNumber($shortageCodesNumber)").assertExists()
        checkInActivity.onNodeWithText("اضافی ها(یکتا): $additionalNumber($additionalCodesNumber)").assertExists()
        checkInActivity.onNodeWithText("تعداد اسکن شده: 0").assertExists()
    }

    @Test
    fun checkInActivityTest2() {
        val shortagesNumber = 0

        val shortageCodesNumber = 0
        val additionalNumber = 1
        val additionalCodesNumber = 1
        val scannedNumber = 14

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        checkInActivity.waitForIdle()
        Thread.sleep(1000)
        checkInActivity.waitForIdle()

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        epcs.forEach {
            val uhfTagInfo = UHFTAGInfo()
            uhfTagInfo.epc = it
            RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
        }

        checkInActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        checkInActivity.waitForIdle()

        checkInActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        checkInActivity.waitForIdle()

        Thread.sleep(2000)
        checkInActivity.waitForIdle()
        Thread.sleep(2000)

        checkInActivity.onNodeWithText("کسری ها(یکتا): $shortagesNumber($shortageCodesNumber)")
            .assertExists()
        checkInActivity.onNodeWithText("اضافی ها(یکتا): $additionalNumber($additionalCodesNumber)")
            .assertExists()
        checkInActivity.onNodeWithText("تعداد اسکن شده: $scannedNumber").assertExists()
    }

    //check filters
    @Test
    fun checkInActivityTest3() {

        val filter = "اضافی ها"

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        checkInActivity.waitForIdle()
        Thread.sleep(1000)
        checkInActivity.waitForIdle()

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        epcs.forEach {
            val uhfTagInfo = UHFTAGInfo()
            uhfTagInfo.epc = it
            RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
        }

        checkInActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        checkInActivity.waitForIdle()

        checkInActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        checkInActivity.waitForIdle()

        Thread.sleep(2000)
        checkInActivity.waitForIdle()
        Thread.sleep(2000)

        checkInActivity.onNodeWithTag("checkInFilterDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText(filter).performClick()
        checkInActivity.waitForIdle()

        checkInActivity.onNodeWithText("11531052J-2000-L").assertExists()

        Thread.sleep(5000)
    }
}