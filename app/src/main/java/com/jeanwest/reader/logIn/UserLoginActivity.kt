package com.jeanwest.reader.logIn

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
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.shared.ErrorSnackBar
import com.jeanwest.reader.shared.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class UserLoginActivity : ComponentActivity() {

    private var username by mutableStateOf("")
    private var password by mutableStateOf("")
    private var state = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Page() }
    }

    private fun signIn() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = memory.edit()

        val logInRequest = Volley.newRequestQueue(this)
        val url = "https://rfid-api.avakatan.ir/login"

        val jsonRequest = object : JsonObjectRequest(Method.POST, url, null, { response ->

            editor.putString("accessToken", response.getString("accessToken"))
            editor.putString("username", username)
            editor.putString("userFullName", response.getString("fullName"))
            editor.putInt("userLocationCode", response.getInt("locationCode"))
            editor.putInt(
                "userWarehouseCode",
                response.getJSONObject("location").getInt("warehouseCode")
            )
            editor.putInt(
                "userWarehouseCode",
                response.getJSONObject("location").getInt("warehouseCode")
            )

            val warehouses = mutableMapOf<String, String>()
            val warehousesJsonArray = response.getJSONArray("warehouses")
            for (i in 0 until warehousesJsonArray.length()) {
                warehouses[warehousesJsonArray.getJSONObject(i).getString("WareHouse_ID")] =
                    warehousesJsonArray.getJSONObject(i).getString("WareHouseTitle")
            }

            editor.putString("userWarehouses", Gson().toJson(warehouses).toString())
            editor.apply()

            val intent =
                Intent(this@UserLoginActivity, MainActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)


        }, {
            when {
                it is NoConnectionError -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
                it.networkResponse.statusCode == 401 -> {

                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "نام کاربری یا رمز عبور اشتباه است",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
                else -> {

                    val error = JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")

                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            error.getString("message"),
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
            }

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
                onClick = { signIn() },
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