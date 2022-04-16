package com.jeanwest.reader.refill

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.hardware.Barcode2D
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

        refillActivity.onAllNodesWithTag("refillItems")[0].assertExists()
        refillActivity.onNodeWithText("پیدا نشده: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()
        refillActivity.onNodeWithText("خطی: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()

        barcodeScan()
        restart()

        refillActivity.onAllNodesWithTag("refillItems")[0].assertExists()
        refillActivity.onNodeWithText("پیدا نشده: " + (refillActivity.activity.inputBarcodes.size - 3))
            .assertExists()
        refillActivity.onNodeWithText("خطی: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()

        refillActivity.onNodeWithTag("RefillActivityLazyColumn")
            .performScrollToIndex(refillActivity.activity.inputBarcodes.size - 1)

        Thread.sleep(1000)
        refillActivity.waitForIdle()

        refillActivity.onNodeWithText(refillActivity.activity.inputBarcodes[0]).performTouchInput {
            longClick()
        }
        refillActivity.waitForIdle()

        refillActivity.onNodeWithText(refillActivity.activity.inputBarcodes[1]).performClick()
        refillActivity.waitForIdle()

        refillActivity.onNodeWithText("انتخاب همه").assertExists()
        refillActivity.onNodeWithText("بازگشت").assertExists()
        refillActivity.onNodeWithText("پاک کردن").performClick()
        refillActivity.waitForIdle()
        refillActivity.onNodeWithText("بله").performClick()
        refillActivity.waitForIdle()

        refillActivity.onNodeWithText("پیدا نشده: " + (refillActivity.activity.inputBarcodes.size - 1))
            .assertExists()
        refillActivity.onNodeWithText("خطی: " + refillActivity.activity.inputBarcodes.size)
            .assertExists()
    }

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

        refillActivity.activity.inputBarcodes.forEach {
            refillActivity.activity.refillSignedProductCodes.add(it)
        }
        refillActivity.activity.refillClear()

        refillActivity.activity.runOnUiThread {
            refillActivity.activity.recreate()
        }
        refillActivity.waitForIdle()
        Thread.sleep(4000)
        refillActivity.waitForIdle()
    }
}
