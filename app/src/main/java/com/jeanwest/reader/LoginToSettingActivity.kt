package com.jeanwest.reader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.add.SettingActivity
import kotlinx.android.synthetic.main.activity_login_setting.*

class LoginToSettingActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_setting)
        login_setting_toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    fun loginButton(view: View?) {
        if (login_password_text.text.toString() == "123456") {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        } else {
            Toast.makeText(this, "رمز عبور اشتباه است", Toast.LENGTH_LONG)
        }
    }
}