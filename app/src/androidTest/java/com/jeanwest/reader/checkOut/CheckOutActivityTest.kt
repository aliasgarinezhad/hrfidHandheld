package com.jeanwest.reader.checkOut

import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.shared.Product
import com.jeanwest.reader.shared.test.Barcode2D
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class)
class CheckOutActivityTest {

    @get:Rule
    val checkOutActivity = createAndroidComposeRule<CheckOutActivity>()

    //send all stuffs after test
    @Test
    fun test() {

        start()
        clearUserData()
        restart()

        val memory = PreferenceManager.getDefaultSharedPreferences(checkOutActivity.activity)

        val type = object : TypeToken<MutableList<Product>>() {}.type
        val products: MutableList<Product> = Gson().fromJson(
            memory.getString("refillProductsForTest", ""),
            type
        ) ?: mutableListOf()

        Log.e("refillProductsForTest", products.toString())

        assert(products.size > 20)

        checkOutActivity.onAllNodesWithText("هنوز کالایی برای ثبت حواله اسکن نکرده اید")[0].assertExists()

        val scannedProducts = mutableListOf<Product>()
        products.forEach {
            scannedProducts.add(it)
        }

        barcodeArrayScan(20, scannedProducts)

        restart()

        checkOutActivity.onNodeWithText("انتخاب مقصد").performClick()
        checkOutActivity.waitForIdle()
        checkOutActivity.onNodeWithText("انبار VM").performClick()
        checkOutActivity.waitForIdle()

        checkOutActivity.onNodeWithText("ارسال اسکن شده ها").performClick()
        checkOutActivity.waitForIdle()

        assert(checkOutActivity.activity.products.filter {
            it.scannedBarcodeNumber > 0
        }.size == 20)

        checkOutActivity.activity.products.filter {
            it.scannedBarcodeNumber > 0
        }.toMutableList().forEach {
            if (it.wareHouseNumber > 1) {
                assert(it.scannedBarcodeNumber == 2)
            } else {
                assert(it.scannedBarcodeNumber == 1)
            }
        }

        for (i in 0 until 3) {

            checkOutActivity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(checkOutActivity.activity.uiList[i].KBarCode)
                assertTextContains(checkOutActivity.activity.uiList[i].name)
                assertTextContains("انبار: " + checkOutActivity.activity.uiList[i].wareHouseNumber)
                assertTextContains("اسکن: " + if (checkOutActivity.activity.uiList[i].wareHouseNumber > 1) 2 else 1)
            }
        }
    }

    //test output apk file

    private fun restart() {
        checkOutActivity.activity.runOnUiThread {
            checkOutActivity.activity.recreate()
        }

        checkOutActivity.waitForIdle()
        Thread.sleep(4000)
        checkOutActivity.waitForIdle()
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
        checkOutActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        waitForFinishLoading()
    }
    private fun waitForFinishLoading() {
        checkOutActivity.waitForIdle()

        while (checkOutActivity.activity.isDataLoading) {
            Thread.sleep(200)
            checkOutActivity.waitForIdle()
        }
        checkOutActivity.waitForIdle()
    }

    private fun start() {
        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        checkOutActivity.waitForIdle()
        Thread.sleep(4000)
        checkOutActivity.waitForIdle()
        Thread.sleep(4000)
    }

    private fun clearUserData() {

        val products = mutableListOf<Product>()
        products.addAll(checkOutActivity.activity.products)

        products.forEach {
            if (it.scannedBarcodeNumber > 0) {
                checkOutActivity.activity.clear(it)
                checkOutActivity.waitForIdle()
            }
        }
    }
}