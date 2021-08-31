package com.jeanwest.reader

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class UserLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_login)
    }

    fun signIn(view: View) {

        val memory: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor: SharedPreferences.Editor = memory.edit()

        val username = findViewById<EditText>(R.id.userNameEditText)
        val password = findViewById<EditText>(R.id.passwordEditText)

        val logInRequest = Volley.newRequestQueue(this)
        val url = "http://rfid-api-0-1.avakatan.ir:3100/login"

        val jsonRequest = object : JsonObjectRequest(Method.POST, url, null, { response ->

            editor.putString("accessToken", response.getString("accessToken"))
            editor.putString("username", username.editableText.toString())
            editor.apply()
            finish()

        },{ response ->

            Toast.makeText(this, response.toString(), Toast.LENGTH_LONG).show()
            editor.putString("accessToken", "")
            editor.putString("username", "")
            editor.apply()

        }) {

            override fun getBody(): ByteArray {
                val body = JSONObject()
                body.put("username", username.editableText.toString())
                body.put("password", password.editableText.toString())
                return body.toString().toByteArray()
            }

            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                return params
            }
        }
        logInRequest.add(jsonRequest)
    }
}