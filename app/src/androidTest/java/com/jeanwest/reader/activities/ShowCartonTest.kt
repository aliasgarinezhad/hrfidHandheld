package com.jeanwest.reader.activities

import android.view.KeyEvent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.testData.carton
import com.jeanwest.reader.testData.cartonProducts
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test


class ShowCartonTest {

    @get:Rule
    val activity = createAndroidComposeRule<ShowCarton>()

    @Test
    fun test() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"
        activity.activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()

        val kbarcodes = mutableListOf<String>()
        val productJson = JSONArray(cartonProducts)

        for (i in 0 until productJson.length()) {
            kbarcodes.add(
                productJson.getJSONObject(i).getString("kbarcode")
            )
        }

        barcodeScan(carton.number)
        waitForFinishLoading()

        activity.onNodeWithText("مجموع: ${carton.numberOfItems}").assertExists()
        activity.onNodeWithText("انبار جاری: " + carton.source).assertExists()
        activity.onNodeWithText("شرح: " + carton.specification).assertExists()

        activity.activity.inputProducts.forEach {
            assert(it.value.draftNumber == kbarcodes.count { it1 ->
                it.value.KBarCode == it1
            })
        }

        val products = mutableListOf<Product>()
        products.addAll(activity.activity.inputProducts.values)
        products.sortBy {
            it.productCode
        }
        products.sortBy {
            it.name
        }

        for (i in 0 until 3) {

            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains(products[i].KBarCode)
                assertTextContains(products[i].name)
                assertTextContains("موجودی: " + products[i].draftNumber)
                assertTextContains("سایز: " + products[i].size)
            }
        }
    }

    private fun waitForFinishLoading() {

        activity.waitForIdle()
        while (activity.activity.loading) {
            Thread.sleep(200)
            activity.waitForIdle()
        }
    }

    private fun barcodeScan(barcode: String) {
        Barcode2D.barcode = barcode
        activity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))
        waitForFinishLoading()
    }
}
