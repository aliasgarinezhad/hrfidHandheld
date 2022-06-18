package com.jeanwest.reader.checkIn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.ExceptionHandler
import com.jeanwest.reader.JalaliDate.JalaliDateConverter
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.theme.CustomTextField
import com.jeanwest.reader.theme.borderColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class GetBarcodesByCheckInNumberActivity : ComponentActivity() {

    private var warehouseNumber by mutableStateOf("")
    private var barcodeTable = mutableListOf<String>()
    private var state = SnackbarHostState()
    private var uiList = mutableStateListOf<CheckInProperties>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()!!))
    }

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

        val type = object : TypeToken<SnapshotStateList<CheckInProperties>>() {}.type

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        barcodeTable = Gson().fromJson(
            memory.getString("GetBarcodesByCheckInNumberActivityBarcodeTable", ""),
            barcodeTable.javaClass
        ) ?: mutableListOf()

        uiList = Gson().fromJson(
            memory.getString("GetBarcodesByCheckInNumberActivityUiList", ""),
            type
        ) ?: mutableStateListOf()
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
        uiList.clear()
        barcodeTable = mutableListOf()
        saveMemory()
    }

    private fun getWarehouseDetails(code: String) {

        if (code.isEmpty()) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "لطفا شماره حواله را وارد کنید",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        val number = code.toLong()
        uiList.forEach {
            if (it.number == number) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "حواله تکراری است",
                        null,
                        SnackbarDuration.Long
                    )
                }
                return
            }
        }

        val url = "https://rfid-api.avakatan.ir/stock-draft-details/$code"
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
            uiList.add(checkInProperties)
            saveMemory()

        }, {
            when (it) {
                is NoConnectionError -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
                else -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            it.toString(),
                            null,
                            SnackbarDuration.Long
                        )
                    }
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
                    content = { Content() },
                    floatingActionButton = { OpenCheckInButton() },
                    floatingActionButtonPosition = FabPosition.Center,
                    snackbarHost = { ErrorSnackBar(state) },
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

        Column(modifier = Modifier.fillMaxSize()) {

            CustomTextField(modifier = Modifier
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 14.dp)
                .border(
                    BorderStroke(1.dp, borderColor),
                    shape = MaterialTheme.shapes.small
                )
                .fillMaxWidth(),
                hint = "شماره حواله را وارد کنید",
                onSearch = { getWarehouseDetails(warehouseNumber) },
                onValueChange = { warehouseNumber = it },
                value = warehouseNumber
            )

            LazyColumn(modifier = Modifier.padding(top = 2.dp)) {

                items(uiList.size) { i ->
                    Row(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                            .border(
                                BorderStroke(1.dp, borderColor),
                                shape = MaterialTheme.shapes.small
                            )
                            .background(
                                color = MaterialTheme.colors.onPrimary,
                                shape = MaterialTheme.shapes.small
                            )
                            .fillMaxWidth(),
                    ) {
                        Column {

                            Row(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "حواله: " + uiList[i].number,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .weight(1.2F)
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                )
                                Text(
                                    text = "تعداد کالاها: " + uiList[i].numberOfItems,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .weight(1F)
                                        .fillMaxWidth(),
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "مبدا: " + uiList[i].source,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .weight(1.2F)
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                )
                                Text(
                                    text = "تاریخ ثبت: " + uiList[i].date,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .weight(1F),
                                )

                            }

                            Row(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "مقصد: " + uiList[i].destination,
                                    style = MaterialTheme.typography.body1,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun OpenCheckInButton() {
        ExtendedFloatingActionButton(
            onClick = {
                Intent(this, CheckInActivity::class.java).also {
                    it.putExtra("CheckInFileBarcodeTable", JSONArray(barcodeTable).toString())
                    startActivity(it)
                }
            },
            text = { Text("شروع تروفالس") },
            backgroundColor = MaterialTheme.colors.primary,
            contentColor = MaterialTheme.colors.onPrimary
        )
    }
}