package com.jeanwest.reader.count

import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.sharedClassesAndFiles.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalCoilApi

class CountActivityTest {

    @get:Rule
    var countActivity = createAndroidComposeRule<CountActivity>()

    //open an excel file, scan some epcs, check filters
    @Test
    fun countActivityTest1() {

        val fileNumber = 15
        val shortagesNumber = 2
        val additionalNumber = 1
        val scannedNumber = 14
        val filters = mutableListOf("اضافی", "کسری", "تایید شده")

        start()
        clearUserData()
        importTestFile()
        restart()

        countActivity.onNodeWithText("کسری: $fileNumber").assertExists()
        countActivity.onNodeWithText("اضافی: 0").assertExists()
        countActivity.onNodeWithText("اسکن شده: 0").assertExists()

        epcScan()

        countActivity.onNodeWithText("کسری: $shortagesNumber").assertExists()
        countActivity.onNodeWithText("اضافی: $additionalNumber").assertExists()
        countActivity.onNodeWithText("اسکن شده: $scannedNumber").assertExists()


        countActivity.onNodeWithTag("CountActivityFilterDropDownList").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText(filters[0]).performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("91273501-8420-S-1").assertExists()
        countActivity.onAllNodesWithTag("items").assertCountEquals(1)


        countActivity.onNodeWithTag("CountActivityFilterDropDownList").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText(filters[1]).performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("11852002J-2430-F").assertExists()
        countActivity.onNodeWithText("64822109J-8010-F").assertExists()
        countActivity.onAllNodesWithTag("items").assertCountEquals(2)

        countActivity.onNodeWithTag("CountActivityFilterDropDownList").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText(filters[2]).performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("11852002J-2430-F").assertDoesNotExist()
        countActivity.onNodeWithText("64822109J-8010-F").assertDoesNotExist()
        countActivity.onNodeWithText("91273501-8420-S-1").assertDoesNotExist()
        countActivity.onAllNodesWithTag("items")[0].assertExists()
    }

    private fun importTestFile() {
        countActivity.activity.excelBarcodes.addAll(barcodes)
        countActivity.activity.saveToMemory()
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

        countActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        countActivity.waitForIdle()

        countActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        countActivity.waitForIdle()

        Thread.sleep(2000)
        countActivity.waitForIdle()
        Thread.sleep(2000)
    }

    private fun restart() {
        countActivity.activity.runOnUiThread {
            countActivity.activity.recreate()
        }

        countActivity.waitForIdle()
        Thread.sleep(4000)
        countActivity.waitForIdle()
    }

    private fun start() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        countActivity.waitForIdle()
        Thread.sleep(4000)
        countActivity.waitForIdle()
    }

    private fun clearUserData() {
        countActivity.onNodeWithTag("CountActivityClearButton").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("بله").performClick()
        countActivity.waitForIdle()

        countActivity.onNodeWithTag("CountActivityClearButton").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("بله").performClick()
        countActivity.waitForIdle()
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

