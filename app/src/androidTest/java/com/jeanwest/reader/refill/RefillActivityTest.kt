package com.jeanwest.reader.refill

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.jeanwest.reader.sharedClassesAndFiles.hardware.Barcode2D
import org.junit.Rule
import org.junit.Test

@ExperimentalCoilApi
class RefillActivityTest {

    @get:Rule
    val refillActivity = createAndroidComposeRule<RefillActivity>()

    @Test
    fun refillActivityTest1() {

        start()
        clearUserData()
        restart()

        assert(refillActivity.activity.inputBarcodes.size > 50)

        refillActivity.onNodeWithText("پیدا شده: " + 0)
            .assertExists()
        refillActivity.onNodeWithText("خطی: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()

        for (i in 0 until 3) {

            refillActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(refillActivity.activity.refillProducts[i].KBarCode)
                assertTextContains(refillActivity.activity.refillProducts[i].name)
                assertTextContains("انبار: " + refillActivity.activity.refillProducts[i].wareHouseNumber)
                assertTextContains("اسکن: " + 0)
            }
        }

        val scannedProducts = mutableListOf<Product>()
        refillActivity.activity.refillProducts.forEach {
            scannedProducts.add(it)
        }

        barcodeArrayScan(50, scannedProducts)

        restart()

        refillActivity.onNodeWithText("پیدا شده: " + 50)
            .assertExists()
        refillActivity.onNodeWithText("خطی: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()

        restart()

        refillActivity.onNodeWithText("ارسال اسکن شده ها").performClick()
        refillActivity.waitForIdle()

        assert(refillActivity.activity.refillProducts.filter {
            it.scannedBarcodeNumber > 0
        }.size == 50)

        refillActivity.activity.refillProducts.filter {
            it.scannedBarcodeNumber > 0
        }.toMutableList().forEach {
            if (it.wareHouseNumber > 1) {
                assert(it.scannedBarcodeNumber == 2)
            } else {
                assert(it.scannedBarcodeNumber == 1)
            }
        }

        for (i in 0 until 3) {

            refillActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(scannedProducts[i].KBarCode)
                assertTextContains(scannedProducts[i].name)
                assertTextContains("انبار: " + scannedProducts[i].wareHouseNumber)
                assertTextContains("اسکن: " + if (scannedProducts[i].wareHouseNumber > 1) 2 else 1)
            }
        }
    }

    //test output apk file

    private fun restart() {
        refillActivity.activity.runOnUiThread {
            refillActivity.activity.recreate()
        }

        refillActivity.waitForIdle()
        Thread.sleep(4000)
        refillActivity.waitForIdle()
        Thread.sleep(4000)
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
        refillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        refillActivity.waitForIdle()
        Thread.sleep(500)
        refillActivity.waitForIdle()
        Thread.sleep(500)
        refillActivity.waitForIdle()
    }

    private fun start() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        refillActivity.waitForIdle()
        Thread.sleep(4000)
        refillActivity.waitForIdle()
        Thread.sleep(4000)
    }

    private fun clearUserData() {

        val products = mutableListOf<Product>()
        products.addAll(refillActivity.activity.refillProducts)

        products.forEach {
            if (it.scannedBarcodeNumber > 0) {
                refillActivity.activity.clear(it)
                refillActivity.waitForIdle()
            }
        }
    }
}
