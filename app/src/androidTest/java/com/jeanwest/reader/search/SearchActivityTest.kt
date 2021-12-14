package com.jeanwest.reader.search

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class SearchActivityTest {

    @get:Rule
    var searchActivity = createAndroidComposeRule<SearchActivity>()

    //Search product by writing K_Bar_Code, k_bar_code and barcode and check results (always K_Bar_Code should be showed on text field)
    @Test
    fun searchActivityTest1() {
        val productCode = "11531052"
        val color = "همه رنگ ها"
        val size = "همه سایز ها"
        val warehouse = "انبار"
        val store = "دفتر مركزي"

        searchActivity.onNodeWithTag("SearchProductCodeTextField").performTextClearance()
        searchActivity.onNodeWithTag("SearchProductCodeTextField").performTextInput(productCode)
        searchActivity.onNodeWithTag("SearchProductCodeTextField").performImeAction()
        searchActivity.waitForIdle()

        Thread.sleep(2000)
        searchActivity.waitForIdle()
        Thread.sleep(2000)

        searchActivity.onNodeWithTag("SearchStoreFilterDropDownList").performClick()
        searchActivity.waitForIdle()
        searchActivity.onNodeWithText(store).performClick()
        searchActivity.waitForIdle()

        searchActivity.onNodeWithTag("SearchColorFilterDropDownList").performClick()
        searchActivity.waitForIdle()
        searchActivity.onAllNodesWithText(color)[1].performClick()
        searchActivity.waitForIdle()

        searchActivity.onNodeWithTag("SearchSizeFilterDropDownList").performClick()
        searchActivity.waitForIdle()
        searchActivity.onAllNodesWithText(size)[1].performClick()
        searchActivity.waitForIdle()

        searchActivity.onNodeWithTag("SearchWarehouseFilterDropDownList").performClick()
        searchActivity.waitForIdle()
        searchActivity.onNodeWithText(warehouse).performClick()
        searchActivity.waitForIdle()

        searchActivity.onAllNodesWithTag("SearchItems")[0].assertExists()
        searchActivity.waitForIdle()
    }

    @Test
    fun searchActivityTest2() {
        val productCode = "11531052J-2010-L"

        searchActivity.onNodeWithTag("SearchProductCodeTextField").performTextClearance()
        searchActivity.onNodeWithTag("SearchProductCodeTextField").performTextInput(productCode)
        searchActivity.onNodeWithTag("SearchProductCodeTextField").performImeAction()
        searchActivity.waitForIdle()

        Thread.sleep(2000)
        searchActivity.waitForIdle()
        Thread.sleep(2000)

        searchActivity.onNodeWithText("11531052").assertExists()
        searchActivity.onAllNodesWithTag("SearchItems")[0].assertExists()
        searchActivity.waitForIdle()
    }

    @Test
    fun searchActivityTest3() {

        searchActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        searchActivity.waitForIdle()

        Thread.sleep(2000)
        searchActivity.waitForIdle()
        Thread.sleep(2000)

        searchActivity.onNodeWithText("64822109").assertExists()
        searchActivity.onAllNodesWithTag("SearchItems")[0].assertExists()
        searchActivity.waitForIdle()
    }
}