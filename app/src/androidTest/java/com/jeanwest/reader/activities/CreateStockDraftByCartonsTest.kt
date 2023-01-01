package com.jeanwest.reader.activities

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.data.Carton
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.test.Barcode2D
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CreateStockDraftByCartonsTest {

    @get:Rule
    val activity = createAndroidComposeRule<CreateStockDraftByCartons>()

    //send all stuffs after test
    @Test
    fun test() {

        start()
        clearUserData()
        restart()

        val cartons = mutableListOf("CN3114100045356", "CN3114100045354", "CN3114100045346")

        activity.onAllNodesWithText("هنوز کارتنی برای ثبت حواله اسکن نکرده اید")[0].assertExists()

        barcodeScan("123456")

        cartons.forEach {
            barcodeScan(it)
            barcodeScan(it)
            waitForFinishLoading()
        }

        activity.onNodeWithText("مجموع: " + cartons.size).assertExists()

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(activity.activity.uiList[i].number)
                assertTextContains("انبار جاری: " + activity.activity.uiList[i].source)
                assertTextContains("تنوع جنس: " + activity.activity.uiList[i].barcodeTable.distinct().size)
                assertTextContains("جمع اجناس: " + activity.activity.uiList[i].numberOfItems)
            }
        }

        restart()

        activity.onNodeWithText("ثبت حواله").performClick()
        activity.waitForIdle()

        assert(activity.activity.cartons.size == 3)

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(activity.activity.uiList[i].number)
                assertTextContains("انبار جاری: " + activity.activity.uiList[i].source)
                assertTextContains("تنوع جنس: " + activity.activity.uiList[i].barcodeTable.distinct().size)
                assertTextContains("جمع اجناس: " + activity.activity.uiList[i].numberOfItems)
            }
        }
    }

    private fun restart() {
        activity.activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()
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
        activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()
    }

    private fun clearUserData() {

        val products = mutableListOf<Carton>()
        products.addAll(activity.activity.uiList)

        products.forEach {
            activity.activity.clear(it)
            activity.waitForIdle()
        }
    }
}

