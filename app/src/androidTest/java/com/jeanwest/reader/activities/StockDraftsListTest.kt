package com.jeanwest.reader.activities

import android.view.KeyEvent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import coil.annotation.ExperimentalCoilApi
import org.junit.Rule
import org.junit.Test

class StockDraftsListTest {

    @get:Rule
    val activity = createAndroidComposeRule<StockDraftsList>()

    @Test
    fun test() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MDE2IiwibmFtZSI6Itiq2LPYqiBSRklEINiq2LPYqiBSRklEIiwicm9sZXMiOlsidXNlciJdLCJzY29wZXMiOlsiZXJwIl0sImlhdCI6MTY2OTUzOTgxOCwiZXhwIjoxNzI3NjAwNjE4LCJhdWQiOiJlcnAifQ._UmYJySVtg9GycGkiVLS3NmZlSpyo_0UrkG6s2dKReM"
        activity.runOnUiThread {
            activity.activity.recreate()
        }
        waitForFinishLoading()

        for (i in 0 until 3) {
            activity.onAllNodesWithTag("items")[i].apply {
                assertTextContains("حواله: " + activity.activity.uiList[i].number)
                assertTextContains("تعداد کالاها: " + activity.activity.uiList[i].numberOfItems)
                assertTextContains("تاریخ ثبت: " + activity.activity.uiList[i].date)
                assertTextContains("مبدا: " + activity.activity.uiList[i].source)
                assertTextContains("شرح: " + activity.activity.uiList[i].specification)
                assertTextContains("مقصد: " + activity.activity.uiList[i].destination)
                performClick()
                activity.waitForIdle()
                waitForFinishLoading()
                val products = activity.activity.selectedStockDraftProducts.values.toMutableList()

                for (j in 0 until 3) {
                    activity.onAllNodesWithTag("items")[j].apply {
                        assertTextContains(products[j].KBarCode)
                        assertTextContains(products[j].name)
                        assertTextContains("حواله: " + products[j].draftNumber)
                        assertTextContains("انبار: " + products[j].wareHouseNumber)
                    }
                }
                activity.activity.onKeyDown(4, KeyEvent(KeyEvent.ACTION_DOWN, 4))
                waitForFinishLoading()
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
}