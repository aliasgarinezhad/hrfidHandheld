package com.jeanwest.reader.checkIn

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test


@OptIn(ExperimentalCoilApi::class)
class CheckInActivityTest {

    @get:Rule
    val checkInActivity = createAndroidComposeRule<CheckInActivity>()

    //open a check-in, scan some items, check filters
    @Test
    fun checkInActivityTest1() {

        val fileNumber = 15
        val shortagesNumber = 2
        val additionalNumber = 1
        val scannedNumber = 14
        val filters = mutableListOf("اضافی", "کسری", "تایید شده")

        start()
        clearUserData()
        importTestFile()
        restart()

        checkInActivity.onNodeWithText("کسری: $fileNumber").assertExists()
        checkInActivity.onNodeWithText("اضافی: 0").assertExists()
        checkInActivity.onNodeWithText("تعداد اسکن شده: 0").assertExists()
        checkInActivity.onAllNodesWithTag("CheckInActivityLazyColumnItem")[0].assertExists()

        epcScan()

        checkInActivity.onNodeWithText("کسری: $shortagesNumber").assertExists()
        checkInActivity.onNodeWithText("اضافی: $additionalNumber").assertExists()
        checkInActivity.onNodeWithText("تعداد اسکن شده: $scannedNumber").assertExists()
        checkInActivity.onAllNodesWithTag("CheckInActivityLazyColumnItem")[0].assertExists()

        checkInActivity.onNodeWithTag("checkInFilterDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText(filters[0]).performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("91273501-8420-S-1").assertExists()
        checkInActivity.onAllNodesWithTag("CheckInActivityLazyColumnItem").assertCountEquals(1)


        checkInActivity.onNodeWithTag("checkInFilterDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText(filters[1]).performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("11852002J-2430-F").assertExists()
        checkInActivity.onNodeWithText("64822109J-8010-F").assertExists()
        checkInActivity.onAllNodesWithTag("CheckInActivityLazyColumnItem").assertCountEquals(2)

        checkInActivity.onNodeWithTag("checkInFilterDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText(filters[2]).performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("11852002J-2430-F").assertDoesNotExist()
        checkInActivity.onNodeWithText("64822109J-8010-F").assertDoesNotExist()
        checkInActivity.onNodeWithText("91273501-8420-S-1").assertDoesNotExist()
        checkInActivity.onAllNodesWithTag("CheckInActivityLazyColumnItem")[0].assertExists()
    }

    private fun importTestFile() {
        checkInActivity.activity.intent.putExtra(
            "CheckInFileBarcodeTable",
            JSONArray(barcodes).toString()
        )
    }

    private fun epcScan() {
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
    }

    private fun restart() {
        checkInActivity.activity.runOnUiThread {
            checkInActivity.activity.recreate()
        }

        checkInActivity.waitForIdle()
        Thread.sleep(4000)
        checkInActivity.waitForIdle()
    }

    private fun start() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"
    }

    private fun clearUserData() {

        checkInActivity.onNodeWithTag("CheckInTestTag").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("بله").performClick()
        checkInActivity.waitForIdle()

        checkInActivity.onNodeWithTag("CheckInTestTag").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("بله").performClick()
        checkInActivity.waitForIdle()
    }

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

    private val barcodes = mutableListOf(
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
}