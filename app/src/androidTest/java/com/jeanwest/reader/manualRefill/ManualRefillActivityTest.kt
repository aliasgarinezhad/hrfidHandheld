package com.jeanwest.reader.manualRefill

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.hardware.Barcode2D
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

@ExperimentalCoilApi
class ManualRefillActivityTest {

    @get:Rule
    val manualRefillActivity = createAndroidComposeRule<ManualRefillActivity>()

    //scan some epcs and barcodes, delete process
    @Test
    fun manualRefillActivityTest1() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        clearUserData()

        manualRefillActivity.onNodeWithTag("scanTypeDropDownList").performClick()
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithText("RFID").performClick()
        manualRefillActivity.waitForIdle()
        epcScan()

        manualRefillActivity.onNodeWithText(ManualRefillActivity.manualRefillProducts[0].KBarCode)
            .assertExists()
        manualRefillActivity.onNodeWithText(ManualRefillActivity.manualRefillProducts[1].KBarCode)
            .assertExists()
        manualRefillActivity.onNodeWithText(ManualRefillActivity.manualRefillProducts[2].KBarCode)
            .assertExists()
        manualRefillActivity.onNodeWithText(ManualRefillActivity.manualRefillProducts[3].KBarCode)
            .assertExists()

        ManualRefillActivity.manualRefillProducts.forEach {
            if (it.KBarCode == "11852002J-2430-F") {
                if (!((it.scannedEPCNumber + it.scannedBarcodeNumber) == 1 && it.scannedEPCs[0] == "30C0194000DC20C000017A1C")) {
                    assert(false)
                }
            }
        }

        manualRefillActivity.onNodeWithTag("scanTypeDropDownList").performClick()
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithText("بارکد").performClick()
        manualRefillActivity.waitForIdle()
        barcodeScan()

        manualRefillActivity.onNodeWithText("اسکن شده: ${barcodes.size + epcs.size}").assertExists()

        ManualRefillActivity.manualRefillProducts.forEach {
            if (it.KBarCode == "11852002J-2430-F") {
                if (!((it.scannedEPCNumber + it.scannedBarcodeNumber) == 3 && it.scannedEPCs[0] == "30C0194000DC20C000017A1C")) {
                    assert(false)
                }
            }
        }

        ManualRefillActivity.manualRefillProducts.forEach {
            if (it.KBarCode == "54822102J-8010-L") {
                if (!((it.scannedEPCNumber + it.scannedBarcodeNumber) == 1 && it.scannedEPCs.isEmpty())) {
                    assert(false)
                }
            }
        }

        val removeItem1 = ManualRefillActivity.manualRefillProducts[0].KBarCode
        val removeItem2 = ManualRefillActivity.manualRefillProducts[1].KBarCode
        manualRefillActivity.onNodeWithText(ManualRefillActivity.manualRefillProducts[0].KBarCode)
            .performTouchInput {
                longClick()
            }
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithText(ManualRefillActivity.manualRefillProducts[1].KBarCode)
            .performClick()
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithText("پاک کردن").performClick()
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithText("بله").performClick()
        manualRefillActivity.waitForIdle()

        Thread.sleep(5000)
        manualRefillActivity.waitForIdle()

        manualRefillActivity.onNodeWithText(removeItem1).assertDoesNotExist()
        manualRefillActivity.onNodeWithText(removeItem2).assertDoesNotExist()
    }

    //adding new user defined product, scan that product manual test
    /*@Test
    fun manualRefillActivityTest2() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        clearUserData()

        manualRefillActivity.onNodeWithText("تعریف شارژ").performClick()
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithTag("NewProductKBarCode").performTextClearance()
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithTag("NewProductKBarCode")
            .performTextInput("11531052J-2000-L")
        manualRefillActivity.waitForIdle()
        manualRefillActivity.onNodeWithText("اضافه کردن").performClick()
        manualRefillActivity.waitForIdle()
        Thread.sleep(2000)
        manualRefillActivity.waitForIdle()

        manualRefillActivity.onNodeWithText("11531052J-2000-L").assertExists()

        Barcode2D.barcode = "11531052J-2000-L"

        manualRefillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        manualRefillActivity.waitForIdle()
        Thread.sleep(2000)
        manualRefillActivity.waitForIdle()

        manualRefillActivity.onAllNodesWithText("اسکن شده: 1").assertCountEquals(2)
        manualRefillActivity.onNodeWithText("اسکن شده: 0").assertDoesNotExist()

    }*/

    private fun barcodeScan() {

        for (i in 0 until barcodes.size) {
            Barcode2D.barcode = barcodes[i]

            manualRefillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
            manualRefillActivity.waitForIdle()
            Thread.sleep(2000)
            manualRefillActivity.waitForIdle()
        }
        Thread.sleep(10000)
        manualRefillActivity.waitForIdle()
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

        manualRefillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        manualRefillActivity.waitForIdle()
        Thread.sleep(2000)
        manualRefillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        Thread.sleep(10000)
        manualRefillActivity.waitForIdle()

    }

    private fun clearUserData() {

        ManualRefillActivity.manualRefillProducts.forEach {
            manualRefillActivity.activity.signedProductCodes.add(it.KBarCode)
        }
        manualRefillActivity.activity.clear()


        manualRefillActivity.activity.runOnUiThread {
            manualRefillActivity.activity.recreate()
        }
        manualRefillActivity.waitForIdle()
        Thread.sleep(1000)
        manualRefillActivity.waitForIdle()
    }

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

