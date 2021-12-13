package com.jeanwest.reader.count

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jeanwest.reader.MainActivity
import org.junit.Rule
import org.junit.Test

class SearchActivityTest {

    @get:Rule
    var activity = createAndroidComposeRule<MainActivity>()

    @Test
    fun fileAttachmentFirstTest() {
        activity.onNodeWithText("شمارش").performClick()
    }

}