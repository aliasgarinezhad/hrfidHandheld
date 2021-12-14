package com.jeanwest.reader.search

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

class SearchSubActivityTest {

    @get:Rule
    var searchSubActivity = createAndroidComposeRule<SearchSubActivity>()

    //Find special product and see results (found EPC, product specification and image matching and beep)

    @Test
    fun searchSubActivityTest1() {
        val name = "ساپورت"
        val kbarcode = "64822109J-8010-F"
        val rfidCode = 130290L

        searchSubActivity.onNodeWithText(name).assertExists()
        searchSubActivity.onNodeWithText(kbarcode).assertExists()

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        var uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 50L)
        uhfTagInfo.tid = "E28011702000015F195D0A18"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 101L)
        uhfTagInfo.tid = "E28011702000015F195D0A19"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 201L)
        uhfTagInfo.tid = "E28011702000015F195D0A17"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 301L)
        uhfTagInfo.tid = "E28011702000015F195D0A16"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)


        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()
        Thread.sleep(1000)
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 4").assertExists()

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 4").assertExists()

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 0").assertExists()
    }

    //Find special product that its number is bigger than 3 and test clear button
    @Test
    fun searchSubActivityTest2() {

        val name = "ساپورت"
        val kbarcode = "64822109J-8010-F"
        val rfidCode = 130290L

        searchSubActivity.onNodeWithText(name).assertExists()
        searchSubActivity.onNodeWithText(kbarcode).assertExists()

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        var uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 50L)
        uhfTagInfo.tid = "E28011702000015F195D0A18"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 101L)
        uhfTagInfo.tid = "E28011702000015F195D0A19"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 201L)
        uhfTagInfo.tid = "E28011702000015F195D0A17"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 301L)
        uhfTagInfo.tid = "E28011702000015F195D0A16"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        searchSubActivity.waitForIdle()

        Thread.sleep(1000)
        searchSubActivity.waitForIdle()

        searchSubActivity.onNodeWithText("پیدا شده: 4").assertExists()

        searchSubActivity.onNodeWithTag("SearchSubClearButton").performClick()
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 0").assertExists()
    }

    //Do last again and test check box
    @Test
    fun searchSubActivityTest3() {

        val name = "ساپورت"
        val kbarcode = "64822109J-8010-F"
        val rfidCode = 130290L

        searchSubActivity.onNodeWithText(name).assertExists()
        searchSubActivity.onNodeWithText(kbarcode).assertExists()

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        var uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 50L)
        uhfTagInfo.tid = "E28011702000015F195D0A18"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 101L)
        uhfTagInfo.tid = "E28011702000015F195D0A19"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 201L)
        uhfTagInfo.tid = "E28011702000015F195D0A17"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, rfidCode, 301L)
        uhfTagInfo.tid = "E28011702000015F195D0A16"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        searchSubActivity.onNodeWithTag("SearchSubSaveSwitch").performClick()
        searchSubActivity.waitForIdle()

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        searchSubActivity.waitForIdle()
        Thread.sleep(1000)
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 4").assertExists()

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 4").assertExists()

        searchSubActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        searchSubActivity.waitForIdle()
        searchSubActivity.onNodeWithText("پیدا شده: 4").assertExists()

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