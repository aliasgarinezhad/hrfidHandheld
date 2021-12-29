package com.jeanwest.reader.write


import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

class WriteActivityTest {

    @get:Rule
    var writeActivity = createAndroidComposeRule<WriteActivity>()

    //Write one stuff and check whether result is correct

    @Test
    fun addProductTest1() {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        val uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "E28011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A17"
        repeat(5) {
            RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
        }

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()
        Thread.sleep(1000)
        writeActivity.waitForIdle()


        assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).item == 130290L)
    }

    //Write one unProgrammed stuff that placed between four programmed stuffs

    @Test
    fun addProductTest2() {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        var uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A18"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A19"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "E28011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A17"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A16"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A15"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()

        Thread.sleep(1000)
        writeActivity.waitForIdle()

        assert(RFIDWithUHFUART.writtenUhfTagInfo.tid == "E28011702000015F195D0A17")
    }

    //Do last test again with option checked (you should face with programming error)

    @Test
    fun addProductTest3() {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        var uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A18"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A19"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "E28011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A17"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A16"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "308011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A15"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        writeActivity.onNodeWithTag("switch").performClick()

        writeActivity.waitForIdle()

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()

        Thread.sleep(1000)
        writeActivity.waitForIdle()

        assert(RFIDWithUHFUART.writtenUhfTagInfo.tid != "E28011702000015F195D0A17")
    }

    @Test
    fun addProductTest4() {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        writeActivity.waitForIdle()

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()

        writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        writeActivity.waitForIdle()

        Thread.sleep(2000)
        writeActivity.waitForIdle()

        assert(RFIDWithUHFUART.writtenUhfTagInfo.epc == "")
    }

    @Test
    fun addProductTest5() {

        val header = 48
        val company = 101
        val partition = 0
        val filter = 0
        val serialNumberRange = 1000000L..1000100L

        /*writeActivity.onNodeWithTag("WriteSettingButton").performClick()
        writeActivity.waitForIdle()
        writeActivity.onNodeWithTag("WritePasswordTextField").performTextClearance()
        writeActivity.onNodeWithTag("WritePasswordTextField").performTextInput("123456")
        writeActivity.onNodeWithTag("WriteEnterWriteSettingButton").performClick()

        writeActivity.onNodeWithTag("WriteSettingPartitionDropDownList").performClick()
        writeActivity.onNodeWithText(partition.toString()).performClick()

        writeActivity.onNodeWithTag("WriteSettingFilterDropDownList").performClick()
        writeActivity.onNodeWithText(filter.toString()).performClick()

        writeActivity.onNodeWithTag("WriteSettingHeaderTextField").performTextClearance()
        writeActivity.onNodeWithTag("WriteSettingHeaderTextField").performTextInput(header.toString())

        writeActivity.onNodeWithTag("WriteSettingCompanyTextField").performTextClearance()
        writeActivity.onNodeWithTag("WriteSettingCompanyTextField").performTextInput(company.toString())

        writeActivity.onNodeWithTag("WriteSettingSerialNumberMinTextField").performTextClearance()
        writeActivity.onNodeWithTag("WriteSettingSerialNumberMinTextField").performTextInput("1000000")

        writeActivity.onNodeWithTag("WriteSettingSerialNumberMaxTextField").performTextClearance()
        writeActivity.onNodeWithTag("WriteSettingSerialNumberMaxTextField").performTextInput("1000100")
        writeActivity.waitForIdle()
        writeActivity.onNodeWithTag("WriteSettingBackButton").performClick()
        writeActivity.waitForIdle()*/

        for (i in serialNumberRange) {

            RFIDWithUHFUART.uhfTagInfo.clear()
            RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
            RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

            val uhfTagInfo = UHFTAGInfo()
            uhfTagInfo.epc = "E28011702000015F195D0A17"
            uhfTagInfo.tid = "E28011702000015F195D0A18"

            repeat(5) {
                RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
            }

            writeActivity.waitForIdle()

            writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

            writeActivity.waitForIdle()

            writeActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

            writeActivity.waitForIdle()

            Thread.sleep(500)
            writeActivity.waitForIdle()

            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).item == 130290L)
            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).serial == i)
            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).header == header)
            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).company == company)
            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).filter == filter)
            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).partition == partition)
        }
    }

    private fun epcDecoder(epc: String): EPC {

        val binaryEPC =
            String.format("%64s", epc.substring(0, 16).toULong(16).toString(2))
                .replace(" ".toRegex(), "0") +
                    String.format("%32s", epc.substring(16, 24).toULong(16).toString(2))
                        .replace(" ".toRegex(), "0")
        val result = EPC(0, 0, 0, 0, 0L, 0L)
        result.header = binaryEPC.substring(0, 8).toInt(2)
        result.partition = binaryEPC.substring(8, 11).toInt(2)
        result.filter = binaryEPC.substring(11, 14).toInt(2)
        result.company = binaryEPC.substring(14, 26).toInt(2)
        result.item = binaryEPC.substring(26, 58).toLong(2)
        result.serial = binaryEPC.substring(58, 96).toLong(2)
        return result
    }

    data class EPC(
        var header: Int,
        var filter: Int,
        var partition: Int,
        var company: Int,
        var item: Long,
        var serial: Long
    )
}