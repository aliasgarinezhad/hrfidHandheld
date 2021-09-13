package com.jeanwest.reader.fileAttachment

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import com.jeanwest.reader.theme.MyApplicationTheme

class FileAttachment : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme() {

            }
        }
    }
}