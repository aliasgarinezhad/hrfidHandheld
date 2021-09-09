package com.jeanwest.reader.transfer

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import org.hamcrest.CoreMatchers
import org.junit.Assert.*
import org.junit.Test

class TransferenceActivityLogInTest {

    //Login with correct values and in the next activity, check whether parameters correct
    @Test
    fun firstTest() {
        ActivityScenario.launch(MainActivity::class.java)
        Espresso.onView(ViewMatchers.withId(R.id.startTransferActivityButton))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.sourceWareHouseTextView))
            .perform(ViewActions.typeText("1"))
        Espresso.onView(ViewMatchers.withId(R.id.desWareHouseTextView))
            .perform(ViewActions.typeText("1706"))
        Espresso.onView(ViewMatchers.withId(R.id.transferExplainationTV))
            .perform(ViewActions.typeText("test"))

        Espresso.onView(ViewMatchers.withId(R.id.loginToTransfer)).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.section_labelT))
            .check(ViewAssertions.matches(ViewMatchers.withText(CoreMatchers.containsString("1"))))
        Espresso.onView(ViewMatchers.withId(R.id.section_labelT))
            .check(ViewAssertions.matches(ViewMatchers.withText(CoreMatchers.containsString("1706"))))
        Espresso.onView(ViewMatchers.withId(R.id.section_labelT))
            .check(ViewAssertions.matches(ViewMatchers.withText(CoreMatchers.containsString("test"))))

    }

    //Login with empty fields and you should face with error
    @Test
    fun secondTest() {
        ActivityScenario.launch(MainActivity::class.java)
        Espresso.onView(ViewMatchers.withId(R.id.startTransferActivityButton))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.sourceWareHouseTextView))
            .perform(ViewActions.typeText(""))
        Espresso.onView(ViewMatchers.withId(R.id.desWareHouseTextView))
            .perform(ViewActions.typeText("1706"))
        Espresso.onView(ViewMatchers.withId(R.id.transferExplainationTV))
            .perform(ViewActions.typeText("test"))

        Espresso.onView(ViewMatchers.withId(R.id.loginToTransfer)).perform(ViewActions.click())

        Thread.sleep(1000)

        Espresso.onView(ViewMatchers.withId(R.id.sourceWareHouseTextView))
            .perform(ViewActions.clearText())
        Espresso.onView(ViewMatchers.withId(R.id.desWareHouseTextView))
            .perform(ViewActions.clearText())
        Espresso.onView(ViewMatchers.withId(R.id.transferExplainationTV))
            .perform(ViewActions.clearText())

        Espresso.onView(ViewMatchers.withId(R.id.sourceWareHouseTextView))
            .perform(ViewActions.typeText("1"))
        Espresso.onView(ViewMatchers.withId(R.id.desWareHouseTextView))
            .perform(ViewActions.typeText(""))
        Espresso.onView(ViewMatchers.withId(R.id.transferExplainationTV))
            .perform(ViewActions.typeText("test"))

        Espresso.onView(ViewMatchers.withId(R.id.loginToTransfer)).perform(ViewActions.click())

        Thread.sleep(1000)

        Espresso.onView(ViewMatchers.withId(R.id.sourceWareHouseTextView))
            .perform(ViewActions.clearText())
        Espresso.onView(ViewMatchers.withId(R.id.desWareHouseTextView))
            .perform(ViewActions.clearText())
        Espresso.onView(ViewMatchers.withId(R.id.transferExplainationTV))
            .perform(ViewActions.clearText())

        Espresso.onView(ViewMatchers.withId(R.id.sourceWareHouseTextView))
            .perform(ViewActions.typeText("1"))
        Espresso.onView(ViewMatchers.withId(R.id.desWareHouseTextView))
            .perform(ViewActions.typeText("1706"))
        Espresso.onView(ViewMatchers.withId(R.id.transferExplainationTV))
            .perform(ViewActions.typeText(""))

        Espresso.onView(ViewMatchers.withId(R.id.loginToTransfer)).perform(ViewActions.click())

        Thread.sleep(1000)

        Espresso.onView(ViewMatchers.withId(R.id.sourceWareHouseTextView))
            .perform(ViewActions.clearText())
        Espresso.onView(ViewMatchers.withId(R.id.desWareHouseTextView))
            .perform(ViewActions.clearText())
        Espresso.onView(ViewMatchers.withId(R.id.transferExplainationTV))
            .perform(ViewActions.clearText())
    }
}