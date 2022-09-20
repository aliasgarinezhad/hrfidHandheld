package com.jeanwest.reader.centralWarehouseCheckIn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.checkIn.DraftProperties
import com.jeanwest.reader.checkIn.GetCheckInPropertiesActivity
import org.junit.Rule
import org.junit.Test

class CentralCheckInImportTestData1 {

    @get:Rule
    var getCheckInPropertiesActivityTest =
        createAndroidComposeRule<GetCheckInPropertiesActivity>()

    @Test
    fun test() {

        val checkInCodes = mutableListOf(
            DraftProperties(
                number = 119945,
                date = "1399/1/2",
                numberOfItems = 125,
                source = 44,
                destination = 1707
            )
        )

        clearUserData()

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        getCheckInPropertiesActivityTest.waitForIdle()

        checkInCodes.forEach {
            getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
                .performTextClearance()
            getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
                .performTextInput(it.number.toString())
            getCheckInPropertiesActivityTest.onNodeWithTag("CustomTextField")
                .performImeAction()
            getCheckInPropertiesActivityTest.waitForIdle()
            Thread.sleep(500)
            getCheckInPropertiesActivityTest.waitForIdle()
        }

        Thread.sleep(500)
        getCheckInPropertiesActivityTest.waitForIdle()
        Thread.sleep(500)

        assert(checkInCodes == getCheckInPropertiesActivityTest.activity.uiList.toMutableList())

        for (i in 0 until 1) {

            getCheckInPropertiesActivityTest.onAllNodesWithTag("item")[i].apply {
                onChildren()[0].assertTextContains("حواله: " + checkInCodes[i].number)
                onChildren()[1].assertTextContains("تعداد کالاها: " + checkInCodes[i].numberOfItems)
                onChildren()[2].assertTextContains("مبدا: " + checkInCodes[i].source)
                onChildren()[3].assertTextContains("تاریخ ثبت: " + checkInCodes[i].date)
                onChildren()[4].assertTextContains("مقصد: " + checkInCodes[i].destination)
            }
        }
        getCheckInPropertiesActivityTest.waitForIdle()
    }

    private fun clearUserData() {
        val uiListCopy = mutableListOf<DraftProperties>()
        uiListCopy.addAll(getCheckInPropertiesActivityTest.activity.uiList)
        uiListCopy.forEach {
            getCheckInPropertiesActivityTest.activity.clear(it)
            getCheckInPropertiesActivityTest.waitForIdle()
        }
    }
}
