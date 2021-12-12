package com.jeanwest.reader

import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.theme.MyApplicationTheme
import org.json.JSONObject

class UserLoginActivity : ComponentActivity() {

    private var username by mutableStateOf("")
    private var password by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Page() }
    }

    private fun signIn() {

        val memory: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor: SharedPreferences.Editor = memory.edit()

        val logInRequest = Volley.newRequestQueue(this)
        val url = "http://rfid-api-0-1.avakatan.ir/login"

        val jsonRequest = object : JsonObjectRequest(Method.POST, url, null, { response ->

            editor.putString("accessToken", response.getString("accessToken"))
            editor.putString("username", username)
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
                body.put("username", username)
                body.put("password", password)
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

    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                )
            }
        }
    }

    @Composable
    fun Content() {

        Column (modifier = Modifier.fillMaxSize()) {
            UsernameTextField()
            PasswordTextField()
            Button(onClick = {signIn()}, modifier = Modifier.padding(top = 20.dp).align(Alignment.CenterHorizontally)) {
                Text(text = "ورود")
            }
        }
    }

    @Composable
    fun AppBar() {

        TopAppBar(

            title = {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ورود به حساب کاربری", textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }

    @Composable
    fun UsernameTextField() {

        OutlinedTextField(
            value = username, onValueChange = {
                username = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth(),
            label = { Text(text = "نام کاربری") }
        )
    }

    @Composable
    fun PasswordTextField() {

        OutlinedTextField(
            value = password, onValueChange = {
                password = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth(),
            label = { Text(text = "رمز عبور") }
        )
    }

}