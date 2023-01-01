package com.jeanwest.reader.activities

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.test.Barcode2D
import org.junit.Rule
import org.junit.Test

@ExperimentalCoilApi

class KioskTest {

    @get:Rule
    var activity = createAndroidComposeRule<Kiosk>()

    @Test
    fun test() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"
        waitForFinishLoading()

        barcodeScan("11531052J-2010-L")
        waitForFinishLoading()

        activity.onNodeWithText(activity.activity.uiList[0].name).assertExists()
        activity.onNodeWithText("کد فرعی: " + activity.activity.uiList[0].productCode).assertExists()
        activity.onNodeWithText("قیمت: " + activity.activity.uiList[0].originalPrice).assertExists()
        activity.onNodeWithText("قیمت فروش: " + activity.activity.uiList[0].salePrice).assertExists()

        val maxItems = if (activity.activity.uiList.size > 3) 3 else activity.activity.uiList.size
        for (i in 0 until maxItems) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains("رنگ: " + activity.activity.uiList[i].color)
                assertTextContains("سایز: " + activity.activity.uiList[i].size)
                assertTextContains("انبار: " + activity.activity.uiList[i].wareHouseNumber)
                assertTextContains("فروشگاه: " + activity.activity.uiList[i].storeNumber)
            }
        }
        activity.activity.onKeyDown(4, KeyEvent(KeyEvent.ACTION_DOWN, 4))
        activity.waitForIdle()
        activity.onNodeWithTag("CustomTextField").performTextClearance()
        activity.onNodeWithTag("CustomTextField").performTextInput("11531052")
        activity.onNodeWithTag("CustomTextField").performImeAction()
        waitForFinishLoading()
        activity.onNodeWithText(activity.activity.uiList[0].name).assertExists()
        activity.onNodeWithText("کد فرعی: " + activity.activity.uiList[0].productCode).assertExists()
        activity.onNodeWithText("قیمت: " + activity.activity.uiList[0].originalPrice).assertExists()
        activity.onNodeWithText("قیمت فروش: " + activity.activity.uiList[0].salePrice).assertExists()

        for (i in 0 until maxItems) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains("رنگ: " + activity.activity.uiList[i].color)
                assertTextContains("سایز: " + activity.activity.uiList[i].size)
                assertTextContains("انبار: " + activity.activity.uiList[i].wareHouseNumber)
                assertTextContains("فروشگاه: " + activity.activity.uiList[i].storeNumber)
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
}
