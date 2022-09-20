package com.jeanwest.reader.count

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.checkIn.CheckInActivity
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.jeanwest.reader.sharedClassesAndFiles.hardware.Barcode2D
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import org.junit.Rule
import org.junit.Test


class CountActivityImportTestData2 {

    @get:Rule
    val checkInActivity = createAndroidComposeRule<CheckInActivity>()

    //open a check-in, scan some items, check filters
    @Test
    fun test() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        checkInActivity.onNodeWithTag("CheckInTestTag").performClick()
        checkInActivity.waitForIdle()
        checkInActivity.onNodeWithText("بله").performClick()
        checkInActivity.waitForIdle()

        Thread.sleep(5000)
        checkInActivity.waitForIdle()
        Thread.sleep(5000)
        checkInActivity.waitForIdle()
        checkInActivity.activity.saveToMemory()
        Thread.sleep(500)
        checkInActivity.waitForIdle()

    }
}