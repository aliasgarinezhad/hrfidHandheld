package com.jeanwest.reader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException

class MainActivity : AppCompatActivity() {
    
    lateinit var rf: RFIDWithUHFUART
    
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        while (!rf.init()) {
            rf.free()
        }
        if (Build.MODEL == "EXARKXK650") {
            while (!rf.setFrequencyMode(0x08)) {
                rf.free()
            }
        } else if (Build.MODEL == "c72") {
            while (!rf.setFrequencyMode(0x04)) {
                rf.free()
            }
        }
        while (!rf.setRFLink(2)) {
            rf.free()
        }
    }

    fun addNewActivity(view: View?) {
        val intent = Intent(this, addNew::class.java)
        startActivity(intent)
    }

    fun readingActivity(view: View?) {
        val intent = Intent(this, userSpecActivity::class.java)
        startActivity(intent)
    }

    fun findingActivity(view: View?) {
        val intent = Intent(this, FindingProductActivity::class.java)
        startActivity(intent)
    }

    fun settingActivity(view: View?) {
        val intent = Intent(this, AboutUsActivity::class.java)
        startActivity(intent)
    }

    fun advanceSettingButton(view: View?) {
        val intent = Intent(this, LoginToSettingActivity::class.java)
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 4) {
            rf.free()
            finish()
        }
        return true
    }
}