package com.jeanwest.reader.checkOut

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.sharedClassesAndFiles.Barcode2D
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class)
class CheckOutActivityTest {

    @get:Rule
    var checkOutActivity = createAndroidComposeRule<CheckOutActivity>()

    @Test
    fun checkOutActivityTest1() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        //clearUserData()

        checkOutActivity.onNodeWithTag("scanTypeDropDownList").performClick()
        checkOutActivity.waitForIdle()
        checkOutActivity.onNodeWithText("RFID").performClick()
        checkOutActivity.waitForIdle()
        epcScan()

        checkOutActivity.onNodeWithText(checkOutActivity.activity.scannedProducts[0].KBarCode)
            .assertExists()
        checkOutActivity.onNodeWithText(checkOutActivity.activity.scannedProducts[1].KBarCode)
            .assertExists()
        checkOutActivity.onNodeWithText(checkOutActivity.activity.scannedProducts[2].KBarCode)
            .assertExists()
        checkOutActivity.onNodeWithText(checkOutActivity.activity.scannedProducts[3].KBarCode)
            .assertExists()

        checkOutActivity.activity.scannedProducts.forEach {
            if (it.KBarCode == "11852002J-2430-F") {
                if (!(it.scannedNumber == 1 && it.scannedEPCs[0] == "30C0194000DC20C000017A1C")) {
                    assert(false)
                }
            }
        }

        checkOutActivity.onNodeWithTag("scanTypeDropDownList").performClick()
        checkOutActivity.waitForIdle()
        checkOutActivity.onNodeWithText("بارکد").performClick()
        checkOutActivity.waitForIdle()
        barcodeScan()

        checkOutActivity.onNodeWithText("اسکن شده: ${barcodes.size + epcs.size}").assertExists()

        checkOutActivity.activity.scannedProducts.forEach {
            if (it.KBarCode == "11852002J-2430-F") {
                if (!(it.scannedNumber == 3 && it.scannedEPCs[0] == "30C0194000DC20C000017A1C")) {
                    assert(false)
                }
            }
        }

        checkOutActivity.activity.scannedProducts.forEach {
            if (it.KBarCode == "54822102J-8010-L") {
                if (!(it.scannedNumber == 1 && it.scannedEPCs.isEmpty())) {
                    assert(false)
                }
            }
        }

        val removeItem1 = checkOutActivity.activity.scannedProducts[0].KBarCode
        val removeItem2 = checkOutActivity.activity.scannedProducts[1].KBarCode
        checkOutActivity.onNodeWithText(checkOutActivity.activity.scannedProducts[0].KBarCode)
            .performTouchInput {
                longClick()
            }
        checkOutActivity.waitForIdle()
        checkOutActivity.onNodeWithText(checkOutActivity.activity.scannedProducts[1].KBarCode)
            .performClick()
        checkOutActivity.waitForIdle()
        checkOutActivity.onNodeWithText("پاک کردن").performClick()
        checkOutActivity.waitForIdle()
        checkOutActivity.onNodeWithText("بله").performClick()
        checkOutActivity.waitForIdle()

        Thread.sleep(5000)
        checkOutActivity.waitForIdle()

        checkOutActivity.onNodeWithText(removeItem1).assertDoesNotExist()
        checkOutActivity.onNodeWithText(removeItem2).assertDoesNotExist()
    }

    private fun barcodeScan() {

        for (i in 0 until barcodes.size) {
            Barcode2D.barcode = barcodes[i]

            checkOutActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
            checkOutActivity.waitForIdle()
            Thread.sleep(2000)
            checkOutActivity.waitForIdle()
        }
        Thread.sleep(10000)
        checkOutActivity.waitForIdle()
    }

    private fun epcScan() {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""

        epcs.forEach {
            val scannedUhfTagInfo = UHFTAGInfo()
            scannedUhfTagInfo.epc = it
            RFIDWithUHFUART.uhfTagInfo.add(scannedUhfTagInfo)
        }

        checkOutActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        checkOutActivity.waitForIdle()
        Thread.sleep(2000)
        checkOutActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        Thread.sleep(10000)
        checkOutActivity.waitForIdle()

    }

    /*private fun clearUserData() {

        checkOutActivity.activity.scannedProducts.forEach {
            checkOutActivity.activity.signedProductCodes.add(it.KBarCode)
        }
        checkOutActivity.activity.clear()

        checkOutActivity.activity.runOnUiThread {
            checkOutActivity.activity.recreate()
        }
        checkOutActivity.waitForIdle()
        Thread.sleep(1000)
        checkOutActivity.waitForIdle()
    }*/

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
        "54822102J-8010-L",
    )

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
}