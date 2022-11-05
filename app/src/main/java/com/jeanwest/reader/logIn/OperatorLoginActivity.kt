package com.jeanwest.reader.logIn

import android.annotation.SuppressLint
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
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.shared.ErrorSnackBar
import com.jeanwest.reader.shared.operatorLogin
import com.jeanwest.reader.shared.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class OperatorLoginActivity : ComponentActivity() {

    private var password by mutableStateOf("")
    private var username by mutableStateOf("")
    private var state = SnackbarHostState()
    private lateinit var queue : RequestQueue

    override fun onResume() {
        super.onResume()
        setContent { Page() }
        queue = Volley.newRequestQueue(this)
    }

    private fun advanceUserAuthenticate() {
        operatorLogin(queue, state, username, password, {
            Intent(this, DeviceRegisterActivity::class.java).also {
                it.putExtra("advanceSettingToken", it)
                startActivity(it)
            }
        }, {})
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
