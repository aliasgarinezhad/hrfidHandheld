package com.jeanwest.reader.activities

import android.annotation.SuppressLint
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
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.management.ErrorSnackBar
import com.jeanwest.reader.management.getWarehousesLists
import com.jeanwest.reader.management.registerDevice
import com.jeanwest.reader.ui.MyApplicationTheme

class DeviceRegister : ComponentActivity() {

    private var deviceSerialNumber by mutableStateOf("")
    private var deviceId by mutableStateOf("")
    private var iotToken by mutableStateOf("")
    private var advanceSettingToken = ""
    private var deviceLocationCode = 0
    private var deviceLocation = ""
    private val locations = mutableMapOf<Int, String>()
    private var state = SnackbarHostState()
    private lateinit var queue : RequestQueue


    override fun onResume() {
        super.onResume()
        setContent { Page() }
        queue = Volley.newRequestQueue(this)
        advanceSettingToken = intent.getStringExtra("advanceSettingToken") ?: ""
        getLocations()
    }

    private fun registerDeviceToIotHub() {

        registerDevice(queue, state, advanceSettingToken, deviceSerialNumber, { deviceId, iotToken ->
            saveToMemory()
            val nextActivityIntent = Intent(this, MainActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(nextActivityIntent)
        }, {})
    }

    private fun getLocations() {

        getWarehousesLists(queue, state, {
            locations.clear()
            it.forEach{ it1 ->
                locations[it1.value] = it1.key
            }
        }, {}, advanceSettingToken)
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
