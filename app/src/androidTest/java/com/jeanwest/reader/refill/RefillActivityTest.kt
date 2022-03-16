package com.jeanwest.reader.refill

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import org.junit.Rule
import org.junit.Test

@ExperimentalCoilApi
class RefillActivityTest {

    @get:Rule
    val refillActivity = createAndroidComposeRule<RefillActivity>()

    //check refill products
    @Test
    fun refillActivityTest1() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        refillActivity.waitForIdle()
        Thread.sleep(1000)
        refillActivity.waitForIdle()

        refillActivity.onNodeWithText("پیدا نشده: 36").assertExists()
        refillActivity.onNodeWithText("خطی: 36").assertExists()
    }

    //scan some items and check all statistics
    @Test
    fun refillActivityTest2() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        refillActivity.waitForIdle()
        Thread.sleep(1000)
        refillActivity.waitForIdle()

        refillActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        refillActivity.waitForIdle()

        refillActivity.onNodeWithText("پیدا نشده: 36").assertExists()
        refillActivity.onNodeWithText("خطی: 36").assertExists()
    }
}
