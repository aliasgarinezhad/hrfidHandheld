package com.jeanwest.reader.activities

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.R
import com.jeanwest.reader.data.*
import com.jeanwest.reader.management.*
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.hardware.setRFPower
import com.jeanwest.reader.hardware.successBeep
import com.jeanwest.reader.test.RFIDWithUHFUART
import com.jeanwest.reader.ui.*
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import org.json.JSONArray

@OptIn(ExperimentalFoundationApi::class)
class CreateCarton : ComponentActivity(),
    IBarcodeResult {

    private var cartonNumber = ""
    private val barcode2D = Barcode2D(this)
    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var scannedEpcs = mutableListOf<String>()
    private var scanningJob: Job? = null

    //ui parameters
    var loading by mutableStateOf(false)
    private var rfidScan by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var state = SnackbarHostState()
    var uiList = mutableStateListOf<Product>()
    private lateinit var queue: RequestQueue
    private var creatingCarton by mutableStateOf(false)
    var scannedBarcodes = mutableListOf<String>()
    private var source by mutableStateOf("انتخاب انبار جاری")
    private var sources = mutableStateMapOf<String, Int>()
    var products = mutableListOf<Product>()
    private var locations = mutableStateMapOf<String, String>()
    var scanTypeValue by mutableStateOf("RFID")
    private var printers = mutableStateMapOf<String, Int>()
    private var printer by mutableStateOf("")
    private var openPrintDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        queue = Volley.newRequestQueue(this)

        setContent {
            Page()
        }

        loadMemory()

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
        loadPrintersList()
    }

    private fun loadPrintersList() {
        loading = true
        getPrintersList(queue, state, {
            printers.clear()
            printers.putAll(it)
            printer = printers.keys.toMutableList()[0]
            syncScannedItemsToServer()
        }, {
            loading = false
        })
    }

    private fun printCarton() {

        print(queue, state, printers[printer] ?: 0, cartonNumber, {
            scannedBarcodes.clear()
            scannedEpcs.clear()
            products.clear()
            syncScannedItemsToServer()
            saveToMemory()
            creatingCarton = false
        }, {
            creatingCarton = false
        })
    }

    private fun stopRFScan() {

        scanningJob?.let {
            if (it.isActive) {
                rfidScan = false // cause scanning routine loop to stop
                runBlocking { it.join() }
            }
        }
    }

    private suspend fun startRFScan() {

        scanning = true
        rfidScan = true
        if (!setRFPower(state, rf, rfPower)) {
            rfidScan = false
            return
        }

        var epcTablePreviousSize = scannedEpcs.size

        rf.startInventoryTag(0, 0, 0)

        while (rfidScan) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        scannedEpcs.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            scannedEpcs = scannedEpcs.distinct().toMutableList()

            val speed = scannedEpcs.size - epcTablePreviousSize
            when {
                speed > 100 -> {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 700)
                }
                speed > 30 -> {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                }
                speed > 10 -> {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 300)
                }
                speed > 0 -> {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                }
            }
            epcTablePreviousSize = scannedEpcs.size

            saveToMemory()
            delay(1000)
        }

        rf.stopInventory()
        saveToMemory()
        scanning = false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (scanTypeValue == "بارکد") {
                    stopRFScan()
                    startBarcodeScan()
                } else {
                    if (!rfidScan) {

                        scanningJob = CoroutineScope(Dispatchers.IO).launch {
                            startRFScan()
                        }
                    } else {

                        stopRFScan()
                        if (scannedEpcs.size + scannedBarcodes.size != 0) {
                            syncScannedItemsToServer()
                        }
                    }

                }
            } else if (keyCode == 4) {
                back()
            } else if (keyCode == 139) {
                stopRFScan()
            }
        }
        return true
    }

    private fun syncScannedItemsToServer() {

        loading = true

        if (scannedBarcodes.size + scannedEpcs.size == 0) {
            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy {
                it.productCode
            }
            uiList.sortBy {
                it.name
            }
            loading = false
            return
        }

        val barcodeArray = mutableListOf<String>()
        val epcArray = mutableListOf<String>()
        val alreadySyncedBarcodes = mutableListOf<String>()
        val alreadySyncedEpcs = mutableListOf<String>()

        products.forEach {
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
            if (it.scannedEPCNumber > 0) {
                alreadySyncedEpcs.addAll(scannedEpcs)
            }
        }

        scannedBarcodes.forEach {
            if (it !in alreadySyncedBarcodes) {
                barcodeArray.add(it)
            } else {
                val productIndex = products.indexOf(products.last { it1 ->
                    it1.scannedBarcode == it
                })
                products[productIndex].scannedBarcodeNumber =
                    scannedBarcodes.count { it1 ->
                        it1 == it
                    }
            }
        }

        scannedEpcs.forEach {
            if (it !in alreadySyncedEpcs) {
                epcArray.add(it)
            }
        }

        if (barcodeArray.size + epcArray.size == 0) {
            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy {
                it.productCode
            }
            uiList.sortBy {
                it.name
            }
            loading = false
            return
        }

        getProductsV4(
            queue,
            state,
            epcArray,
            barcodeArray,
            { epcs, barcodes, invalidEpcs, invalidBarcodes ->

                barcodes.forEach {
                    var isInRefillProductList = false

                    run forEach1@{
                        products.forEach { it1 ->
                            if (it1.KBarCode == it.KBarCode) {
                                it1.scannedBarcode = it.scannedBarcode
                                it1.scannedBarcodeNumber += 1
                                isInRefillProductList = true
                                return@forEach1
                            }
                        }
                    }
                    if (!isInRefillProductList) {
                        it.scannedBarcodeNumber = 1
                        products.add(it)
                    }
                }

                epcs.forEach {
                    var isInRefillProductList = false

                    run forEach1@{
                        products.forEach { it1 ->
                            if (it1.KBarCode == it.KBarCode) {
                                it1.scannedEPCs.addAll(it.scannedEPCs)
                                isInRefillProductList = true
                                return@forEach1
                            }
                        }
                    }
                    if (!isInRefillProductList) {
                        products.add(it)
                    }
                }

                for (i in 0 until invalidBarcodes.length()) {
                    scannedBarcodes.remove(invalidBarcodes[i])
                }
                for (i in 0 until invalidEpcs.length()) {
                    scannedEpcs.remove(invalidEpcs[i])
                }

                uiList.clear()
                uiList.addAll(products)
                uiList.sortBy {
                    it.productCode
                }
                uiList.sortBy {
                    it.name
                }
                loading = false

            },
            {

                uiList.clear()
                uiList.addAll(products)
                uiList.sortBy { it1 ->
                    it1.productCode
                }
                uiList.sortBy { it1 ->
                    it1.name
                }
                loading = false
            }
        )
    }

    private fun createNewCarton() {

        creatingCarton = true
        createCarton(queue, state, uiList, sources[source]!!, {
            cartonNumber = it
            printCarton()
        }, {
            creatingCarton = false
        })
    }

    private fun syncScannedItemToServer(barcode: String) {

        loading = true

        val alreadySyncedBarcodes = mutableListOf<String>()
        products.forEach {
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
        }

        if (barcode in alreadySyncedBarcodes) {
            successBeep()
            scannedBarcodes.add(barcode)
            saveToMemory()
            val productIndex = products.indexOf(products.last { it1 ->
                it1.scannedBarcode == barcode
            })
            products[productIndex].scannedBarcodeNumber =
                scannedBarcodes.count { it1 ->
                    it1 == barcode
                }

            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy {
                it.productCode
            }
            uiList.sortBy {
                it.name
            }
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            loading = false
            return
        }

        getProductsV4(
            queue,
            state,
            mutableListOf(),
            mutableListOf(barcode),
            { _, barcodes, _, _ ->

                if (barcodes.size == 1) {
                    successBeep()
                    scannedBarcodes.add(barcode)
                    saveToMemory()
                    var isInRefillProductList = false

                    run forEach1@{
                        products.forEach { it1 ->
                            if (it1.KBarCode == barcodes[0].KBarCode) {
                                it1.scannedBarcode = barcodes[0].scannedBarcode
                                it1.scannedBarcodeNumber += 1
                                isInRefillProductList = true
                                return@forEach1
                            }
                        }
                    }
                    if (!isInRefillProductList) {
                        barcodes[0].scannedBarcodeNumber = 1
                        products.add(barcodes[0])
                    }
                }

                uiList.clear()
                uiList.addAll(products)
                uiList.sortBy {
                    it.productCode
                }
                uiList.sortBy {
                    it.name
                }
                uiList.sortBy { it1 ->
                    it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                }
                loading = false

            },
            {

                uiList.clear()
                uiList.addAll(products)
                uiList.sortBy { it1 ->
                    it1.productCode
                }
                uiList.sortBy { it1 ->
                    it1.name
                }
                uiList.sortBy { it1 ->
                    it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                }
                loading = false
            })
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {
            syncScannedItemToServer(barcode)
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString(
            "CreateCartonBarcodeTable",
            JSONArray(scannedBarcodes).toString()
        )

        edit.putString(
            "CreateCartonEpcTable",
            JSONArray(scannedEpcs).toString()
        )

        edit.putString(
            "CreateCartonProducts",
            Gson().toJson(products).toString()
        )

        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        val type = object : TypeToken<List<Product>>() {}.type

        val userLocationCode = memory.getInt("userWarehouseCode", 0)

        locations = Gson().fromJson(
            memory.getString("userWarehouses", ""),
            locations.javaClass
        ) ?: mutableStateMapOf()

        locations.forEach {
            sources[it.value] = it.key.toInt()
        }
        source = locations[userLocationCode.toString()] ?: "انتخاب انبار جاری"

        scannedBarcodes = Gson().fromJson(
            memory.getString("CreateCartonBarcodeTable", ""),
            scannedBarcodes.javaClass
        ) ?: mutableListOf()

        scannedEpcs = Gson().fromJson(
            memory.getString("CreateCartonEpcTable", ""),
            scannedEpcs.javaClass
        ) ?: mutableListOf()

        products = Gson().fromJson(
            memory.getString("CreateCartonProducts", ""),
            type
        ) ?: ArrayList()
    }

    fun clear(product: Product) {

        val removedRefillProducts = mutableListOf<Product>()

        products.forEach {
            if (it.KBarCode == product.KBarCode) {

                scannedBarcodes.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                scannedEpcs.removeAll { it1 ->
                    it1 in it.scannedEPCs
                }
                removedRefillProducts.add(it)
            }
        }
        products.removeAll(removedRefillProducts)
        removedRefillProducts.clear()

        uiList.clear()
        uiList.addAll(products)
        uiList.sortBy { it1 ->
            it1.productCode
        }
        uiList.sortBy { it1 ->
            it1.name
        }
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

        saveToMemory()
        stopBarcodeScan()
        beep.release()
        queue.stop()
        finish()
    }

    private fun openSearchActivity(product: Product) {

        val intent = Intent(this, SearchSpecialProduct::class.java)
        intent.putExtra("product", Gson().toJson(product).toString())
        startActivity(intent)
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
                    text = stringResource(id = R.string.createCarton),
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

            if (loading || scanning) {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp)
                        .background(JeanswestBackground, Shapes.small)
                        .fillMaxWidth()
                ) {
                    LoadingCircularProgressIndicator(scanning, loading)
                }
            } else {

                if (openPrintDialog) {
                    PrintAlertDialog()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 0.dp, top = 16.dp)
                    ) {
                        Row(modifier = Modifier.weight(2F)) {

                            FilterDropDownList(
                                modifier = Modifier
                                    .padding(start = 16.dp),
                                icon = {},
                                text = {
                                    Text(
                                        text = source,
                                        style = MaterialTheme.typography.body2,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    source = it
                                },
                                values = sources.keys.toMutableList()
                            )
                        }
                        Row(modifier = Modifier.weight(1F)) {

                            FilterDropDownList(
                                icon = {},
                                text = {
                                    Text(
                                        text = scanTypeValue,
                                        style = MaterialTheme.typography.body2,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    scanTypeValue = it
                                },
                                values = mutableListOf("RFID", "بارکد")
                            )
                        }

                        Text(
                            text = "مجموع: " + (scannedEpcs.size + scannedBarcodes.size).toString(),
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier
                                .weight(1F)
                                .align(Alignment.CenterVertically)
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
                                "هنوز کالایی برای ایجاد کارتن اسکن نکرده اید",
                                style = Typography.h1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                            )
                        }
                    }
                } else {

                    LazyColumn(modifier = Modifier.padding(top = 0.dp)) {

                        items(uiList.size) { i ->
                            LazyColumnItem(i)
                        }
                    }
                }
            }
        }
        if (uiList.isNotEmpty()) {
            BottomBarButton(text = if (creatingCarton) "در حال ایجاد ..." else "ایجاد کارتن") {

                if (source == "انتخاب انبار جاری") {

                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "لطفا انبار جاری را انتخاب کنید",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                } else if (!creatingCarton) {
                    openPrintDialog = true
                }
            }
        }
    }

    @Composable
    fun LazyColumnItem(i: Int) {

        val topPaddingClearButton = if (i == 0) 8.dp else 4.dp

        Box {

            Item(
                i, uiList, true,
                text3 = "اسکن: " + uiList[i].scannedNumber,
                text4 = "انبار: " + uiList[i].wareHouseNumber,
            ) {
                openSearchActivity(uiList[i])
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
    fun PrintAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openPrintDialog = false
            },
            buttons = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {

                    Text("لطفا پرینتر مورد نظر خود را مشخص کنید ",
                        style = MaterialTheme.typography.body2)

                    Row {

                        FilterDropDownList(
                            modifier = Modifier
                                .padding(top = 24.dp, end = 16.dp),
                            icon = {},
                            text = {
                                Text(
                                    text = printer,
                                    style = MaterialTheme.typography.body2,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 16.dp)
                                )
                            },
                            onClick = {
                                printer = it
                            },
                            values = printers.keys.toMutableList()
                        )

                        Button(onClick = {
                            openPrintDialog = false
                            createNewCarton()

                        }, modifier = Modifier.padding(top = 24.dp)
                            .align(Alignment.CenterVertically)) {
                            Text(text = "پرینت")
                        }
                    }
                }
            }
        )
    }
}
