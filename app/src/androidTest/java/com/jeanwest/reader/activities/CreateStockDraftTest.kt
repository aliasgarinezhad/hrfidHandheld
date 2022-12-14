package com.jeanwest.reader.activities

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.test.Barcode2D
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class)
class CreateStockDraftTest {

    @get:Rule
    val activity = createAndroidComposeRule<CreateStockDraft>()

    //send all stuffs after test
    @Test
    fun test() {

        start()
        clearUserData()
        restart()

        val memory = PreferenceManager.getDefaultSharedPreferences(activity.activity)

        val type = object : TypeToken<MutableList<Product>>() {}.type
        val products: MutableList<Product> = Gson().fromJson(
            memory.getString("refillProductsForTest", ""),
            type
        ) ?: mutableListOf()

        assert(products.size > 20)

        activity.onAllNodesWithText("هنوز کالایی برای ثبت حواله اسکن نکرده اید")[0].assertExists()

        val scannedProducts = mutableListOf<Product>()
        products.forEach {
            scannedProducts.add(it)
        }

        barcodeScan("123456")

        barcodeArrayScan(20, scannedProducts)

        restart()

        activity.onNodeWithText("ثبت حواله").performClick()
        activity.waitForIdle()

        assert(activity.activity.products.filter {
            it.scannedBarcodeNumber > 0
        }.size == 20)

        activity.activity.products.filter {
            it.scannedBarcodeNumber > 0
        }.toMutableList().forEach {
            if (it.wareHouseNumber > 1) {
                assert(it.scannedBarcodeNumber == 2)
            } else {
                assert(it.scannedBarcodeNumber == 1)
            }
        }

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(activity.activity.uiList[i].KBarCode)
                assertTextContains(activity.activity.uiList[i].name)
                assertTextContains("انبار: " + activity.activity.uiList[i].wareHouseNumber)
                assertTextContains("اسکن: " + if (activity.activity.uiList[i].wareHouseNumber > 1) 2 else 1)
            }
        }
    }

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
        activity.activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()
    }

    private fun clearUserData() {

        val products = mutableListOf<Product>()
        products.addAll(activity.activity.products)

        products.forEach {
            if (it.scannedBarcodeNumber > 0) {
                activity.activity.clear(it)
                activity.waitForIdle()
            }
        }
    }
}
