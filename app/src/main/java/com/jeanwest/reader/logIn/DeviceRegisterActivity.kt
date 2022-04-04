package com.jeanwest.reader.logIn

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class DeviceRegisterActivity : ComponentActivity() {

    private var deviceSerialNumber by mutableStateOf("")
    private var deviceId by mutableStateOf("")
    private var iotToken by mutableStateOf("")
    private var advanceSettingToken = ""
    private val apiTimeout = 30000
    private var deviceLocationCode = 0
    private var deviceLocation = ""
    private val locations = mutableMapOf<Int, String>()
    private var state = SnackbarHostState()


    override fun onResume() {
        super.onResume()
        setContent { Page() }
        advanceSettingToken = intent.getStringExtra("advanceSettingToken") ?: ""
        getLocations()
    }

    //868969010014520

    private fun registerDeviceToIotHub() {
        val url = "http://rfid-api.avakatan.ir/devices/handheld"
        val request = object : JsonObjectRequest(Method.POST, url, null, {
            deviceId = it.getString("deviceId")
            iotToken = it.getJSONObject("authentication").getJSONObject("symmetricKey")
                .getString("primaryKey")

            saveToMemory()

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "دستگاه با موفقیت رجیستر شد",
                    null,
                    SnackbarDuration.Long
                )
            }

            val nextActivityIntent = Intent(this, MainActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(nextActivityIntent)
        }, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "خطا در رجیستر کردن دستگاه",
                    null,
                    SnackbarDuration.Long
                )
            }
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

    private fun getLocations() {
        val url = "http://rfid-api.avakatan.ir/department-infos"
        val request = object : JsonArrayRequest(Method.GET, url, null, {
            locations.clear()
            for (i in 0 until it.length()) {
                locations[it.getJSONObject(i).getInt("DepartmentInfo_ID")] =
                    it.getJSONObject(i).getString("DepName")
            }
        }, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    it.toString(),
                    null,
                    SnackbarDuration.Long
                )
            }
        }) {

            override fun getHeaders(): MutableMap<String, String> {
                val header = mutableMapOf<String, String>()
                header["accept"] = "application/json"
                header["Content-Type"] = "application/json"
                header["Authorization"] = "Bearer $advanceSettingToken"
                return header
            }
        }

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()

        memoryEditor.putString("deviceId", deviceId)
        memoryEditor.putInt("deviceLocationCode", deviceLocationCode)
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

            ImeiTextField()
            LocationDropDownList()

            Button(modifier = Modifier
                .padding(top = 20.dp)
                .align(Alignment.CenterHorizontally)
                .testTag("WriteEnterWriteSettingButton"),
                onClick = {
                    registerDeviceToIotHub()
                }) {
                Text(text = "ورود")
            }
        }
    }

    @Composable
    fun LocationDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .clickable { expanded = true }) {
                locations[deviceLocationCode]?.let { Text(text = it) }
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {
                locations.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        deviceLocationCode = it.key
                        deviceLocation = it.value
                    }) {
                        Text(text = it.value)
                    }
                }
            }
        }
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