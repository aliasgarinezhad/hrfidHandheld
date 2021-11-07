package com.jeanwest.reader.add


import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

class AddProductTest {

    /*@get:Rule
    var activity = createAndroidComposeRule<MainActivity>()*/

    @get:Rule
    var addProductActivity = createAndroidComposeRule<AddProductActivity>()

    //Write one stuff and check whether result is correct

    @Test
    fun addProductTest1() {
        //activity.onNodeWithText("اضافه کردن").performClick()

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        val uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "E28011702000015F195D0A17"
        uhfTagInfo.tid = "E28011702000015F195D0A17"
        repeat(5) {
            RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
        }

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()
        Thread.sleep(1000)
        addProductActivity.waitForIdle()


        //Log.e("Error", RFIDWithUHFUART.writtenUhfTagInfo.epc)
        assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).item == 130290L)
    }

    //Write one unProgrammed stuff that placed between four programmed stuffs

    @Test
    fun addProductTest2() {
        //activity.onNodeWithText("اضافه کردن").performClick()

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

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()

        Thread.sleep(1000)
        addProductActivity.waitForIdle()

        assert(RFIDWithUHFUART.writtenUhfTagInfo.tid == "E28011702000015F195D0A17")
    }

    //Do last test again with option checked (you should face with programming error)

    @Test
    fun addProductTest3() {
        //activity.onNodeWithText("اضافه کردن").performClick()

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

        addProductActivity.onNodeWithTag("switch").performClick()

        addProductActivity.waitForIdle()

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()

        Thread.sleep(1000)
        addProductActivity.waitForIdle()

        assert(RFIDWithUHFUART.writtenUhfTagInfo.tid != "E28011702000015F195D0A17")
    }

    @Test
    fun addProductTest5() {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        addProductActivity.waitForIdle()

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()

        addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

        addProductActivity.waitForIdle()

        Thread.sleep(2000)
        addProductActivity.waitForIdle()

        assert(RFIDWithUHFUART.writtenUhfTagInfo.epc == "")
    }

    @Test
    fun addProductTest4() {
        //activity.onNodeWithText("اضافه کردن").performClick()

        for (i in 1000000L..1000100L) {

            RFIDWithUHFUART.uhfTagInfo.clear()
            RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
            RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

            val uhfTagInfo = UHFTAGInfo()
            uhfTagInfo.epc = "E28011702000015F195D0A17"
            uhfTagInfo.tid = "E28011702000015F195D0A18"

            repeat(5) {
                RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
            }

            addProductActivity.waitForIdle()

            addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

            addProductActivity.waitForIdle()

            addProductActivity.activity.onKeyDown(280, KeyEvent(ACTION_DOWN, 280))

            addProductActivity.waitForIdle()

            Thread.sleep(500)
            addProductActivity.waitForIdle()

            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).item == 130290L)
            assert(epcDecoder(RFIDWithUHFUART.writtenUhfTagInfo.epc).serial == i)
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