package com.jeanwest.reader.count

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.jeanwest.reader.sharedClassesAndFiles.hardware.Barcode2D
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalCoilApi

// first run GetCheckInPropertiesActivityTest

class CountActivityTest {

    @get:Rule
    var countActivity = createAndroidComposeRule<CountActivity>()

    //open an excel file, scan some epcs, check filters
    @Test
    fun test1() {

        val inputProductsNumber = 1062

        start()
        clear()
        clear()

        val memory = PreferenceManager.getDefaultSharedPreferences(countActivity.activity)
        val type = object : TypeToken<MutableList<Product>>() {}.type
        val products: MutableList<Product> = Gson().fromJson(
            memory.getString("inputProductsForTest", ""),
            type
        )

        products.sortBy {
            it.productCode
        }
        products.sortBy {
            it.name
        }

        products.forEach {
            repeat(it.desiredNumber) { _ ->
                countActivity.activity.inputBarcodes.add(it.scannedBarcode)
            }
        }
        countActivity.activity.saveToMemory()

        restart()

        countActivity.waitForIdle()
        Thread.sleep(5000)

        countActivity.waitForIdle()
        Thread.sleep(5000)
        countActivity.waitForIdle()

        countActivity.onNodeWithText("کسری: $inputProductsNumber").assertExists()
        countActivity.onNodeWithText("اضافی: 0").assertExists()
        countActivity.onNodeWithText("اسکن: 0").assertExists()

        products.forEach {
            assert(countActivity.activity.inputProducts[it.KBarCode] == it)
        }

        for (i in 0 until 3) {

            countActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(products[i].KBarCode)
                assertTextContains(products[i].name)
                assertTextContains("موجودی: " + products[i].desiredNumber)
                assertTextContains("کسری: " + products[i].desiredNumber)
            }
        }

        epcScan(false, products)

        countActivity.waitForIdle()
        Thread.sleep(4000)
        countActivity.waitForIdle()
        Thread.sleep(2000)
        countActivity.waitForIdle()

        countActivity.onNodeWithText("کسری: 0").assertExists()
        countActivity.onNodeWithText("اضافی: 0").assertExists()
        countActivity.onNodeWithText("اسکن: $inputProductsNumber").assertExists()

        val productMapRFID = mutableListOf<Product>()
        val productMapBarcode = mutableListOf<Product>()
        products.forEach { it1 ->
            if ((it1.brandName == "JeansWest" || it1.brandName == "JootiJeans" || it1.brandName == "Baleno")
                && !it1.name.contains("جوراب")
                && !it1.name.contains("عينك")
                && !it1.name.contains("شاپينگ")
            ) {
                productMapRFID.add(it1)
            } else {
                productMapBarcode.add(it1)
            }
        }

        clear()
        countActivity.waitForIdle()
        Thread.sleep(5000)
        countActivity.waitForIdle()
        epcScan(true, products)
        checkResults()

        clear()
        countActivity.waitForIdle()
        Thread.sleep(5000)
        countActivity.waitForIdle()
        epcScan(true, productMapRFID)
        countActivity.onNodeWithTag("scanTypeDropDownList").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("بارکد").performClick()
        countActivity.waitForIdle()
        barcodeArrayScan(productMapBarcode)
        countActivity.onNodeWithTag("CountActivityFilterDropDownList").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("کسری").performClick()
        countActivity.waitForIdle()
        checkResults()
    }

    @Test
    fun test2() {

        val inputProductsNumber = 1062

        start()
        clear()
        clear()

        val memory = PreferenceManager.getDefaultSharedPreferences(countActivity.activity)
        val type = object : TypeToken<MutableList<Product>>() {}.type
        val products: MutableList<Product> = Gson().fromJson(
            memory.getString("inputProductsForTest", ""),
            type
        )

        products.sortBy {
            it.productCode
        }
        products.sortBy {
            it.name
        }

        countActivity.waitForIdle()
        Thread.sleep(5000)
        countActivity.waitForIdle()

        epcScan(false, products)

        countActivity.waitForIdle()
        Thread.sleep(4000)
        countActivity.waitForIdle()
        Thread.sleep(2000)
        countActivity.waitForIdle()

        countActivity.onNodeWithText("کسری: 0").assertDoesNotExist()
        countActivity.onNodeWithText("اضافی: $inputProductsNumber").assertDoesNotExist()
        countActivity.onNodeWithText("اسکن: $inputProductsNumber").assertExists()

        for (i in 0 until 3) {

            countActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(products[i].KBarCode)
                assertTextContains(products[i].name)
                assertTextContains("رنگ: " + products[i].color)
                assertTextContains("اسکن: " + products[i].desiredNumber)
            }
        }

        products.forEach {
            assert(countActivity.activity.uiList[it.KBarCode]!!.matchedNumber == it.desiredNumber)
            assert(countActivity.activity.uiList[it.KBarCode]!!.scan == "اضافی")
        }
    }

    private fun checkResults() {

        val shortagesNumber = 351
        val additionalNumber = 243
        val scannedNumber = 954

        countActivity.waitForIdle()
        Thread.sleep(5000)
        countActivity.waitForIdle()
        Thread.sleep(5000)
        countActivity.waitForIdle()

        countActivity.onAllNodesWithText("کسری: $shortagesNumber")[0].assertExists()
        countActivity.onNodeWithText("اضافی: $additionalNumber").assertExists()
        countActivity.onNodeWithText("اسکن: $scannedNumber").assertExists()

        countActivity.activity.inputProducts.forEach {

            if (it.value.desiredNumber >= 3) {
                assert(countActivity.activity.uiList[it.value.KBarCode]!!.matchedNumber == 3)
                assert(countActivity.activity.uiList[it.value.KBarCode]!!.scan == "کسری")
            } else if (it.value.desiredNumber == 2) {
                assert(countActivity.activity.uiList[it.value.KBarCode]!!.matchedNumber == 3)
                assert(countActivity.activity.uiList[it.value.KBarCode]!!.scan == "اضافی")
            } else {
                assert(countActivity.activity.uiList[it.value.KBarCode]!!.matchedNumber == 1)
                assert(countActivity.activity.uiList[it.value.KBarCode]!!.scan == "اضافی")
            }
        }

        val shortageProductList = countActivity.activity.uiList.values.filter {
            it.scan == "کسری"
        }.toMutableList()

        shortageProductList.sortBy {
            it.productCode
        }
        shortageProductList.sortBy {
            it.name
        }

        val forLoopMaxValue = if (shortageProductList.size > 3) 3 else shortageProductList.size
        for (i in 0 until forLoopMaxValue) {

            countActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(shortageProductList[i].KBarCode)
                assertTextContains(shortageProductList[i].name)
                assertTextContains("موجودی: " + shortageProductList[i].desiredNumber)
                assertTextContains("کسری: " + shortageProductList[i].matchedNumber)
            }
        }

        val additionalProductList = countActivity.activity.uiList.values.filter {
            it.scan == "اضافی"
        }.toMutableList()

        additionalProductList.sortBy {
            it.productCode
        }
        additionalProductList.sortBy {
            it.name
        }

        countActivity.onNodeWithTag("CountActivityFilterDropDownList").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("اضافی").performClick()
        countActivity.waitForIdle()

        for (i in 0 until 3) {

            countActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(additionalProductList[i].KBarCode)
                assertTextContains(additionalProductList[i].name)
                assertTextContains("موجودی: " + additionalProductList[i].desiredNumber)
                assertTextContains("اضافی: " + additionalProductList[i].matchedNumber)
            }
        }
    }

    private fun epcScan(withDifference: Boolean, rfidProducts: MutableList<Product>) {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        rfidProducts.forEach {

            if (withDifference) {
                if (it.desiredNumber >= 3) {
                    for (i in 0 until it.desiredNumber - 3) {
                        val uhfTagInfo = UHFTAGInfo()
                        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
                        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                    }
                } else if (it.desiredNumber == 2) {
                    for (i in 0 until it.desiredNumber + 3) {
                        val uhfTagInfo = UHFTAGInfo()
                        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
                        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                    }
                } else {
                    for (i in 0 until it.desiredNumber + 1) {
                        val uhfTagInfo = UHFTAGInfo()
                        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
                        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                    }
                }
            } else {
                for (i in 0 until it.desiredNumber) {
                    val uhfTagInfo = UHFTAGInfo()
                    uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
                    RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                }
            }
        }

        countActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        countActivity.waitForIdle()

        Thread.sleep(1000)
        countActivity.waitForIdle()

        countActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        countActivity.waitForIdle()

        Thread.sleep(2000)
        countActivity.waitForIdle()
        Thread.sleep(2000)
    }

    private fun barcodeArrayScan(productMapBarcode: MutableList<Product>) {
        productMapBarcode.forEach {

            if (it.desiredNumber >= 3) {
                for (i in 0 until it.desiredNumber - 3) {
                    barcodeScan(it.KBarCode)
                }
            } else if (it.desiredNumber == 2) {
                for (i in 0 until it.desiredNumber + 3) {
                    barcodeScan(it.KBarCode)
                }
            } else {
                for (i in 0 until it.desiredNumber + 1) {
                    barcodeScan(it.KBarCode)
                }
            }
        }
    }

    private fun barcodeScan(barcode: String) {
        Barcode2D.barcode = barcode
        countActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        countActivity.waitForIdle()
        Thread.sleep(1000)
        countActivity.waitForIdle()
        Thread.sleep(1000)
        countActivity.waitForIdle()
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

    private fun clear() {
        countActivity.onNodeWithTag("CountActivityClearButton").performClick()
        countActivity.waitForIdle()
        countActivity.onNodeWithText("بله").performClick()
        countActivity.waitForIdle()
    }

    private fun epcGenerator(
        header: Int,
        filter: Int,
        partition: Int,
        company: Int,
        item: Long,
        serial: Long
    ): String {

        var tempStr = java.lang.Long.toBinaryString(header.toLong())
        val headerStr = String.format("%8s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(filter.toLong())
        val filterStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(partition.toLong())
        val positionStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(company.toLong())
        val companynumberStr = String.format("%12s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(item)
        val itemNumberStr = String.format("%32s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(serial)
        val serialNumberStr = String.format("%38s", tempStr).replace(" ".toRegex(), "0")
        val epcStr =
            headerStr + positionStr + filterStr + companynumberStr + itemNumberStr + serialNumberStr // binary string of EPC (96 bit)

        tempStr = epcStr.substring(0, 64).toULong(2).toString(16)
        val epc0To64 = String.format("%16s", tempStr).replace(" ".toRegex(), "0")
        tempStr = epcStr.substring(64, 96).toULong(2).toString(16)
        val epc64To96 = String.format("%8s", tempStr).replace(" ".toRegex(), "0")

        return epc0To64 + epc64To96

    }
}

