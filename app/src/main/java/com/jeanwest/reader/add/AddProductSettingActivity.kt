package com.jeanwest.reader.add

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.preference.PreferenceManager
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme

class AddProductSettingActivity : ComponentActivity() {

    private var filterNumber by mutableStateOf(AddProductActivity.filterNumber)
    private var partitionNumber by mutableStateOf(AddProductActivity.partitionNumber)
    private var headerNumber by mutableStateOf(AddProductActivity.headerNumber.toString())
    private var companyNumber by mutableStateOf(AddProductActivity.companyNumber.toString())
    private var password by mutableStateOf(AddProductActivity.tagPassword)
    private var serialNumberMin by mutableStateOf(AddProductActivity.counterMinValue.toString())
    private var serialNumberMax by mutableStateOf(AddProductActivity.counterMaxValue.toString())

    @SuppressLint("SetTextI18n", "CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Page() }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()

        AddProductActivity.counterMinValue = serialNumberMin.toLong()
        AddProductActivity.counterValue = serialNumberMin.toLong()
        AddProductActivity.counterMaxValue = serialNumberMax.toLong()
        AddProductActivity.headerNumber = headerNumber.toInt()
        AddProductActivity.filterNumber = filterNumber
        AddProductActivity.partitionNumber = partitionNumber
        AddProductActivity.companyNumber = companyNumber.toInt()
        AddProductActivity.tagPassword = password

        memoryEditor.putLong("value", AddProductActivity.counterValue)
        memoryEditor.putLong("max", AddProductActivity.counterMaxValue)
        memoryEditor.putLong("min", AddProductActivity.counterMinValue)
        memoryEditor.putInt("header", AddProductActivity.headerNumber)
        memoryEditor.putInt("filter", AddProductActivity.filterNumber)
        memoryEditor.putInt("partition", AddProductActivity.partitionNumber)
        memoryEditor.putInt("company", AddProductActivity.companyNumber)
        memoryEditor.putString("password", AddProductActivity.tagPassword)
        memoryEditor.putLong("counterModified", 0L)
        memoryEditor.apply()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode == 4) {
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

        Column (modifier = Modifier.fillMaxSize()) {

            HeaderTextField()
            CompanyTextField()
            PasswordTextField()
            SerialNumberMinTextField()
            SerialNumberMaxTextField()
            Row ( modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround) {
                Text(text = "سریال:${AddProductActivity.counterValue}")
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

        val filterValues = listOf(0,1,2,3,4,5,6,7)

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = "فیلتر($filterNumber)")
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
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

        val partitionValues = listOf(0,1,2,3,4,5,6,7)

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = "پارتیشن($partitionNumber)")
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
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
                .fillMaxWidth(),
            label = {Text(text = "هدر")}
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
                .fillMaxWidth(),
            label = {Text(text = "سریال کارخانه")}
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
            label = {Text(text = "رمز تگ ها")}
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
                .fillMaxWidth(),
            label = {Text(text = "شروع بازه شماره سریال")}
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
                .fillMaxWidth(),
            label = {Text(text = "پایان بازه شماره سریال")}
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
                        "تنظیمات پیشرفته", textAlign = TextAlign.Center,
                    )
                }
            },
            navigationIcon = {
                Box(
                    modifier = Modifier.width(60.dp)
                ) {
                    IconButton(onClick = { back() }) {
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