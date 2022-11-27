package com.jeanwest.reader.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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
import androidx.preference.PreferenceManager
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.management.ErrorSnackBar
import com.jeanwest.reader.ui.MyApplicationTheme
import com.jeanwest.reader.data.userLogin

class UserLogin : ComponentActivity() {

    private var username by mutableStateOf("")
    private var password by mutableStateOf("")
    private var state = SnackbarHostState()
    private lateinit var queue : RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Page() }
        queue = Volley.newRequestQueue(this)
    }

    private fun login() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = memory.edit()

        userLogin(queue, state, username, password, { token, fullName, assignedLocation, assignedWarehouse, warehouses ->

            editor.putString("accessToken", token)
            editor.putString("username", username)
            editor.putString("userFullName", fullName)
            editor.putInt("userLocationCode", assignedLocation)
            editor.putInt("userWarehouseCode", assignedWarehouse)
            editor.putString("userWarehouses", Gson().toJson(warehouses).toString())
            editor.apply()
            val intent =
                Intent(this@UserLogin, MainActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }, {
            editor.putString("accessToken", "")
            editor.putString("username", "")
            editor.apply()
        })
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun Content() {

        Column(modifier = Modifier.fillMaxSize()) {
            UsernameTextField()
            PasswordTextField()
            Button(
                onClick = { login() },
                modifier = Modifier
                    .padding(top = 20.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
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
