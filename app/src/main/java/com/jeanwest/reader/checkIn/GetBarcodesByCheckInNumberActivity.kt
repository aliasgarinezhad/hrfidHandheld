package com.jeanwest.reader.checkIn

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.JalaliDate.JalaliDateConverter
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme
import org.json.JSONArray

class GetBarcodesByCheckInNumberActivity : ComponentActivity() {

    private var checkInNumber by mutableStateOf("")
    private var barcodeTable = mutableListOf<String>()
    private var uiList by mutableStateOf(mutableListOf<CheckInProperties>())
    private var uiListTemp by mutableStateOf(mutableListOf<CheckInProperties>())

    override fun onResume() {
        super.onResume()
        setContent {
            Page()
        }
        loadMemory()
    }

    private fun back() {
        finish()
    }

    private fun loadMemory() {

        val type = object : TypeToken<List<CheckInProperties>>() {}.type

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        barcodeTable = Gson().fromJson(
            memory.getString("GetBarcodesByCheckInNumberActivityBarcodeTable", ""),
            barcodeTable.javaClass
        ) ?: mutableListOf()

        uiList = Gson().fromJson(
            memory.getString("GetBarcodesByCheckInNumberActivityUiList", ""),
            type
        ) ?: mutableListOf()

        uiListTemp = Gson().fromJson(
            memory.getString("GetBarcodesByCheckInNumberActivityUiList", ""),
            type
        ) ?: mutableListOf()
    }

    private fun saveMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString(
            "GetBarcodesByCheckInNumberActivityBarcodeTable",
            JSONArray(barcodeTable).toString()
        )
        edit.putString("GetBarcodesByCheckInNumberActivityUiList", Gson().toJson(uiList).toString())

        edit.apply()
    }

    private fun clear() {
        uiList = mutableListOf()
        uiListTemp = mutableListOf()
        barcodeTable = mutableListOf()
        saveMemory()
    }

    private fun getCheckInNumberDetails(checkInNumber: String) {

        if (checkInNumber.isEmpty()) {
            Toast.makeText(this, "لطفا شماره حواله را وارد کنید", Toast.LENGTH_LONG).show()
            return
        }

        val number = checkInNumber.toLong()
        uiListTemp.forEach { it ->
            if (it.number == number) {
                Toast.makeText(this, "حواله تکراری است", Toast.LENGTH_LONG).show()
                return
            }
        }

        val url = "http://rfid-api.avakatan.ir/stock-draft-details/$checkInNumber"
        val request = object : JsonArrayRequest(url, fun(it) {

            val source = it.getJSONObject(0).getInt("FromWareHouse_ID")
            val destination = it.getJSONObject(0).getInt("ToWareHouse_ID")
            val miladiCreateDate = it.getJSONObject(0).getString("CreateDate").substring(0, 10)
            val intArrayFormatJalaliCreateDate = JalaliDateConverter.gregorian_to_jalali(
                miladiCreateDate.substring(0, 4).toInt(),
                miladiCreateDate.substring(5, 7).toInt(),
                miladiCreateDate.substring(8, 10).toInt()
            )

            val jalaliCreateDate =
                "${intArrayFormatJalaliCreateDate[0]}/${intArrayFormatJalaliCreateDate[1]}/${intArrayFormatJalaliCreateDate[2]}"

            var numberOfItems = 0
            for (i in 0 until it.length()) {
                numberOfItems += it.getJSONObject(i).getInt("Qty")
                repeat(it.getJSONObject(i).getInt("Qty")) { _ ->
                    barcodeTable.add(it.getJSONObject(i).getString("kbarcode"))
                }
            }

            val checkInProperties = CheckInProperties(
                number = number,
                date = jalaliCreateDate,
                source = source,
                destination = destination,
                numberOfItems = numberOfItems
            )

            uiListTemp.add(checkInProperties)
            uiList = mutableListOf()
            uiList = uiListTemp

            saveMemory()

        }, {
            when (it) {
                is NoConnectionError -> {
                    Toast.makeText(
                        this,
                        "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Toast.makeText(this, it.toString(), Toast.LENGTH_LONG).show()
                }
            }
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }
        }

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    @Composable
    fun Page() {

        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() }
                )
            }
        }
    }

    @Composable
    fun AppBar() {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.checkInText),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .fillMaxSize()
                        .wrapContentSize()
                )
            },
            navigationIcon = {
                IconButton(onClick = { back() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = ""
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    clear()
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            }
        )
    }

    @Composable
    fun Content() {

        val modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp, top = 5.dp)
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
            ) {
                CheckInNumberTextField(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                        .testTag("CheckInNumberTextField")
                        .weight(5F)
                )
                AddCheckInNumberButton(modifier = Modifier.weight(1F))
                OKButton(
                    modifier = Modifier
                        .weight(1F)
                        .padding(end = 10.dp)
                )
            }

            LazyColumn(modifier = Modifier.padding(top = 2.dp)) {

                items(uiList.size) { i ->
                    Row(
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .background(
                                color = MaterialTheme.colors.onPrimary,
                                shape = MaterialTheme.shapes.small
                            )
                            .fillMaxWidth(),
                    ) {
                        Column {

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "شماره حواله: " + uiList[i].number,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = modifier
                                        .weight(1.2F)
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                )
                                Text(
                                    text = "تعداد کالاها: " + uiList[i].numberOfItems,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = modifier
                                        .weight(1F)
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "مبدا: " + uiList[i].source,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = modifier
                                        .weight(1.2F)
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                )
                                Text(
                                    text = "مقصد: " + uiList[i].destination,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = modifier
                                        .weight(1F)
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "تاریخ ثبت: " + uiList[i].date,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = modifier.padding(
                                        start = 8.dp,
                                        top = 4.dp,
                                        bottom = 4.dp,
                                        end = 0.dp
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CheckInNumberTextField(modifier: Modifier) {
        OutlinedTextField(
            value = checkInNumber,
            onValueChange = {
                checkInNumber = it
            },
            modifier = modifier,
            label = { Text(text = "شماره حواله") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }

    @Composable
    fun AddCheckInNumberButton(modifier: Modifier) {
        IconButton(
            onClick = {
                getCheckInNumberDetails(checkInNumber)
            },
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Filled.Add, "", tint = MaterialTheme.colors.primary)
        }
    }

    @Composable
    fun OKButton(modifier: Modifier) {
        IconButton(
            onClick = {
                Intent(this, CheckInActivity::class.java).also {
                    it.putExtra("CheckInFileBarcodeTable", JSONArray(barcodeTable).toString())
                    startActivity(it)
                }
            },
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Filled.Check, "", tint = MaterialTheme.colors.primary)
        }
    }
}