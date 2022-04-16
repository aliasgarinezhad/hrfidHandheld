package com.jeanwest.reader.manualRefill

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.MainActivity
import org.junit.Rule
import org.junit.Test

@ExperimentalCoilApi
class ManualRefillActivityTest{

    @get:Rule
    val manualRefillWarehouseManagerActivity = createAndroidComposeRule<ManualRefillActivity>()

    //scan some items
    @Test
    fun manualRefillWarehouseManagerActivityTest1() {

        MainActivity.token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjQwMTYsIm5hbWUiOiI0MDE2IiwiaWF0IjoxNjM5NTU3NDA0LCJleHAiOjE2OTc2MTgyMDR9.5baJVQbpJwTEJCm3nW4tE8hW8AWseN0qauIuBPFK5pQ"

        manualRefillWarehouseManagerActivity.waitForIdle()
        Thread.sleep(1000)
        manualRefillWarehouseManagerActivity.waitForIdle()

        manualRefillWarehouseManagerActivity.activity.onKeyDown(280, KeyEvent(KeyEvent.ACTION_DOWN, 280))

        manualRefillWarehouseManagerActivity.waitForIdle()

        manualRefillWarehouseManagerActivity.onNodeWithText("اسکن شده: 1").assertExists()
    }
}

