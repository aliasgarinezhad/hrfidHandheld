package com.jeanwest.reader.search

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import coil.annotation.ExperimentalCoilApi
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
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
        val name = "ساپورت"
        val kbarcode = "64822109J-8010-F"
        val rfidCode = 130290L

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        searchSubActivity.waitForIdle()
        Thread.sleep(500)
        searchSubActivity.waitForIdle()

        searchSubActivity.activity.intent.putExtra("product", Gson().toJson(product).toString())

        searchSubActivity.activity.runOnUiThread {
            searchSubActivity.activity.recreate()
        }

        searchSubActivity.waitForIdle()

        searchSubActivity.onNodeWithText(name).assertExists()
        searchSubActivity.onNodeWithText(kbarcode).assertExists()
        searchSubActivity.onNodeWithText("قیمت: " + product.originalPrice).assertExists()
        searchSubActivity.onNodeWithText("فروش: " + product.salePrice).assertExists()
        searchSubActivity.onNodeWithText("فروش: " + product.salePrice).assertExists()
        searchSubActivity.onNodeWithText("موجودی فروشگاه: " + product.shoppingNumber.toString())
            .assertExists()
        searchSubActivity.onNodeWithText("موجودی انبار: " + product.warehouseNumber.toString())
            .assertExists()
        searchSubActivity.onNodeWithText("پیدا شده: 0").assertExists()


        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        val uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 50L)
        uhfTagInfo.tid = "E28011702000015F195D0A18"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()
        Thread.sleep(1000)
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 1").assertExists()

    }

    private val product = SearchResultProducts(
        name = "ساپورت",
        KBarCode = "64822109J-8010-F",
        imageUrl = "https://www.banimode.com/jeanswest/image.php?token=tmv43w4as&code=64822109J-8010-F",
        shoppingNumber = 1,
        warehouseNumber = 0,
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