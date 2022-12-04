package com.jeanwest.reader.activities

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.data.getWarehousesLists
import com.jeanwest.reader.data.operatorLogin
import com.jeanwest.reader.data.registerDevice
import com.jeanwest.reader.ui.*

class DeviceRegister : ComponentActivity() {

    private var password by mutableStateOf("")
    private var username by mutableStateOf("")
    private var state = SnackbarHostState()
    private lateinit var queue: RequestQueue
    private var advanceSettingToken = ""
    private var loginMode by mutableStateOf(true)
    private var deviceSerialNumber by mutableStateOf("")
    private var deviceId by mutableStateOf("")
    private var iotToken by mutableStateOf("")
    private var deviceLocation by mutableStateOf("")
    private val locations = mutableStateMapOf<String, Int>()
    var loading by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        setContent { Page() }
        queue = Volley.newRequestQueue(this)
    }

    private fun advanceUserAuthenticate() {
        loading = true
        operatorLogin(queue, state, username, password, {
            advanceSettingToken = it
            loginMode = false
            getLocations()
        }, {
            loading = false
        })
    }

    private fun registerDeviceToIotHub() {

        loading = true

        registerDevice(
            queue,
            state,
            advanceSettingToken,
            deviceSerialNumber,
            { deviceId, iotToken ->
                this.deviceId = deviceId
                this.iotToken = iotToken
                saveToMemory()
                loading = false
                val nextActivityIntent = Intent(this, MainActivity::class.java)
                intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(nextActivityIntent)
            },
            {
                loading = false
            })
    }

    private fun getLocations() {

        loading = true
        getWarehousesLists(queue, state, {
            locations.clear()
            locations.putAll(it)
            deviceLocation = locations.keys.toList()[0]
            loading = false
        }, {
            loading = false
        }, advanceSettingToken)
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()

        memoryEditor.putString("deviceId", deviceId)
        memoryEditor.putInt("deviceLocationCode", locations[deviceLocation] ?: 0)
        memoryEditor.putString("deviceLocation", deviceLocation)
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
        if (loginMode) {
            finish()
        } else {
            loginMode = true
        }
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { if (loginMode) AppBar() else AppBar2() },
                    content = { if (loginMode) Content() else Content2() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun Content() {

        Column {
            if (loading) {
                LoadingCircularProgressIndicator(isDataLoading = loading)
            } else {
                SimpleTextField(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp)
                        .fillMaxWidth(),
                    hint = "نام کاربری خود را وارد کنید",
                    onValueChange = { username = it },
                    value = username,
                )

                SimpleTextField(
                    modifier = Modifier
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        )
                        .fillMaxWidth(),
                    hint = "رمز عبور خود را وارد کنید",
                    onValueChange = { password = it },
                    value = password,
                )

                BigButton(text = "ورود") {
                    advanceUserAuthenticate()
                }
            }
        }
    }

    @Composable
    fun Content2() {

        Column {

            if (loading) {
                LoadingCircularProgressIndicator(isDataLoading = loading)
            } else {

                SimpleTextField(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp)
                        .fillMaxWidth(),
                    hint = "شماره سریال دستگاه را وارد کنید",
                    onValueChange = { deviceSerialNumber = it },
                    value = deviceSerialNumber,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {

                    FilterDropDownList(
                        modifier = Modifier
                            .padding(start = 24.dp, bottom = 24.dp),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_location_city_24),
                                contentDescription = "",
                                tint = iconColor,
                                modifier = Modifier
                                    .size(28.dp)
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 6.dp)
                            )
                        },
                        text = {
                            Text(
                                text = deviceLocation,
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 6.dp)
                            )
                        },
                        onClick = {
                            deviceLocation = it
                        },
                        values = locations.keys.toMutableList()
                    )
                }

                BigButton(text = "ثبت اطلاعات") {
                    registerDeviceToIotHub()
                }
            }
        }
    }

    @Composable
    fun AppBar2() {

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
                        stringResource(id = R.string.deviceRegister), textAlign = TextAlign.Center,
                    )
                }
            },
            navigationIcon = {
                Box(
                    modifier = Modifier.width(60.dp)
                ) {
                    IconButton(
                        onClick = { back() },
                        modifier = Modifier.testTag("back")
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

    @Composable
    fun AppBar() {

        TopAppBar(

            title = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.deviceRegister), textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}
