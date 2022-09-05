package com.jeanwest.reader.checkIn

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.centralWarehouseCheckIn.CentralWarehouseCheckInActivity
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.jeanwest.reader.sharedClassesAndFiles.Barcode2D
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test


class CheckInActivityTest {

    @get:Rule
    val checkInActivity = createAndroidComposeRule<CheckInActivity>()

    //open a check-in, scan some items, check filters
    @Test
    fun checkInActivityTest1() {

        val inputProductsNumber = 1062

        start()
        clearUserData()
        restart()

        checkInActivity.waitForIdle()
        Thread.sleep(5000)

        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()

        checkInActivity.onNodeWithText("کسری: $inputProductsNumber").assertExists()
        checkInActivity.onNodeWithText("اضافی: 0").assertExists()
        checkInActivity.onNodeWithText("اسکن شده: 0").assertExists()

        val appProductMap = mutableMapOf<Long, Int>()
        checkInActivity.activity.inputProducts.forEach {
            appProductMap[it.value.rfidKey] = it.value.desiredNumber
        }

        assert(appProductMap == productMap)

        for (i in 0 until 3) {

            checkInActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(productList[i].KBarCode)
                assertTextContains(productList[i].name)
                assertTextContains("موجودی: " + productList[i].desiredNumber)
                assertTextContains("کسری: " + productList[i].desiredNumber)
            }
        }

        epcScan(false, productMap)

        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()

        checkInActivity.onNodeWithText("کسری: 0").assertExists()
        checkInActivity.onNodeWithText("اضافی: 0").assertExists()
        checkInActivity.onNodeWithText("اسکن شده: $inputProductsNumber").assertExists()
        checkInActivity.onNodeWithTag("items").assertDoesNotExist()

        clearUserData()
        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        epcScan(true, productMap)
        checkResults()

        clearUserData()
        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        epcScan(true, productMapRFID)
        checkInActivity.onNodeWithTag("scanTypeDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("بارکد").performClick()
        checkInActivity.waitForIdle()
        barcodeArrayScan()
        checkInActivity.onNodeWithTag("checkInFilterDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("کسری").performClick()
        checkInActivity.waitForIdle()
        checkResults()
    }

    private fun checkResults() {

        val shortagesNumber = 351
        val additionalNumber = 243
        val scannedNumber = 954

        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()

        checkInActivity.onNodeWithText("کسری: $shortagesNumber").assertExists()
        checkInActivity.onNodeWithText("اضافی: $additionalNumber").assertExists()
        checkInActivity.onNodeWithText("اسکن شده: $scannedNumber").assertExists()

        checkInActivity.activity.inputProducts.forEach {

            if (it.value.desiredNumber >= 3) {

                checkInActivity.activity.conflictResultProducts.forEach { it1 ->
                    if (it1.KBarCode == it.value.KBarCode) {
                        assert(it1.matchedNumber == 3 && it1.scan == "کسری")
                    }
                }
            } else if (it.value.desiredNumber == 2) {

                checkInActivity.activity.conflictResultProducts.forEach { it1 ->
                    if (it1.KBarCode == it.value.KBarCode) {
                        assert(it1.matchedNumber == 3 && it1.scan == "اضافی")
                    }
                }
            } else {

                checkInActivity.activity.conflictResultProducts.forEach { it1 ->
                    if (it1.KBarCode == it.value.KBarCode) {
                        assert(it1.matchedNumber == 1 && it1.scan == "اضافی")
                    }
                }
            }
        }

        for (i in 0 until 3) {

            checkInActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(shortageProductList[i].KBarCode)
                assertTextContains(shortageProductList[i].name)
                assertTextContains("موجودی: " + shortageProductList[i].desiredNumber)
                assertTextContains("کسری: " + shortageProductList[i].matchedNumber)
            }
        }

        checkInActivity.onNodeWithTag("checkInFilterDropDownList").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("اضافی").performClick()
        checkInActivity.waitForIdle()

        for (i in 0 until 3) {

            checkInActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(additionalProductList[i].KBarCode)
                assertTextContains(additionalProductList[i].name)
                assertTextContains("موجودی: " + additionalProductList[i].desiredNumber)
                assertTextContains("اضافی: " + additionalProductList[i].matchedNumber)
            }
        }
    }

    private fun epcScan(withDifference: Boolean, rfidMap: MutableMap<Long, Int>) {

        RFIDWithUHFUART.uhfTagInfo.clear()
        RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
        RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

        rfidMap.forEach {

            if (withDifference) {
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
            } else {
                for (i in 0 until it.value) {
                    val uhfTagInfo = UHFTAGInfo()
                    uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.key, i.toLong())
                    RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
                }
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

    private fun start() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"
    }

    private fun clearUserData() {

        checkInActivity.onNodeWithTag("CheckInTestTag").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("بله").performClick()
        checkInActivity.waitForIdle()
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

    private fun barcodeArrayScan() {
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

    private val productList = mutableListOf(

        Product(
            "DL013010400010001",
            desiredNumber = 13,
            name = "100 ml Body Scrub - Japanese Cherry Blossom ",
            matchedNumber = 3
        ),

        Product(
            "DL013010500010001",
            desiredNumber = 7,
            name = "100 ml Body Scrub -Mandarin Lime and Basil",
            matchedNumber = 3
        ),

        Product(
            "DL013011700050001",
            desiredNumber = 14,
            name = "500 ml Shampoo - Nectarine and Ruby Grapefruit",
            matchedNumber = 3
        ),
    )

    private val shortageProductList = mutableListOf(

        Product(
            "DL013010400010001",
            desiredNumber = 13,
            name = "100 ml Body Scrub - Japanese Cherry Blossom ",
            matchedNumber = 3
        ),

        Product(
            "DL013010500010001",
            desiredNumber = 7,
            name = "100 ml Body Scrub -Mandarin Lime and Basil",
            matchedNumber = 3
        ),

        Product(
            "DL013011700050001",
            desiredNumber = 14,
            name = "500 ml Shampoo - Nectarine and Ruby Grapefruit",
            matchedNumber = 3
        ),
    )

    private val additionalProductList = mutableListOf(
        Product(
            "03659511-8010-140-1",
            desiredNumber = 2,
            name = "اسلش",
            matchedNumber = 3
        ),
        Product(
            "03659511-8010-130-1",
            desiredNumber = 2,
            name = "اسلش",
            matchedNumber = 3
        ),
        Product(
            "03659511-8010-120-1",
            desiredNumber = 2,
            name = "اسلش",
            matchedNumber = 3
        )
    )

    private val productMapBarcode = mutableMapOf(
        "ZD-K9" to 1,
        "ZD-CAR5002-1-99" to 1,
        "ZD-CAR616la-72" to 2,
        "ZD-I8" to 1,
        "ZD-LIFTRAK" to 1,
        "ZD-K10" to 1,
        "ZD-MOTOR007" to 2,
        "ZD-CAR955120-23" to 1,
        "ZD-CAR955120-58" to 1,
        "ZD-TRAN3101-43" to 2,
        "ZD-TRAN3101-84" to 1,
        "ZD-ORDAK1620-16" to 3,
        "ZD-CAR003-01" to 2,
        "ZD-CAR003-4-93" to 2,
        "ZD-CAR003-5-72" to 2,
        "ZD-CAR003-1-72" to 2,
        "ZD-CAR003-3-16" to 2,
        "ZD-CAR003-2-23" to 2,
        "ZD-CAR616la-16" to 2,
        "ZD-TRAN19022" to 1,
        "ZD-TRAN19021" to 1,
        "ZD-CAR5002-2-72" to 1,
        "ZD-CAR5002-3-23" to 1,
        "ZD-CAR5002-4-72" to 1,
        "ZD-CAR5002-3-43" to 1,
        "ZD-CAR5002-2-16" to 1,
        "ZD-CAR5002-1-72" to 1,
        "ZD-CAR5002-4-58" to 1,
        "ZD-MOTOR2033-58" to 2,
        "ZD-CAR616F-23" to 2,
        "ZD-CAR616F-72" to 2,
        "ZD-TANK5892-36" to 1,
        "ZD-TANK5892-43" to 1,
        "ZD-MOTOR2033-72" to 2,
        "ZD-HELI99286" to 1,
        "21852001J-2080-F" to 1,
        "6269330724046" to 12,
        "DL013011700050001" to 14,
        "DL013010400010001" to 13,
        "DL013010500010001" to 7,
        "6269330710261" to 12,
        "6269330710087" to 12
    )

    private val productMapRFID = mutableMapOf(
        259070L to 1,
        262787L to 1,
        251646L to 130,
        259867L to 180,
        259161L to 2,
        259162L to 4,
        259163L to 4,
        259165L to 3,
        259164L to 3,
        253156L to 2,
        253155L to 3,
        253152L to 2,
        253153L to 3,
        253154L to 4,
        253172L to 2,
        253173L to 3,
        253174L to 4,
        253175L to 3,
        253176L to 2,
        253157L to 2,
        253158L to 3,
        253159L to 4,
        253161L to 2,
        253160L to 3,
        253169L to 4,
        253168L to 3,
        253170L to 3,
        253171L to 2,
        253167L to 2,
        252753L to 2,
        252752L to 2,
        252560L to 2,
        252754L to 3,
        252755L to 3,
        252756L to 3,
        252547L to 3,
        252546L to 3,
        252545L to 2,
        252544L to 2,
        252543L to 2,
        252542L to 2,
        252559L to 3,
        252558L to 3,
        252557L to 2,
        252556L to 2,
        252555L to 2,
        252554L to 2,
        252865L to 2,
        252870L to 3,
        252869L to 3,
        252868L to 2,
        252867L to 2,
        252866L to 2,
        259169L to 3,
        259170L to 3,
        259166L to 2,
        259167L to 4,
        259168L to 4,
        258300L to 6,
        258299L to 8,
        258298L to 8,
        258297L to 8,
        258295L to 2,
        258296L to 4,
        266662L to 9,
        266661L to 9,
        266664L to 3,
        266663L to 3,
        266660L to 2,
        266659L to 2,
        266658L to 2,
        266665L to 5,
        266666L to 5,
        266647L to 5,
        266648L to 6,
        264523L to 4,
        264524L to 2,
        264520L to 2,
        264521L to 4,
        264522L to 5,
        266652L to 5,
        266651L to 5,
        266649L to 5,
        266650L to 6,
        262553L to 6,
        262555L to 6,
        262554L to 6,
        262552L to 4,
        262557L to 7,
        262556L to 4,
        262558L to 7,
        262559L to 7,
        265581L to 7,
        265579L to 2,
        265585L to 4,
        265584L to 4,
        265583L to 7,
        265582L to 7,
        265580L to 4,
        264942L to 4,
        264940L to 7,
        264939L to 4,
        264935L to 3,
        264943L to 2,
        264941L to 7,
        264936L to 3,
        264938L to 4,
        264937L to 2,
        264951L to 4,
        264949L to 7,
        264947L to 4,
        264946L to 2,
        264952L to 2,
        264950L to 7,
        264948L to 4,
        264944L to 3,
        264945L to 3,
        266668L to 5,
        264552L to 2,
        264551L to 4,
        264549L to 5,
        264548L to 2,
        264550L to 4,
        266667L to 6,
        255715L to 1,
        261670L to 1,
        262332L to 1,
        262711L to 1,
        262710L to 6,
        260138L to 1,
        262399L to 1,
        262373L to 1,
        259870L to 1,
        259869L to 1,
        259944L to 1,
        258659L to 1,
        257964L to 1,
        258088L to 1,
        259323L to 1,
        262742L to 1,
        262338L to 1,
        262328L to 1,
        262325L to 1,
        266620L to 6,
        259098L to 4,
        259093L to 2,
        252113L to 1,
        259144L to 1,
        258333L to 6,
        266621L to 2,
        256550L to 5,
        256549L to 3,
        256548L to 2,
        266696L to 3,
        255473L to 9,
        262416L to 1,
        262393L to 1,
        262384L to 1,
        261207L to 1,
        261206L to 2,
        262421L to 1,
        266694L to 4,
        262853L to 1,
        255470L to 1,
        255468L to 4,
        258256L to 1,
        258219L to 1,
        258336L to 6,
        262790L to 1,
        258539L to 3,
        258582L to 1,
        255472L to 5,
        255875L to 6,
        261698L to 1,
        258162L to 1,
        258166L to 1,
        258189L to 3,
        260272L to 1,
        255467L to 1,
        255466L to 2,
        256551L to 4,
        256552L to 1,
        256508L to 6,
        256507L to 6,
        255874L to 3,
        256640L to 3,
        256638L to 3,
        256536L to 3,
        266625L to 3,
        258538L to 1,
        263591L to 1,
        261315L to 1,
        266718L to 3,
        266716L to 2,
        259963L to 1,
        256509L to 8,
        258532L to 1,
        264475L to 1,
        259954L to 1,
        264442L to 1,
        264440L to 1,
        264460L to 3
    )

    private val productMap = mutableMapOf(
        259070L to 1,
        262787L to 1,
        251646L to 130,
        259867L to 180,
        265972L to 1,
        267494L to 1,
        267485L to 2,
        267504L to 1,
        267505L to 1,
        265969L to 1,
        267499L to 2,
        267498L to 1,
        267497L to 1,
        267469L to 2,
        267470L to 1,
        267465L to 3,
        267484L to 2,
        267482L to 2,
        267483L to 2,
        267479L to 2,
        267481L to 2,
        267480L to 2,
        267486L to 2,
        267502L to 1,
        267503L to 1,
        267489L to 1,
        267491L to 1,
        267495L to 1,
        267492L to 1,
        267490L to 1,
        267493L to 1,
        267496L to 1,
        267467L to 2,
        267488L to 2,
        267487L to 2,
        267500L to 1,
        267501L to 1,
        267468L to 2,
        267471L to 1,
        259161L to 2,
        259162L to 4,
        259163L to 4,
        259165L to 3,
        259164L to 3,
        253156L to 2,
        253155L to 3,
        253152L to 2,
        253153L to 3,
        253154L to 4,
        253172L to 2,
        253173L to 3,
        253174L to 4,
        253175L to 3,
        253176L to 2,
        253157L to 2,
        253158L to 3,
        253159L to 4,
        253161L to 2,
        253160L to 3,
        253169L to 4,
        253168L to 3,
        253170L to 3,
        253171L to 2,
        253167L to 2,
        252753L to 2,
        252752L to 2,
        252560L to 2,
        252754L to 3,
        252755L to 3,
        252756L to 3,
        252547L to 3,
        252546L to 3,
        252545L to 2,
        252544L to 2,
        252543L to 2,
        252542L to 2,
        252559L to 3,
        252558L to 3,
        252557L to 2,
        252556L to 2,
        252555L to 2,
        252554L to 2,
        252865L to 2,
        252870L to 3,
        252869L to 3,
        252868L to 2,
        252867L to 2,
        252866L to 2,
        259169L to 3,
        259170L to 3,
        259166L to 2,
        259167L to 4,
        259168L to 4,
        258300L to 6,
        258299L to 8,
        258298L to 8,
        258297L to 8,
        258295L to 2,
        258296L to 4,
        266662L to 9,
        266661L to 9,
        266664L to 3,
        266663L to 3,
        266660L to 2,
        266659L to 2,
        266658L to 2,
        266665L to 5,
        266666L to 5,
        266647L to 5,
        266648L to 6,
        264523L to 4,
        264524L to 2,
        264520L to 2,
        264521L to 4,
        264522L to 5,
        266652L to 5,
        266651L to 5,
        266649L to 5,
        266650L to 6,
        262553L to 6,
        262555L to 6,
        262554L to 6,
        262552L to 4,
        262557L to 7,
        262556L to 4,
        262558L to 7,
        262559L to 7,
        265581L to 7,
        265579L to 2,
        265585L to 4,
        265584L to 4,
        265583L to 7,
        265582L to 7,
        265580L to 4,
        264942L to 4,
        264940L to 7,
        264939L to 4,
        264935L to 3,
        264943L to 2,
        264941L to 7,
        264936L to 3,
        264938L to 4,
        264937L to 2,
        264951L to 4,
        264949L to 7,
        264947L to 4,
        264946L to 2,
        264952L to 2,
        264950L to 7,
        264948L to 4,
        264944L to 3,
        264945L to 3,
        266668L to 5,
        264552L to 2,
        264551L to 4,
        264549L to 5,
        264548L to 2,
        264550L to 4,
        266667L to 6,
        255715L to 1,
        261670L to 1,
        262332L to 1,
        262711L to 1,
        262710L to 6,
        260138L to 1,
        262399L to 1,
        262373L to 1,
        259870L to 1,
        259869L to 1,
        259944L to 1,
        258659L to 1,
        257964L to 1,
        258088L to 1,
        259323L to 1,
        262742L to 1,
        260163L to 1,
        262338L to 1,
        262328L to 1,
        262325L to 1,
        266620L to 6,
        259098L to 4,
        259093L to 2,
        252113L to 1,
        259144L to 1,
        258333L to 6,
        266621L to 2,
        256550L to 5,
        256549L to 3,
        256548L to 2,
        266696L to 3,
        255473L to 9,
        262416L to 1,
        262393L to 1,
        262384L to 1,
        261207L to 1,
        261206L to 2,
        262421L to 1,
        266694L to 4,
        262853L to 1,
        255470L to 1,
        255468L to 4,
        258256L to 1,
        258219L to 1,
        258336L to 6,
        262790L to 1,
        258539L to 3,
        258582L to 1,
        255472L to 5,
        255875L to 6,
        261698L to 1,
        258162L to 1,
        258166L to 1,
        258189L to 3,
        260272L to 1,
        255467L to 1,
        255466L to 2,
        256551L to 4,
        256552L to 1,
        256508L to 6,
        256507L to 6,
        255874L to 3,
        256640L to 3,
        256638L to 3,
        256536L to 3,
        266625L to 3,
        258538L to 1,
        263591L to 1,
        261315L to 1,
        266718L to 3,
        266716L to 2,
        259963L to 1,
        256509L to 8,
        258532L to 1,
        264475L to 1,
        259954L to 1,
        264442L to 1,
        264440L to 1,
        264460L to 3,
        249509L to 12,
        220626L to 14,
        220613L to 13,
        220614L to 7,
        186436L to 12,
        186358L to 12
    )
}