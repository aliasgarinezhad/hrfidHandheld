package com.jeanwest.reader.checkIn

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.shared.DraftProperties
import com.jeanwest.reader.shared.Product
import com.jeanwest.reader.shared.test.Barcode2D
import com.jeanwest.reader.shared.test.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test


class CheckInActivityTest {

    @get:Rule
    val checkInActivity = createAndroidComposeRule<CheckInActivity>()

    @Test
    fun test() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        val kbarcodes = mutableListOf<String>()
        val productJson = JSONArray(stockDraftProducts)

        for (i in 0 until productJson.length()) {
            kbarcodes.add(
                productJson.getJSONObject(i).getString("kbarcode")
            )
        }

        checkInActivity.waitForIdle()
        Thread.sleep(500)
        checkInActivity.waitForIdle()

        if (checkInActivity.activity.scanningMode) {

            checkInActivity.onNodeWithText("تایید نهایی").performClick()
            checkInActivity.waitForIdle()
            checkInActivity.onNodeWithText("خیر، نتایج پاک شوند").performClick()
            checkInActivity.waitForIdle()
            checkInActivity.onNodeWithText("کسری").assertDoesNotExist()
        }

        barcodeScan(checkInCode.number.toString())

        checkInActivity.waitForIdle()
        Thread.sleep(500)
        checkInActivity.waitForIdle()
        Thread.sleep(500)
        checkInActivity.waitForIdle()
        Thread.sleep(500)
        checkInActivity.waitForIdle()

        Thread.sleep(2000)
        checkInActivity.waitForIdle()

        checkInActivity.onNodeWithText("کسری: ${checkInCode.numberOfItems}").assertExists()
        checkInActivity.onNodeWithText("اضافی: 0").assertExists()
        checkInActivity.onNodeWithText("اسکن: 0").assertExists()

        val appProductMap = mutableMapOf<Long, Int>()
        checkInActivity.activity.inputProducts.forEach {
            assert(it.value.draftNumber == kbarcodes.count { it1 ->
                it.value.KBarCode == it1
            })
        }

        val products = mutableListOf<Product>()
        products.addAll(checkInActivity.activity.inputProducts.values)
        products.sortBy {
            it.productCode
        }
        products.sortBy {
            it.name
        }

        for (i in 0 until 3) {

            checkInActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(products[i].KBarCode)
                assertTextContains(products[i].name)
                assertTextContains("موجودی: " + products[i].draftNumber)
                assertTextContains("کسری: " + products[i].draftNumber)
            }
        }

        epcScan(false, appProductMap)

        checkInActivity.waitForIdle()
        Thread.sleep(4000)
        checkInActivity.waitForIdle()
        Thread.sleep(2000)
        checkInActivity.waitForIdle()
        restart()

        checkInActivity.onNodeWithText("کسری: 0").assertExists()
        checkInActivity.onNodeWithText("اضافی: 0").assertExists()
        checkInActivity.onNodeWithText("اسکن: ${checkInCode.numberOfItems}").assertExists()
        //checkInActivity.onNodeWithTag("items").assertDoesNotExist()

        val productMapBarcode = mutableMapOf<String, Int>()
        products.subList(0, 20).forEach { it1 ->
            productMapBarcode[it1.KBarCode] = it1.draftNumber
        }

        clearUserData()
        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithTag("scanTypeDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("بارکد").performClick()
        checkInActivity.waitForIdle()
        barcodeArrayScan(productMapBarcode)
        checkResults(productMapBarcode)
    }

    private fun checkResults(productMapBarcode: MutableMap<String, Int>) {

        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()

        checkInActivity.activity.inputProducts.filter {
            it.key in productMapBarcode.keys
        }.forEach {

            if (it.value.draftNumber >= 3) {

                checkInActivity.activity.productConflicts.forEach { it1 ->
                    if (it1.KBarCode == it.value.KBarCode) {
                        assert(it1.conflictNumber == 3 && it1.conflictType == "کسری")
                    }
                }
            } else if (it.value.draftNumber == 2) {

                checkInActivity.activity.productConflicts.forEach { it1 ->
                    if (it1.KBarCode == it.value.KBarCode) {
                        assert(it1.conflictNumber == 3 && it1.conflictType == "اضافی")
                    }
                }
            } else {

                checkInActivity.activity.productConflicts.forEach { it1 ->
                    if (it1.KBarCode == it.value.KBarCode) {
                        assert(it1.conflictNumber == 1 && it1.conflictType == "اضافی")
                    }
                }
            }
        }

        val shortageProductList = checkInActivity.activity.productConflicts.filter {
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

            checkInActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(shortageProductList[i].KBarCode)
                assertTextContains(shortageProductList[i].name)
                assertTextContains("موجودی: " + shortageProductList[i].draftNumber)
                assertTextContains("کسری: " + shortageProductList[i].conflictNumber)
            }
        }

        val additionalProductList = checkInActivity.activity.productConflicts.filter {
            it.conflictType == "اضافی"
        }.toMutableList()

        additionalProductList.sortBy {
            it.productCode
        }
        additionalProductList.sortBy {
            it.name
        }

        checkInActivity.onNodeWithTag("checkInFilterDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("اضافی").performClick()
        checkInActivity.waitForIdle()

        for (i in 0 until 3) {

            checkInActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(additionalProductList[i].KBarCode)
                assertTextContains(additionalProductList[i].name)
                assertTextContains("موجودی: " + additionalProductList[i].draftNumber)
                assertTextContains("اضافی: " + additionalProductList[i].conflictNumber)
            }
        }
    }

    private fun epcScan(withDifference: Boolean, rfidMap: MutableMap<Long, Int>) {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        if (withDifference) {

            rfidMap.forEach {

                if (it.value >= 3) {
                    for (i in 0 until it.value - 3) {
                        val uhfTagInfo = UHFTAGInfo()
                        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.key, i.toLong())
                        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                    }
                } else if (it.value == 2) {
                    for (i in 0 until it.value + 3) {
                        val uhfTagInfo = UHFTAGInfo()
                        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.key, i.toLong())
                        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                    }
                } else {
                    for (i in 0 until it.value + 1) {
                        val uhfTagInfo = UHFTAGInfo()
                        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.key, i.toLong())
                        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                    }
                }
            }
        } else {
            checkInActivity.activity.draftProperties.epcTable.forEach {
                val uhfTagInfo = UHFTAGInfo()
                uhfTagInfo.epc = it
                RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
            }
        }

        checkInActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        checkInActivity.waitForIdle()

        Thread.sleep(1000)
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

    private fun clearUserData() {

        checkInActivity.onNodeWithTag("CheckInTestTag").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("بله").performClick()
        checkInActivity.waitForIdle()
    }

    private val checkInCode = DraftProperties(
        number = 119945,
        date = "1401/5/30",
        numberOfItems = 125,
        source = 44,
        destination = 1707
    )

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

    private fun barcodeArrayScan(productMapBarcode: MutableMap<String, Int>) {
        productMapBarcode.forEach {

            if (it.value >= 3) {
                for (i in 0 until it.value - 3) {
                    barcodeScan(it.key)
                }
            } else if (it.value == 2) {
                for (i in 0 until it.value + 3) {
                    barcodeScan(it.key)
                }
            } else {
                for (i in 0 until it.value + 1) {
                    barcodeScan(it.key)
                }
            }
        }
    }

    private fun barcodeScan(barcode: String) {
        Barcode2D.barcode = barcode
        checkInActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        checkInActivity.waitForIdle()
        Thread.sleep(500)
        checkInActivity.waitForIdle()
        Thread.sleep(500)
        checkInActivity.waitForIdle()
    }
}