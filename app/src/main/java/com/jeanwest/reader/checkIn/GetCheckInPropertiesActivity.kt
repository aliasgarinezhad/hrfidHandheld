package com.jeanwest.reader.checkIn

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.sharedClassesAndFiles.JalaliDate.JalaliDateConverter
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.sharedClassesAndFiles.*
import com.jeanwest.reader.sharedClassesAndFiles.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GetCheckInPropertiesActivity : ComponentActivity(), IBarcodeResult {

    private var warehouseNumber by mutableStateOf("")
    private var barcodeTable = mutableListOf<Product>()
    private var state = SnackbarHostState()
    private var uiList = mutableStateListOf<CheckInProperties>()
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private val barcode2D = Barcode2D(this)
    private lateinit var queue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queue = Volley.newRequestQueue(this)
        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onResume() {
        super.onResume()
        setContent {
            Page()
        }
        loadMemory()
        barcodeInit()
    }

    private fun back() {
        stopBarcodeScan()
        queue.stop()
        beep.release()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {
                startBarcodeScan()
            } else if (keyCode == 4) {
                back()
            }
        }
        return true
    }

    private fun loadMemory() {

        val type = object : TypeToken<SnapshotStateList<CheckInProperties>>() {}.type
        val type1 = object : TypeToken<MutableList<Product>>() {}.type


        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        barcodeTable = Gson().fromJson(
            memory.getString("GetBarcodesByCheckInNumberActivityBarcodeTable", ""),
            type1
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
            Gson().toJson(barcodeTable).toString()
        )
        edit.putString("GetBarcodesByCheckInNumberActivityUiList", Gson().toJson(uiList).toString())

        edit.apply()
    }

    private fun clear(checkInProperties : CheckInProperties) {
        uiList.remove(checkInProperties)
        val removeProducts = mutableListOf<Product>()
        barcodeTable.forEach {
            if(it.checkInNumber == checkInProperties.number) {
                removeProducts.add(it)
            }
        }
        barcodeTable.removeAll(removeProducts)
        saveMemory()
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrBlank()) {
            getWarehouseDetails(barcode)
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        }
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
                barcodeTable.add(
                    Product(
                        KBarCode = it.getJSONObject(i).getString("kbarcode"),
                        desiredNumber = it.getJSONObject(i).getInt("Qty"),
                        checkInNumber = code.toLong(),
                    )
                )
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
        queue.add(request)
    }

    private fun startBarcodeScan() {
        barcode2D.startScan(this)
    }

    private fun barcodeInit() {
        barcode2D.open(this, this)
    }

    private fun stopBarcodeScan() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
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
                        .padding(end = 50.dp)
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
        )
    }

    @Composable
    fun Content() {

        Column(modifier = Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .background(color = Color.White)
                    .border(
                        BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                CustomTextField(
                    modifier = Modifier
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
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
            }

            LazyColumn {

                items(uiList.size) { i ->
                    Item(i)
                }
            }
        }
    }

    @Composable
    fun Item(i: Int) {

        val bottomPadding = if (i == uiList.size - 1) 100.dp else 0.dp
        val topPadding = if (i == 0) 16.dp else 8.dp
        val topPaddingClearButton = if (i == 0) 8.dp else 0.dp

        Box {

            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding, top = topPadding)
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

            Box(
                modifier = Modifier
                    .padding(top = topPaddingClearButton, end = 8.dp)
                    .background(
                        shape = RoundedCornerShape(36.dp),
                        color = deleteCircleColor
                    )
                    .size(30.dp)
                    .align(Alignment.TopEnd)
                    .testTag("clear")
                    .clickable {
                        clear(uiList[i])
                    }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_clear_24),
                    contentDescription = "",
                    tint = deleteColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp)
                )
            }
        }

    }

    @Composable
    fun OpenCheckInButton() {
        ExtendedFloatingActionButton(
            onClick = {
                stopBarcodeScan()
                Intent(this, CheckInActivity::class.java).also {
                    it.putExtra("CheckInFileBarcodeTable", Gson().toJson(barcodeTable).toString())
                    startActivity(it)
                }
            },
            text = { Text("شروع تروفالس") },
            backgroundColor = MaterialTheme.colors.primary,
            contentColor = MaterialTheme.colors.onPrimary
        )
    }
}