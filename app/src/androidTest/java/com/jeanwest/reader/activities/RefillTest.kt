package com.jeanwest.reader.activities

import android.view.KeyEvent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.test.Barcode2D
import org.junit.Rule
import org.junit.Test

@ExperimentalCoilApi
class RefillTest {

    @get:Rule
    val activity = createAndroidComposeRule<Refill>()

    //send all stuffs after test
    @Test
    fun test1() {

        start()
        clearUserData()
        restart()

        assert(activity.activity.inputBarcodes.size > 20)

        activity.onNodeWithText("پیدا شده: " + 0)
            .assertExists()
        activity.onNodeWithText("خطی: " + activity.activity.inputBarcodes.size)
            .assertExists()

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(activity.activity.refillProducts[i].KBarCode)
                assertTextContains(activity.activity.refillProducts[i].name)
                assertTextContains("انبار: " + activity.activity.refillProducts[i].wareHouseNumber)
                assertTextContains("اسکن: " + 0)
            }
        }

        val scannedProducts = mutableListOf<Product>()
        activity.activity.refillProducts.forEach {
            scannedProducts.add(it)
        }

        barcodeScan("123456")

        barcodeArrayScan(20, scannedProducts)

        restart()

        activity.onNodeWithText("پیدا شده: " + 20)
            .assertExists()
        activity.onNodeWithText("خطی: " + activity.activity.inputBarcodes.size)
            .assertExists()

        restart()

        activity.onNodeWithText("ارسال اسکن شده ها").performClick()
        activity.waitForIdle()

        assert(activity.activity.refillProducts.filter {
            it.scannedBarcodeNumber > 0
        }.size == 20)

        activity.activity.refillProducts.filter {
            it.scannedBarcodeNumber > 0
        }.toMutableList().forEach {
            if (it.wareHouseNumber > 1) {
                assert(it.scannedBarcodeNumber == 2)
            } else {
                assert(it.scannedBarcodeNumber == 1)
            }
        }

        val filteredUiList = activity.activity.uiList.filter {
            it.scannedBarcodeNumber > 0
        }

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(filteredUiList[i].KBarCode)
                assertTextContains(filteredUiList[i].name)
                assertTextContains("انبار: " + filteredUiList[i].wareHouseNumber)
                assertTextContains("اسکن: " + if (filteredUiList[i].wareHouseNumber > 1) 2 else 1)
            }
        }
    }

//test output apk file

    private fun restart() {
        activity.activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()
    }

    private fun barcodeArrayScan(number: Int, scannedProducts: MutableList<Product>) {

        for (i in 0 until number) {

            if (scannedProducts[i].wareHouseNumber > 1) {
                barcodeScan(scannedProducts[i].KBarCode)
                barcodeScan(scannedProducts[i].KBarCode)
            } else {
                barcodeScan(scannedProducts[i].KBarCode)
            }
        }
    }

    private fun barcodeScan(barcode: String) {
        Barcode2D.barcode = barcode
        activity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        waitForFinishLoading()
    }

    private fun waitForFinishLoading() {

        activity.waitForIdle()
        while (activity.activity.loading) {
            Thread.sleep(200)
            activity.waitForIdle()
        }
    }

    private fun start() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        waitForFinishLoading()
    }

    private fun clearUserData() {

        val products = mutableListOf<Product>()
        products.addAll(activity.activity.refillProducts)

        products.forEach {
            if (it.scannedBarcodeNumber > 0) {
                activity.activity.clear(it)
                activity.waitForIdle()
            }
        }
    }
}
