package com.jeanwest.reader.checkIn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import org.junit.Rule
import org.junit.Test

class GetCheckInPropertiesActivityTest {

    @get:Rule
    var getCheckInPropertiesActivityTest =
        createAndroidComposeRule<GetCheckInPropertiesActivity>()

    @Test
    fun getBarcodesByCheckInNumberActivityTest1() {

        val checkInCodes = mutableListOf(
            DraftProperties(
                number = 410693089,
                date = "1401/5/29",
                numberOfItems = 1,
                source = 1709,
                destination = 1707
            ),
            DraftProperties(
                number = 410693090,
                date = "1401/5/29",
                numberOfItems = 1,
                source = 1709,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039336,
                date = "1401/5/29",
                numberOfItems = 310,
                source = 44,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039343,
                date = "1401/5/29",
                numberOfItems = 51,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039381,
                date = "1401/5/30",
                numberOfItems = 72,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039384,
                date = "1401/5/30",
                numberOfItems = 57,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039386,
                date = "1401/5/30",
                numberOfItems = 52,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039467,
                date = "1401/5/30",
                numberOfItems = 30,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039470,
                date = "1401/5/30",
                numberOfItems = 21,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039474,
                date = "1401/5/30",
                numberOfItems = 27,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039480,
                date = "1401/5/30",
                numberOfItems = 58,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039484,
                date = "1401/5/30",
                numberOfItems = 33,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039487,
                date = "1401/5/30",
                numberOfItems = 24,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039488,
                date = "1401/5/30",
                numberOfItems = 24,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039500,
                date = "1401/5/30",
                numberOfItems = 54,
                source = 1911,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039507,
                date = "1401/5/30",
                numberOfItems = 80,
                source = 44,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039508,
                date = "1401/5/30",
                numberOfItems = 97,
                source = 44,
                destination = 1707
            ),
            DraftProperties(
                number = 114100039520,
                date = "1401/5/30",
                numberOfItems = 70,
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

        for (i in 0 until 3) {

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
