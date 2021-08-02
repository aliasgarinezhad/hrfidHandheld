package com.jeanwest.reader

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginToSettingActivity : AppCompatActivity() {

    private lateinit var password: EditText
    private lateinit var feedback: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        password = findViewById(R.id.passwordText)
        feedback = findViewById(R.id.feedback)
    }

    fun loginButton(view: View?) {
        if (password.text.toString() == "123456") {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        } else {
            feedback.text = "رمز عبور اشتباه است!"
            feedback.setTextColor(Color.RED)
        }
    }
}