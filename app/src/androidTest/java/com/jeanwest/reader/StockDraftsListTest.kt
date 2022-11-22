package com.jeanwest.reader

import android.view.KeyEvent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import com.jeanwest.reader.activities.MainActivity
import com.jeanwest.reader.activities.StockDraftsList
import org.junit.Rule
import org.junit.Test

class StockDraftsListTest {

    @get:Rule
    val activity = createAndroidComposeRule<StockDraftsList>()

    @Test
    fun test() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"
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