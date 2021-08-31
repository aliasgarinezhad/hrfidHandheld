package com.jeanwest.reader.finding

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import org.hamcrest.CoreMatchers.*
import org.hamcrest.Matcher

import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class FindingActivityTest {

    /*
    Search product by writing K_Bar_Code, k_bar_code and barcode and
    check results (always K_Bar_Code should be showed on text field)
     */
    @Test
    fun firstTest() {

        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.startFindingActivityButton)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109"))
        onView(withId(R.id.finding_search_button)).perform(click())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())

        pressBack()

        onView(withId(R.id.finding_k_bar_code_text)).perform(clearText())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("J64822109801099001"))
        onView(withId(R.id.finding_k_bar_code_text)).perform(pressImeActionButton())
        onView(withId(R.id.finding_k_bar_code_text)).check(matches(withText("64822109")))
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())

        pressBack()

        onView(withId(R.id.finding_k_bar_code_text)).perform(clearText())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109J-8010-F"))
        onView(withId(R.id.finding_search_button)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).check(matches(withText("64822109")))
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())

    }

    //Go to sub activity, back, again go to sub and see if result shown
    @Test
    fun secondTest() {
        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.startFindingActivityButton)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109"))
        onView(withId(R.id.finding_search_button)).perform(click())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())

        pressBack()

        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())
        onView(withId(R.id.findingSubProductSpec)).check(matches(not(withText(""))))
    }

    //Search a product, go to sub activity, back and search another product
    @Test
    fun thirdTest() {

        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.startFindingActivityButton)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109"))
        onView(withId(R.id.finding_search_button)).perform(click())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())

        pressBack()

        onView(withId(R.id.finding_k_bar_code_text)).perform(clearText())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("54822102"))
        onView(withId(R.id.finding_search_button)).perform(click())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())
    }

    @Test
    fun fourthTest() {
        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.startFindingActivityButton)).perform(click())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("64822109"))
        onView(withId(R.id.finding_search_button)).perform(click())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0).perform(click())
        val temp = getText(onView(withId(R.id.findingSubProductSpec)))

        pressBack()

        onView(withId(R.id.finding_k_bar_code_text)).perform(clearText())
        onView(withId(R.id.finding_k_bar_code_text)).perform(typeText("54822102"))
        onView(withId(R.id.finding_search_button)).perform(click())
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0).perform(click())

        if(getText(onView(withId(R.id.findingSubProductSpec))) == temp) {
            assert(false)
        }
    }

    @Test
    fun fifthTest() {
        ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.startFindingActivityButton)).perform(click())

        onView(withId(R.id.finding_k_bar_code_text)).perform(pressKey(280))
        onData(anything()).inAdapterView(withId(R.id.findingListView)).atPosition(0)
            .perform(click())
    }

    private fun getText(matcher: ViewInteraction): String {
        var text = String()
        matcher.perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isAssignableFrom(TextView::class.java)
            }

            override fun getDescription(): String {
                return "Text of the view"
            }

            override fun perform(uiController: UiController, view: View) {
                val tv = view as TextView
                text = tv.text.toString()
            }
        })

        return text
    }
}
