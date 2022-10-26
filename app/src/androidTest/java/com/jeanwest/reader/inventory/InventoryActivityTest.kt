package com.jeanwest.reader.inventory

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.shared.Product
import com.jeanwest.reader.shared.test.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)

class InventoryActivityTest {

@get:Rule
var activity = createAndroidComposeRule<InventoryActivity>()

@Test
fun test() {
MainActivity.token =
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

if (activity.activity.inventoryStarted) {

waitForFinishLoading()
activity.onNodeWithText("پایان انبارگردانی").performClick()
activity.waitForIdle()
activity.onNodeWithText("خیر، نتایج پاک شوند").performClick()
activity.waitForIdle()
}

activity.onNodeWithText("شروع انبارگردانی").performClick()
activity.waitForIdle()
activity.onNodeWithText("شروع انبارگردانی جدید").performClick()
activity.waitForIdle()
waitForFinishLoading()

val products = mutableListOf<Product>()
products.addAll(activity.activity.uiList)
val inputProductsNumber = products.filter { it.inventoryConflictType == "کسری" }.size

activity.onNodeWithText("مغایرت ها").performClick()
activity.waitForIdle()
activity.onNodeWithText("کسری: $inputProductsNumber").assertExists()
activity.onNodeWithText("اضافی: 0").assertExists()
for (i in 0 until 3) {

activity.onAllNodesWithTag("items")[i].apply {
assertTextContains(products[i].KBarCode)
assertTextContains(products[i].name)
assertTextContains("موجودی: " + products[i].inventoryNumber)
assertTextContains("کسری: " + products[i].inventoryNumber)
}
}
}

@Test
fun test2() {

MainActivity.token =
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

waitForFinishLoading()
clearScannedData()

val products = mutableListOf<Product>()
products.addAll(activity.activity.uiList)

epcScan(false, products)

activity.onNodeWithText("مغایرت ها").performClick()
activity.waitForIdle()
activity.onNodeWithTag("items").assertDoesNotExist()
activity.onNodeWithText("کسری: 0").assertExists()
activity.onNodeWithText("اضافی: 0").assertExists()
activity.onNodeWithTag("back").performClick()
activity.waitForIdle()
activity.onNodeWithText("100.0%").assertExists()

activity.onNodeWithText("پایان انبارگردانی").performClick()
activity.waitForIdle()
activity.onNodeWithText("بله").performClick()
activity.waitForIdle()
waitForFinishLoading()
}

@Test
fun test3() {

MainActivity.token =
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

activity.onNodeWithText("شروع انبارگردانی").performClick()
activity.waitForIdle()
activity.onNodeWithText("ادامه انبارگردانی قبلی").performClick()
activity.waitForIdle()
waitForFinishLoading()

activity.onNodeWithText("100.0%").assertExists()
activity.onNodeWithText("مغایرت ها").performClick()
activity.waitForIdle()
activity.onNodeWithTag("items").assertDoesNotExist()
activity.onNodeWithText("کسری: 0").assertExists()
activity.onNodeWithText("اضافی: 0").assertExists()

activity.onNodeWithTag("back").performClick()
activity.waitForIdle()
}

@Test
fun test4() {

MainActivity.token =
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

waitForFinishLoading()
val products = mutableListOf<Product>()
products.addAll(activity.activity.inputProducts.values)

activity.onNodeWithText("پایان انبارگردانی").performClick()
activity.waitForIdle()
activity.onNodeWithText("خیر، نتایج پاک شوند").performClick()
activity.waitForIdle()
activity.onNodeWithText("شروع انبارگردانی").performClick()
activity.waitForIdle()
activity.onNodeWithText("شروع انبارگردانی جدید").performClick()
activity.waitForIdle()
waitForFinishLoading()

epcScan(true, products)

waitForFinishLoading()

activity.onNodeWithText("مغایرت ها").performClick()
activity.waitForIdle()

checkResults()
}

private fun clearScannedData() {
activity.onNodeWithTag("clear").performClick()
activity.waitForIdle()
activity.onNodeWithText("بله").performClick()
activity.waitForIdle()
}

private fun waitForFinishLoading() {
Thread.sleep(200)
activity.waitForIdle()

while (activity.activity.loading || activity.activity.scanning) {
Thread.sleep(200)
activity.waitForIdle()
}
}

private fun restart() {
activity.activity.runOnUiThread {
activity.activity.recreate()
}
}

private fun checkResults() {

activity.activity.inventoryResult.forEach {

if (it.value.inventoryNumber >= 3) {
assert(activity.activity.inventoryResult[it.value.KBarCode]!!.inventoryConflictNumber == 3)
assert(activity.activity.inventoryResult[it.value.KBarCode]!!.inventoryConflictType == "کسری")
} else if (it.value.inventoryNumber == 2) {
assert(activity.activity.inventoryResult[it.value.KBarCode]!!.inventoryConflictNumber == 3)
assert(activity.activity.inventoryResult[it.value.KBarCode]!!.inventoryConflictType == "اضافی")
} else {
assert(activity.activity.inventoryResult[it.value.KBarCode]!!.inventoryConflictNumber == 1)
assert(activity.activity.inventoryResult[it.value.KBarCode]!!.inventoryConflictType == "اضافی")
}
}

val shortageProductList = activity.activity.inventoryResult.values.filter {
it.inventoryConflictType == "کسری"
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
assertTextContains("موجودی: " + shortageProductList[i].inventoryNumber)
assertTextContains("کسری: " + shortageProductList[i].inventoryConflictNumber)
}
}

val additionalProductList = activity.activity.inventoryResult.values.filter {
it.inventoryConflictType == "اضافی"
}.toMutableList()

additionalProductList.sortBy {
it.productCode
}
additionalProductList.sortBy {
it.name
}

activity.onNodeWithTag("scanFilterDropDownList").performClick()
activity.waitForIdle()
activity.onNodeWithText("اضافی").performClick()
activity.waitForIdle()

for (i in 0 until 3) {

activity.onAllNodesWithTag("items")[i].apply {
assertTextContains(additionalProductList[i].KBarCode)
assertTextContains(additionalProductList[i].name)
assertTextContains("موجودی: " + additionalProductList[i].inventoryNumber)
assertTextContains("اضافی: " + additionalProductList[i].inventoryConflictNumber)
}
}
}

private fun epcScan(withDifference: Boolean, rfidProducts: MutableList<Product>) {

RFIDWithUHFUART.uhfTagInfo.clear()
RFIDWithUHFUART.writtenUhfTagInfo.tid = ""
RFIDWithUHFUART.writtenUhfTagInfo.epc = ""

val uhfTagInfo1 = UHFTAGInfo()
uhfTagInfo1.epc = "30123456789"
RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo1)

rfidProducts.forEach {

if (withDifference) {
if (it.inventoryNumber >= 3) {
for (i in 0 until it.inventoryNumber - 3) {
val uhfTagInfo = UHFTAGInfo()
uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
}
} else if (it.inventoryNumber == 2) {
for (i in 0 until it.inventoryNumber + 3) {
val uhfTagInfo = UHFTAGInfo()
uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
}
} else {
for (i in 0 until it.inventoryNumber + 1) {
val uhfTagInfo = UHFTAGInfo()
uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
}
}
} else {
for (i in 0 until it.inventoryNumber) {
val uhfTagInfo = UHFTAGInfo()
uhfTagInfo.epc = epcGenerator(48, 0, 0, 101, it.rfidKey, i.toLong())
RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)
}
}
}

activity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
activity.waitForIdle()

Thread.sleep(1000)
activity.waitForIdle()

activity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
waitForFinishLoading()
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


//test zone by define one product as zone and check list
