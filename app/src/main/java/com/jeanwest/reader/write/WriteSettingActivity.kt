package com.jeanwest.reader.write

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
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme

class WriteSettingActivity : ComponentActivity() {

    private var filterNumber by mutableStateOf(0)
    private var partitionNumber by mutableStateOf(0)
    private var headerNumber by mutableStateOf("48")
    private var companyNumber by mutableStateOf("101")
    private var password by mutableStateOf("00000000")
    private var serialNumberMin by mutableStateOf("0")
    private var serialNumberMax by mutableStateOf("0")
    private var serialNumber by mutableStateOf(0L)

    override fun onResume() {
        super.onResume()
        setContent { Page() }
        loadMemory()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        if (memory.getLong("value", -1L) != -1L) {
            serialNumber = memory.getLong("value", -1L)
            serialNumberMax = memory.getLong("max", -1L).toString()
            serialNumberMin = memory.getLong("value", -1L).toString()
            headerNumber = memory.getInt("header", -1).toString()
            filterNumber = memory.getInt("filter", -1)
            partitionNumber = memory.getInt("partition", -1)
            companyNumber = memory.getInt("company", -1).toString()
            password = memory.getString("password", "") ?: "00000000"
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()

        memoryEditor.putLong("value", serialNumberMin.toLong())
        memoryEditor.putLong("max", serialNumberMax.toLong())
        memoryEditor.putLong("min", serialNumberMin.toLong())
        memoryEditor.putInt("header", headerNumber.toInt())
        memoryEditor.putInt("filter", filterNumber)
        memoryEditor.putInt("partition", partitionNumber)
        memoryEditor.putInt("company", companyNumber.toInt())
        memoryEditor.putString("password", password)
        memoryEditor.putLong("counterModified", 0L)
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

        Column(modifier = Modifier.fillMaxSize()) {

            HeaderTextField()
            CompanyTextField()
            PasswordTextField()
            SerialNumberMinTextField()
            SerialNumberMaxTextField()
            Row(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(text = "سریال:${serialNumber}")
                FilterDropDownList()
                PartitionDropDownList()
            }
        }
    }

    @Composable
    fun FilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        val filterValues = listOf(0, 1, 2, 3, 4, 5, 6, 7)

        Box {
            Row(modifier = Modifier
                .clickable { expanded = true }
                .testTag("WriteSettingFilterDropDownList")) {
                Text(text = "فیلتر($filterNumber)")
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .wrapContentWidth()
            ) {

                filterValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        filterNumber = it
                    }) {
                        Text(text = it.toString())
                    }
                }
            }
        }
    }

    @Composable
    fun PartitionDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        val partitionValues = listOf(0, 1, 2, 3, 4, 5, 6, 7)

        Box {
            Row(modifier = Modifier
                .clickable { expanded = true }
                .testTag("WriteSettingPartitionDropDownList")) {
                Text(text = "پارتیشن($partitionNumber)")
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .wrapContentWidth()
                    .testTag("WriteSettingPartitionDropDownList")
            ) {

                partitionValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        partitionNumber = it
                    }) {
                        Text(text = it.toString())
                    }
                }
            }
        }
    }

    @Composable
    fun HeaderTextField() {

        OutlinedTextField(
            value = headerNumber, onValueChange = {
                headerNumber = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .testTag("WriteSettingHeaderTextField"),
            label = { Text(text = "هدر") }
        )
    }

    @Composable
    fun CompanyTextField() {

        OutlinedTextField(
            value = companyNumber, onValueChange = {
                companyNumber = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .testTag("WriteSettingCompanyTextField"),
            label = { Text(text = "سریال کارخانه") }
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
            label = { Text(text = "رمز تگ ها") }
        )
    }

    @Composable
    fun SerialNumberMinTextField() {

        OutlinedTextField(
            value = serialNumberMin, onValueChange = {
                serialNumberMin = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .testTag("WriteSettingSerialNumberMinTextField"),
            label = { Text(text = "شروع بازه شماره سریال") }
        )
    }

    @Composable
    fun SerialNumberMaxTextField() {

        OutlinedTextField(
            value = serialNumberMax, onValueChange = {
                serialNumberMax = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .testTag("WriteSettingSerialNumberMaxTextField"),
            label = { Text(text = "پایان بازه شماره سریال") }
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
                        "تنظیمات رایت", textAlign = TextAlign.Center,
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