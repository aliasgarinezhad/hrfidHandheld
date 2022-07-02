package com.jeanwest.reader.search

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.rscja.deviceapi.RFIDWithUHFUART.uhfTagInfo
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test


@OptIn(ExperimentalCoilApi::class)
class SearchSubActivityTest {

    @get:Rule
    var searchSubActivity = createAndroidComposeRule<SearchSubActivity>()

    //Find special product and see results (found EPC, product specification and image matching and beep)
    @Test
    fun searchSubActivityTest1() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        searchSubActivity.waitForIdle()
        Thread.sleep(1000)
        searchSubActivity.waitForIdle()

        searchSubActivity.onNodeWithText(product.name).assertExists()
        searchSubActivity.onNodeWithText(product.KBarCode).assertExists()
        searchSubActivity.onNodeWithText("قیمت: " + product.originalPrice).assertExists()
        searchSubActivity.onNodeWithText("فروش: " + product.salePrice).assertExists()
        searchSubActivity.onNodeWithText("موجودی فروشگاه: " + product.storeNumber.toString())
            .assertExists()
        searchSubActivity.onNodeWithText("موجودی انبار: " + product.wareHouseNumber.toString())
            .assertExists()
        searchSubActivity.onNodeWithText("پیدا شده: 0").assertExists()

        uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""
        RFIDWithUHFUART.writtenUhfTagInfo.rssi = ""

        val uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, product.rfidKey, 50L)
        uhfTagInfo.tid = "E28011702000015F195D0A18"
        uhfTagInfo.rssi = ""

        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)


        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()

        var time = System.currentTimeMillis()
        var currentDistance = 1F

        while (System.currentTimeMillis() - time < 2000) {

            searchSubActivity.waitForIdle()

            if (currentDistance == 1F && searchSubActivity.activity.distance == 0.7F) {
                time = System.currentTimeMillis()
                currentDistance = 0.7F
            } else if (currentDistance == 0.7F && searchSubActivity.activity.distance == 0.5F) {
                time = System.currentTimeMillis()
                currentDistance = 0.5F
            } else if (currentDistance == 0.5F && searchSubActivity.activity.distance == 0.2F) {
                time = System.currentTimeMillis()
                currentDistance = 0.2F
            }else if (currentDistance == 0.7F && searchSubActivity.activity.distance == 0.5F) {
                time = System.currentTimeMillis()
                currentDistance = 0.5F
            }
        }
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 1").assertExists()

        searchSubActivity.waitForIdle()

        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""
        RFIDWithUHFUART.writtenUhfTagInfo.rssi = ""

        currentDistance = 0.05F
        time = System.currentTimeMillis()

        while (System.currentTimeMillis() - time < 2000) {

            searchSubActivity.waitForIdle()

            if (currentDistance == 0.05F && searchSubActivity.activity.distance == 0.2F) {
                time = System.currentTimeMillis()
                currentDistance = 0.2F
            } else if (currentDistance == 0.2F && searchSubActivity.activity.distance == 0.5F) {
                time = System.currentTimeMillis()
                currentDistance = 0.5F
            } else if (currentDistance == 0.5F && searchSubActivity.activity.distance == 0.7F) {
                time = System.currentTimeMillis()
                currentDistance = 0.7F
            } else if (currentDistance == 0.7F && searchSubActivity.activity.distance == 1F) {
                time = System.currentTimeMillis()
                currentDistance = 1F
            }
        }

        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 1").assertExists()

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()

    }

    private val product = Product(
        name = "ساپورت",
        KBarCode = "64822109J-8010-F",
        imageUrl = "https://www.banimode.com/jeanswest/image.php?token=tmv43w4as&code=64822109J-8010-F",
        storeNumber = 1,
        wareHouseNumber = 0,
        productCode = "64822109",
        size = "F",
        color = "8010",
        originalPrice = "1490000",
        salePrice = "1490000",
        primaryKey = 9514289L,
        rfidKey = 130290L
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

}