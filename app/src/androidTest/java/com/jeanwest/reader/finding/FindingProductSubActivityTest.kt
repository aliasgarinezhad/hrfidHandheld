package com.jeanwest.reader.finding

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import org.junit.Assert.*
import org.junit.Test
import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.hamcrest.CoreMatchers.*

class FindingProductSubActivityTest{

    //Find special product and see results (found EPC, product specification and image matching and beep)
    @Test
    fun firstTest() {
        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.startFindingActivityButton)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109"))
        onView(withId(R.id.finding_k_bar_code_text)).perform(pressImeActionButton())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0).perform(click())

        RFIDWithUHFUART.uhfTagInfo.clear()

        val uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "30C01940007F3C800002BF30"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        onView(withId(R.id.findingSubProductSpec)).check(matches(not(withText(""))))

        onView(withId(R.id.finding_sub_epc_text)).perform(pressKey(280))
        Thread.sleep(2000)
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF30"))))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("1")))

        onView(withId(R.id.finding_sub_epc_text)).perform(pressKey(280))
        Thread.sleep(100)
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF30"))))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("1")))
    }

    //Find special product that its number is bigger than 3 and test clear button
    @Test
    fun secondTest() {
        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.startFindingActivityButton)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109"))
        onView(withId(R.id.finding_k_bar_code_text)).perform(pressImeActionButton())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0).perform(click())

        RFIDWithUHFUART.uhfTagInfo.clear()

        var uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "30C01940007F3C800002BF30"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "30C01940007F3C800002BF31"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "30C01940007F3C800002BF32"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        onView(withId(R.id.finding_sub_epc_text)).perform(pressKey(280))
        Thread.sleep(2000)
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF30"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF31"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF32"))))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("3")))

        onView(withId(R.id.finding_sub_epc_text)).perform(pressKey(280))

        Thread.sleep(200)

        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF30"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF31"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF32"))))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("3")))

        onView(withId(R.id.finding_sub_clear_button)).perform(click())
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText("")))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("0")))

    }

    //Do last again and test check box
    @Test
    fun thirdTest() {
        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.startFindingActivityButton)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109"))
        onView(withId(R.id.finding_k_bar_code_text)).perform(pressImeActionButton())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0).perform(click())

        RFIDWithUHFUART.uhfTagInfo.clear()

        var uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "30C01940007F3C800002BF30"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "30C01940007F3C800002BF31"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        uhfTagInfo = UHFTAGInfo()
        uhfTagInfo.epc = "30C01940007F3C800002BF32"
        RFIDWithUHFUART.uhfTagInfo.add(uhfTagInfo)

        onView(withId(R.id.finding_sub_epc_text)).perform(pressKey(280))
        Thread.sleep(2000)
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF30"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF31"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF32"))))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("3")))

        onView(withId(R.id.finding_sub_epc_text)).perform(pressKey(280))

        Thread.sleep(200)

        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF30"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF31"))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(containsString("30C01940007F3C800002BF32"))))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("3")))

        RFIDWithUHFUART.uhfTagInfo.clear()
        onView(withId(R.id.finding_sub_epc_text)).perform(pressKey(280))
        Thread.sleep(2000)

        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(not(containsString("30C01940007F3C800002BF30")))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(not(containsString("30C01940007F3C800002BF31")))))
        onView(withId(R.id.finding_sub_epc_text)).check(matches(withText(not(containsString("30C01940007F3C800002BF32")))))
        onView(withId(R.id.finding_sub_number_text)).check(matches(withText("0")))
    }
}