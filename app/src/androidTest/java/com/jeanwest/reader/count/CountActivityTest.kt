package com.jeanwest.reader.count

import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalCoilApi

class CountActivityTest {

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
    var countActivity = createAndroidComposeRule<CountActivity>()


    //open an excel file and check numbers and filters
    @Test
    fun countActivityTest1() {

        val shortagesNumber = 15
        val shortageCodesNumber = 10
        val additionalNumber = 0
        val additionalCodesNumber = 0

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        countActivity.waitForIdle()
        Thread.sleep(1000)
        countActivity.waitForIdle()

        countActivity.onNodeWithText("کسری ها(یکتا): $shortagesNumber($shortageCodesNumber)")
            .assertExists()
        countActivity.onNodeWithText("اضافی ها(یکتا): $additionalNumber($additionalCodesNumber)")
            .assertExists()
        countActivity.onNodeWithText("تعداد اسکن شده: 0").assertExists()
    }

    //scan some items and check numbers
    @Test
    fun countActivityTest2() {

        val shortagesNumber = 2

        val shortageCodesNumber = 2
        val additionalNumber = 1
        val additionalCodesNumber = 1
        val scannedNumber = 14

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        countActivity.waitForIdle()
        Thread.sleep(1000)
        countActivity.waitForIdle()

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

        countActivity.onNodeWithText("کسری ها(یکتا): $shortagesNumber($shortageCodesNumber)")
            .assertExists()
        countActivity.onNodeWithText("اضافی ها(یکتا): $additionalNumber($additionalCodesNumber)")
            .assertExists()
        countActivity.onNodeWithText("تعداد اسکن شده: $scannedNumber").assertExists()
    }
}

