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
    var RF: RFIDWithUHFUART? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            RF = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        while (!RF!!.init()) {
            RF!!.free()
        }
        if (Build.MODEL == "EXARKXK650") {
            while (!RF!!.setFrequencyMode(0x08)) {
                RF!!.free()
            }
        } else if (Build.MODEL == "c72") {
            while (!RF!!.setFrequencyMode(0x04)) {
                RF!!.free()
            }
        }
        while (!RF!!.setRFLink(2)) {
            RF!!.free()
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
        val intent = Intent(this, FindingActivity::class.java)
        startActivity(intent)
    }

    fun settingActivity(view: View?) {
        val intent = Intent(this, AboutUsActivity::class.java)
        startActivity(intent)
    }

    fun advanceSettingButton(view: View?) {
        val intent = Intent(this, loginActivity::class.java)
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 4) {
            RF!!.free()
            finish()
        }
        return true
    }
}