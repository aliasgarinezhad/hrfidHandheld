package com.jeanwest.reader.refill

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.jeanwest.reader.sharedClassesAndFiles.Barcode2D
import org.junit.Rule
import org.junit.Test

@ExperimentalCoilApi
class RefillActivityTest {

    @get:Rule
    val refillActivity = createAndroidComposeRule<RefillActivity>()

    //check refill products, scan some items, clear process
    @Test
    fun refillActivityTest1() {

        start()
        clearUserData()

        Thread.sleep(4000)
        refillActivity.waitForIdle()
        Thread.sleep(4000)
        refillActivity.waitForIdle()

        refillActivity.onAllNodesWithTag("items")[0].assertExists()
        refillActivity.onNodeWithText("پیدا نشده: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()
        refillActivity.onNodeWithText("خطی: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()

        barcodeScan()
        restart()

        refillActivity.onAllNodesWithTag("items")[0].assertExists()
        refillActivity.onNodeWithText("پیدا نشده: " + (refillActivity.activity.inputBarcodes.size - 3))
            .assertExists()
        refillActivity.onNodeWithText("خطی: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()
    }

    //test output apk file

    private fun restart() {
        refillActivity.activity.runOnUiThread {
            refillActivity.activity.recreate()
        }

        refillActivity.waitForIdle()
        Thread.sleep(4000)
        refillActivity.waitForIdle()
    }

    private fun barcodeScan() {
        Barcode2D.barcode = refillActivity.activity.inputBarcodes[0]
        refillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        refillActivity.waitForIdle()

        Barcode2D.barcode = refillActivity.activity.inputBarcodes[1]
        refillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        refillActivity.waitForIdle()

        Barcode2D.barcode = refillActivity.activity.inputBarcodes[2]
        refillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
    }

    private fun start() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        refillActivity.waitForIdle()
        Thread.sleep(4000)
        refillActivity.waitForIdle()
    }

    private fun clearUserData() {

        val products = mutableListOf<Product>()
        products.addAll(RefillActivity.refillProducts)

        products.forEach {
            refillActivity.activity.clear(it)
        }

        refillActivity.activity.runOnUiThread {
            refillActivity.activity.recreate()
        }
        refillActivity.waitForIdle()
        Thread.sleep(4000)
        refillActivity.waitForIdle()
    }
}
