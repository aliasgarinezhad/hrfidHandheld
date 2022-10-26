package com.jeanwest.reader.logIn

import android.content.Intent
import android.view.KeyEvent
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
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.shared.ErrorSnackBar
import com.jeanwest.reader.shared.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class OperatorLoginActivity : ComponentActivity() {

    private var password by mutableStateOf("")
    private var username by mutableStateOf("")
    private var advanceSettingToken = ""
    private var state = SnackbarHostState()

    override fun onResume() {
        super.onResume()
        setContent { Page() }
    }

    private fun advanceUserAuthenticate() {

        val url = "https://rfid-api.avakatan.ir/login/operators"
        val request = object : JsonObjectRequest(Method.POST, url, null, fun(it) {
            advanceSettingToken = it.getString("accessToken")
            Intent(this, DeviceRegisterActivity::class.java).also {
                it.putExtra("advanceSettingToken", advanceSettingToken)
                startActivity(it)
            }
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

                    val error =
                        JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")

                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            error.getString("message"),
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
            }
        }) {

            override fun getHeaders(): MutableMap<String, String> {
                val header = mutableMapOf<String, String>()
                header["accept"] = "application/json"
                header["Content-Type"] = "application/json"
                return header
            }

            override fun getBody(): ByteArray {
                val body = JSONObject()
                body.put("username", username)
                body.put("password", password)
                return body.toString().toByteArray()
            }
        }

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 4) {
            back()
        }
        return true
    }

    private fun back() {
        finish()
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 10.dp)
        ) {

            UsernameTextField()
            PasswordTextField()

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
