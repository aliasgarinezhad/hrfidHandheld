package com.jeanwest.reader.activities

import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.R
import com.jeanwest.reader.data.*
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.management.*
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalFoundationApi::class)
class CreateStockDraftByCartons : ComponentActivity(),
    IBarcodeResult {

    private val barcode2D = Barcode2D(this)

    //ui parameters
    var loading by mutableStateOf(false)
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var state = SnackbarHostState()
    var uiList = mutableStateListOf<Carton>()
    private lateinit var queue: RequestQueue
    private var creatingStockDraft by mutableStateOf(false)
    private var scanMode by mutableStateOf(true)
    var scannedBarcodes = mutableListOf<String>()
    private var destination by mutableStateOf("انتخاب مقصد")
    private var source by mutableStateOf("انتخاب مبدا")
    private var destinations = mutableStateMapOf<String, Int>()
    private var drivers = mutableStateMapOf<String, Int>()
    private var driver by mutableStateOf("انتخاب راننده")
    private var sources = mutableStateMapOf<String, Int>()
    var cartons = mutableStateMapOf<String, Carton>()
    private var locations = mutableMapOf<String, String>()
    private var stockDraftSpec by mutableStateOf("حواله بین انباری با RFID")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        queue = Volley.newRequestQueue(this)

        setContent {
            Page()
        }

        getWarehousesLists(queue, state, {
            destinations.clear()
            destinations.putAll(it)
        }, {})

        getDriversLists(queue, state, {
            drivers.clear()
            drivers.putAll(it)
        }, {})

        loadMemory()

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
        syncScannedItemsToServer()
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

    private fun syncScannedItemsToServer() {

        loading = true

        var cartonCode = ""
        run breakForEach@{
            scannedBarcodes.forEach {
                if (it !in cartons.keys) {
                    cartonCode = it
                    return@breakForEach
                }
            }
        }

        if (cartonCode == "") {
            uiList.clear()
            uiList.addAll(cartons.values)
            loading = false
            return
        }

        getCartonDetails(
            queue,
            state,
            cartonCode,
            {

                cartons[cartonCode] = it
                uiList.clear()
                uiList.addAll(cartons.values)
                syncScannedItemsToServer()
            },
            {
                scannedBarcodes.remove(cartonCode)
                saveToMemory()
                loading = false
            })
    }

    private fun createNewStockDraft() {

        creatingStockDraft = true

        createStockDraftByCarton(
            queue,
            state,
            uiList,
            stockDraftSpec,
            sources[source]!!,
            destinations[destination]!!,
            drivers[driver]!!,
            {
                creatingStockDraft = false
                scannedBarcodes.clear()
                cartons.clear()
                uiList.clear()
                uiList.addAll(cartons.values)
                saveToMemory()
            }, {
                creatingStockDraft = false
            }
        )
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            scannedBarcodes.add(barcode)
            scannedBarcodes = scannedBarcodes.distinct().toMutableList()
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString(
            "StockDraftByCartonsScannedBarcodes",
            JSONArray(scannedBarcodes).toString()
        )

        edit.putString(
            "StockDraftByCartonsProducts",
            Gson().toJson(cartons).toString()
        )

        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        val type = object : TypeToken<SnapshotStateMap<String, Carton>>() {}.type

        val userLocationCode = memory.getInt("userWarehouseCode", 0)

        locations = Gson().fromJson(
            memory.getString("userWarehouses", ""),
            locations.javaClass
        ) ?: mutableMapOf()

        locations.forEach {
            sources[it.value] = it.key.toInt()
        }
        source = locations[userLocationCode.toString()] ?: "امتخاب مبدا"

        val defaultDestinations = locations.filter {
            it.key != userLocationCode.toString()
        }
        if (defaultDestinations.isNotEmpty()) {
            destination = defaultDestinations.values.toList()[0]
        }

        scannedBarcodes = Gson().fromJson(
            memory.getString("StockDraftByCartonsScannedBarcodes", ""),
            scannedBarcodes.javaClass
        ) ?: mutableListOf()

        cartons = Gson().fromJson(
            memory.getString("StockDraftByCartonsProducts", ""),
            type
        ) ?: SnapshotStateMap()
    }

    fun clear(carton: Carton) {

        scannedBarcodes.remove(carton.number)
        cartons.remove(carton.number)
        uiList.clear()
        uiList.addAll(cartons.values)
        saveToMemory()
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

    private fun back() {

        if (scanMode) {
            saveToMemory()
            stopBarcodeScan()
            beep.release()
            queue.stop()
            finish()
        } else {
            saveToMemory()
            scanMode = true
        }
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { if (scanMode) Content() else Content2() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun AppBar() {

        TopAppBar(

            navigationIcon = {
                IconButton(onClick = { back() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.checkOut),
                    modifier = Modifier
                        .padding(end = 50.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        Column {

            if (loading) {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp)
                        .background(JeanswestBackground, Shapes.small)
                        .fillMaxWidth()
                ) {
                    LoadingCircularProgressIndicator(false, loading)
                }
            } else {

                if (uiList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 56.dp)
                            .fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(256.dp)
                        ) {
                            Box(

                                modifier = Modifier
                                    .background(color = Color.White, shape = Shapes.medium)
                                    .size(256.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_empty_box),
                                    contentDescription = "",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            Text(
                                "هنوز کارتنی برای ثبت حواله اسکن نکرده اید",
                                style = Typography.h1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                            )
                        }
                    }
                } else {

                    LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {

                        items(uiList.size) { i ->
                            LazyColumnItem(i)
                        }
                    }
                }
            }
        }

        BottomBarButton(text = "ثبت حواله") {

            if (cartons.isEmpty()) {
                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "هنوز کارتنی برای ارسال اسکن نکرده اید",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {
                scanMode = false
            }
        }
    }

    @Composable
    fun LazyColumnItem(i: Int) {

        val topPaddingClearButton = if (i == 0) 8.dp else 4.dp

        Box {

            Item3(
                enableBottomSpace = i == uiList.size - 1,
                text1 = uiList[i].number,
                text2 = "انبار جاری: " + uiList[i].source,
                text3 = "تنوع جنس: " + uiList[i].barcodeTable.distinct().size,
                text4 = "جمع اجناس: " + uiList[i].numberOfItems,
            )

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
    fun Content2() {

        Column {

            Column(
                modifier = Modifier
                    .shadow(6.dp, Shapes.medium)
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.large
                    )
                    .fillMaxWidth(),
            ) {

                Row {
                    OutlinedTextField(
                        value = stockDraftSpec,
                        singleLine = true,
                        label = { Text(text = "شرح حواله") },
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp)
                            .fillMaxWidth(),
                        onValueChange = {
                            stockDraftSpec = it
                        })
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    FilterDropDownList(
                        modifier = Modifier
                            .padding(start = 16.dp),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_my_location_24),
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
                                text = source,
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 6.dp)
                            )
                        },
                        onClick = {
                            source = it
                        },
                        values = sources.keys.toMutableList()
                    )

                    FilterDropDownList(
                        modifier = Modifier
                            .padding(start = 12.dp),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_location_on_24),
                                contentDescription = "",
                                tint = iconColor,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 4.dp)
                            )
                        },
                        text = {
                            Text(
                                style = MaterialTheme.typography.body2,
                                text = destination,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 4.dp)
                            )
                        },
                        onClick = {
                            destination = it
                        },
                        values = destinations.keys.toMutableList()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    FilterDropDownList(
                        modifier = Modifier
                            .padding(start = 16.dp),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_drive_eta_24),
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
                                text = driver,
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 6.dp)
                            )
                        },
                        onClick = {
                            driver = it
                        },
                        values = drivers.keys.toMutableList()
                    )
                }
            }

            if (uiList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 56.dp)
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(256.dp)
                    ) {
                        Box(

                            modifier = Modifier
                                .background(color = Color.White, shape = Shapes.medium)
                                .size(256.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_empty_box),
                                contentDescription = "",
                                tint = Color.Unspecified,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Text(
                            "هنوز کارتنی برای ثبت حواله اسکن نکرده اید",
                            style = Typography.h1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                        )
                    }
                }
            } else {

                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {

                    items(uiList.size) { i ->
                        Item3(
                            enableBottomSpace = i == uiList.size - 1,
                            text1 = uiList[i].number,
                            text2 = "انبار جاری: " + uiList[i].source,
                            text3 = "تنوع جنس: " + uiList[i].barcodeTable.distinct().size,
                            text4 = "جمع اجناس: " + uiList[i].numberOfItems,
                        )
                    }
                }
            }
        }

        if (uiList.isNotEmpty()) {
            BottomBarButton(text = if (creatingStockDraft) "در حال ثبت ..." else "ثبت نهایی حواله") {

                if (destination == "انتخاب مقصد" || source == "انتخاب مبدا" || driver == "انتخاب راننده") {

                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "لطفا مبدا، مقصد و راننده را انتخاب کنید",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                } else if (!creatingStockDraft) {
                    createNewStockDraft()
                }
            }
        }
    }
}
