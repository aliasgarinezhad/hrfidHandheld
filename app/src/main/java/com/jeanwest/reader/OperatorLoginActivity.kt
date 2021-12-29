package com.jeanwest.reader

import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.theme.MyApplicationTheme
import org.json.JSONObject

class OperatorLoginActivity : ComponentActivity() {

    private var password by mutableStateOf("")
    private var username by mutableStateOf("")
    private var deviceSerialNumber by mutableStateOf("")
    private var deviceId by mutableStateOf("")
    private var iotToken by mutableStateOf("")
    private var advanceSettingToken = ""
    private val apiTimeout = 30000

    override fun onResume() {
        super.onResume()
        setContent { Page() }
    }

    //868969010014520

    private fun registerDeviceToIotHub() {
        val url = "https://wavecountbackend.azurewebsites.net/api/devices/handheld"
        val request = object : JsonObjectRequest(Method.POST, url, null, {
            deviceId = it.getString("deviceId")
            iotToken = it.getJSONObject("authentication").getJSONObject("symmetricKey")
                .getString("primaryKey")
            saveToMemory()
            Toast.makeText(this, "دستگاه با موفقیت رجیستر شد", Toast.LENGTH_SHORT).show()

            val nextActivityIntent = Intent(this, MainActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(nextActivityIntent)
        }, {
            Toast.makeText(this, "خطا در رجیستر کردن دستگاه", Toast.LENGTH_SHORT).show()
        }) {

            override fun getHeaders(): MutableMap<String, String> {
                val header = mutableMapOf<String, String>()
                header["accept"] = "application/json"
                header["Content-Type"] = "application/json"
                header["Authorization"] = "Bearer $advanceSettingToken"
                return header
            }

            override fun getBody(): ByteArray {
                val body = JSONObject()
                body.put("serialNumber", deviceSerialNumber)
                return body.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun advanceUserAuthenticate() {

        val url = "https://wavecountbackend.azurewebsites.net/api/auth/login"
        val request = object : JsonObjectRequest(Method.POST, url, null, fun(it) {
            advanceSettingToken = it.getString("accessToken")
            registerDeviceToIotHub()
        }, {
            Toast.makeText(this, "رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
        }) {

            override fun getHeaders(): MutableMap<String, String> {
                val header = mutableMapOf<String, String>()
                header["accept"] = "application/json"
                header["Content-Type"] = "application/json"
                return header
            }

            override fun getBody(): ByteArray {
                val body = JSONObject()
                body.put("user", username)
                body.put("password", password)
                return body.toString().toByteArray()
            }
        }

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()

        memoryEditor.putString("deviceId", deviceId)
        memoryEditor.putString("iotToken", iotToken)
        memoryEditor.putString("deviceSerialNumber", deviceSerialNumber)
        memoryEditor.apply()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 4) {
            back()
        }
        return true
    }

    private fun back() {
        saveToMemory()
        finish()
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 10.dp)
        ) {

            UsernameTextField()
            PasswordTextField()
            ImeiTextField()

            Button(modifier = Modifier
                .padding(top = 20.dp)
                .align(Alignment.CenterHorizontally)
                .testTag("WriteEnterWriteSettingButton"),
                onClick = { advanceUserAuthenticate() }) {
                Text(text = "ورود")
            }
        }
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

    @Composable
    fun ImeiTextField() {

        OutlinedTextField(
            value = deviceSerialNumber,
            onValueChange = {
                deviceSerialNumber = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .testTag("WriteSettingImeiTextField"),
            label = { Text(text = "شماره سریال دستگاه") },
        )
    }

    @Composable
    fun AppBar() {

        TopAppBar(

            title = {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 0.dp, end = 60.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تنظیمات اپراتور", textAlign = TextAlign.Center,
                    )
                }
            },
            navigationIcon = {
                Box(
                    modifier = Modifier.width(60.dp)
                ) {
                    IconButton(
                        onClick = { back() },
                        modifier = Modifier.testTag("WriteSettingBackButton")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = ""
                        )
                    }
                }
            }
        )
    }
}