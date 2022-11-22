package com.jeanwest.reader

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.activities.MainActivity
import com.jeanwest.reader.activities.ConfirmStockDraft
import com.jeanwest.reader.testData.stockDraftProducts
import com.jeanwest.reader.data.DraftProperties
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.test.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test


class ConfirmStockDraftTest {

    @get:Rule
    val activity = createAndroidComposeRule<ConfirmStockDraft>()

    @Test
    fun test() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"
        waitForFinishLoading()

        val kbarcodes = mutableListOf<String>()
        val productJson = JSONArray(stockDraftProducts)

        for (i in 0 until productJson.length()) {
            kbarcodes.add(
                productJson.getJSONObject(i).getString("kbarcode")
            )
        }

        if (activity.activity.scanningMode) {

            activity.onNodeWithText("تایید نهایی").performClick()
            activity.waitForIdle()
            activity.onNodeWithText("خیر، نتایج پاک شوند").performClick()
            activity.waitForIdle()
            activity.onNodeWithText("کسری").assertDoesNotExist()
        }

        barcodeScan(checkInCode.number.toString())
        waitForFinishLoading()

        activity.onNodeWithText("کسری: ${checkInCode.numberOfItems}").assertExists()
        activity.onNodeWithText("اضافی: 0").assertExists()
        activity.onNodeWithText("اسکن: 0").assertExists()

        activity.activity.inputProducts.forEach {
            assert(it.value.draftNumber == kbarcodes.count { it1 ->
                it.value.KBarCode == it1
            })
        }

        val products = mutableListOf<Product>()
        products.addAll(activity.activity.inputProducts.values)
        products.sortBy {
            it.productCode
        }
        products.sortBy {
            it.name
        }

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(products[i].KBarCode)
                assertTextContains(products[i].name)
                assertTextContains("موجودی: " + products[i].draftNumber)
                assertTextContains("کسری: " + products[i].draftNumber)
            }
        }

        epcScan(activity.activity.draftProperties.epcTable)
        waitForFinishLoading()

        activity.onNodeWithText("کسری: 0").assertExists()
        activity.onNodeWithText("اضافی: 0").assertExists()
        activity.onNodeWithText("اسکن: ${checkInCode.numberOfItems}").assertExists()

        activity.onNodeWithText("تایید نهایی").performClick()
        activity.waitForIdle()
        activity.onNodeWithText("بله").performClick()
        waitForFinishLoading()
        barcodeScan(checkInCode.number.toString())
        waitForFinishLoading()

        clearUserData()
        activity.onNodeWithTag("scanTypeDropDownList").performClick()
        activity.waitForIdle()
        activity.onNodeWithText("بارکد").performClick()
        activity.waitForIdle()
        barcodeScan("123456")
        barcodeArrayScan(products.subList(0, 20))
        restart()
        checkResults(products.subList(0, 20))
    }


    private fun checkResults(products: MutableList<Product>) {

        var additionalNumber = 0
        var scannedNumber = 0

        products.forEach {

            if (it.draftNumber >= 3) {

                activity.activity.productConflicts.forEach { it1 ->
                    if (it1.KBarCode == it.KBarCode) {
                        assert(it1.conflictNumber == 3 && it1.conflictType == "کسری")
                        scannedNumber += it1.scannedNumber
                    }
                }
            } else if (it.draftNumber == 2) {

                activity.activity.productConflicts.forEach { it1 ->
                    if (it1.KBarCode == it.KBarCode) {
                        assert(it1.conflictNumber == 3 && it1.conflictType == "اضافی")
                        additionalNumber += 3
                        scannedNumber += it1.scannedNumber
                    }
                }
            } else {

                activity.activity.productConflicts.forEach { it1 ->
                    if (it1.KBarCode == it.KBarCode) {
                        assert(it1.conflictNumber == 1 && it1.conflictType == "اضافی")
                        additionalNumber += 1
                        scannedNumber += it1.scannedNumber
                    }
                }
            }
        }

        activity.onAllNodesWithText("کسری: ${checkInCode.numberOfItems - (scannedNumber - additionalNumber)}")[0].assertExists()
        activity.onNodeWithText("اضافی: $additionalNumber").assertExists()
        activity.onNodeWithText("اسکن: $scannedNumber").assertExists()

        val shortageProductList = activity.activity.productConflicts.filter {
            it.conflictType == "کسری"
        }.toMutableList()

        shortageProductList.sortBy {
            it.productCode
        }
        shortageProductList.sortBy {
            it.name
        }

        val forLoopMaxValue = if (shortageProductList.size > 3) 3 else shortageProductList.size
        for (i in 0 until forLoopMaxValue) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(shortageProductList[i].KBarCode)
                assertTextContains(shortageProductList[i].name)
                assertTextContains("موجودی: " + shortageProductList[i].draftNumber)
                assertTextContains("کسری: " + shortageProductList[i].conflictNumber)
            }
        }

        val additionalProductList = activity.activity.productConflicts.filter {
            it.conflictType == "اضافی"
        }.toMutableList()

        additionalProductList.sortBy {
            it.productCode
        }
        additionalProductList.sortBy {
            it.name
        }

        activity.onNodeWithTag("checkInFilterDropDownList").performClick()
        activity.waitForIdle()
        activity.onNodeWithText("اضافی").performClick()
        activity.waitForIdle()

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(additionalProductList[i].KBarCode)
                assertTextContains(additionalProductList[i].name)
                assertTextContains("موجودی: " + additionalProductList[i].draftNumber)
                assertTextContains("اضافی: " + additionalProductList[i].conflictNumber)
            }
        }
    }

    private fun waitForFinishLoading() {

        activity.waitForIdle()
        while (activity.activity.loading || activity.activity.scanning) {
            Thread.sleep(200)
            activity.waitForIdle()
        }
    }

    private fun epcScan(products: MutableList<String>) {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        val uhfTagInfo1 = UHFTAGInfo()
        uhfTagInfo1.epc = "30123456789"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo1)

        products.forEach {
            val uhfTagInfo = UHFTAGInfo()
            uhfTagInfo.epc = it
            RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
        }

        activity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        activity.waitForIdle()

        Thread.sleep(1000)
        activity.waitForIdle()

        activity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        activity.waitForIdle()
        waitForFinishLoading()
    }

    private fun restart() {
        activity.activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()
    }

    private fun clearUserData() {

        activity.onNodeWithTag("CheckInTestTag").performClick()
        activity.waitForIdle()
        activity.onNodeWithText("بله").performClick()
        activity.waitForIdle()
    }

    private val checkInCode = DraftProperties(
        number = 119945,
        date = "1401/5/30",
        numberOfItems = 125,
        source = 44,
        destination = 1707
    )

    private fun barcodeArrayScan(products: MutableList<Product>) {
        products.forEach {

            if (it.draftNumber >= 3) {
                for (i in 0 until it.draftNumber - 3) {
                    barcodeScan(it.KBarCode)
                }
            } else if (it.draftNumber == 2) {
                for (i in 0 until it.draftNumber + 3) {
                    barcodeScan(it.KBarCode)
                }
            } else {
                for (i in 0 until it.draftNumber + 1) {
                    barcodeScan(it.KBarCode)
                }
            }
        }
    }

    private fun barcodeScan(barcode: String) {
        Barcode2D.barcode = barcode
        activity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        waitForFinishLoading()
    }
}
