package com.jeanwest.reader.fileAttachment

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jeanwest.reader.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FileAttachmentTest {

    @get:Rule
    var activity = createAndroidComposeRule<MainActivity>()

    @Test
    fun fileAttachmentFirstTest() {
        activity.onNodeWithText("پیوست فایل").performClick()
    }

}