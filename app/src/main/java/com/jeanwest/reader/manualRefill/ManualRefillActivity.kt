package com.jeanwest.reader.manualRefill

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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.sharedClassesAndFiles.Barcode2D
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.sharedClassesAndFiles.*
import com.jeanwest.reader.theme.*
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalFoundationApi::class)
class ManualRefillActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 5
    private val barcode2D = Barcode2D(this)
    private var scanningJob: Job? = null
    val inputBarcodes = ArrayList<String>()

    //ui parameters
    private var isScanning by mutableStateOf(false)
    private var isDataLoading by mutableStateOf(false)
    private var numberOfScanned by mutableStateOf(0)
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var scanTypeValue by mutableStateOf("بارکد")
    private var state = SnackbarHostState()
    var uiList = mutableStateListOf<Product>()
    private lateinit var queue : RequestQueue

    companion object {
        var scannedEpcTable = mutableListOf<String>()
        var scannedBarcodeTable = mutableListOf<String>()
        var products = mutableListOf<Product>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        queue = Volley.newRequestQueue(this)

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode(rf, state)

        setContent {
            Page()
        }

        loadMemory()

        if (numberOfScanned != 0) {
            syncScannedItemsToServer()
        }

        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()!!))
    }

    override fun onResume() {
        super.onResume()
        uiList.clear()
        uiList.addAll(products)
        uiList.sortBy { it1 ->
            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
        }
        barcodeInit()
        saveToMemory()
        loadMemory()
        getRefillBarcodes()
    }

    override fun onPause() {
        super.onPause()
        stopBarcodeScan()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (scanTypeValue == "بارکد") {
                    stopRFScan()
                    startBarcodeScan()
                } else {
                    if (!isScanning) {

                        scanningJob = CoroutineScope(IO).launch {
                            startRFScan()
                        }

                    } else {

                        stopRFScan()
                        if (numberOfScanned != 0) {
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

    private fun stopRFScan() {

        scanningJob?.let {
            if (it.isActive) {
                isScanning = false // cause scanning routine loop to stop
                runBlocking { it.join() }
            }
        }
    }

    private fun getRefillBarcodes() {

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/charge-requests"

        val request = object : JsonArrayRequest(Method.GET, url, null, {

            inputBarcodes.clear()

            for (i in 0 until it.length()) {

                inputBarcodes.add(it.getJSONObject(i).getString("KBarCode"))
            }
            isDataLoading = false
            getRefillItems()
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
            isDataLoading = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun getRefillItems() {

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/products/v4"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val refillItemsJsonArray = it.getJSONArray("KBarCodes")

            for (i in 0 until refillItemsJsonArray.length()) {

                val fileProduct = Product(
                    name = refillItemsJsonArray.getJSONObject(i).getString("productName"),
                    KBarCode = refillItemsJsonArray.getJSONObject(i).getString("KBarCode"),
                    imageUrl = refillItemsJsonArray.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = refillItemsJsonArray.getJSONObject(i).getLong("BarcodeMain_ID"),
                    productCode = refillItemsJsonArray.getJSONObject(i).getString("K_Bar_Code"),
                    size = refillItemsJsonArray.getJSONObject(i).getString("Size"),
                    color = refillItemsJsonArray.getJSONObject(i).getString("Color"),
                    originalPrice = refillItemsJsonArray.getJSONObject(i).getString("OrgPrice"),
                    salePrice = refillItemsJsonArray.getJSONObject(i).getString("SalePrice"),
                    rfidKey = refillItemsJsonArray.getJSONObject(i).getLong("RFID"),
                    wareHouseNumber = refillItemsJsonArray.getJSONObject(i).getInt("depoCount"),
                    scannedBarcode = "",
                    scannedEPCs = mutableListOf(),
                    scannedBarcodeNumber = 0,
                    scannedEPCNumber = 0,
                    kName = refillItemsJsonArray.getJSONObject(i).getString("K_Name"),
                    requestedNum = 1,
                    storeNumber = 0,
                )

                var isInRefillProductList = false
                products.forEach { it1 ->
                    if (it1.KBarCode == fileProduct.KBarCode) {
                        it1.requestedNum += 1
                        isInRefillProductList = true
                        return@forEach
                    }
                }
                if (!isInRefillProductList) {
                    products.add(fileProduct)
                }
            }

            isDataLoading = false

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
            } else {
                uiList.clear()
                uiList.addAll(products)
                uiList.sortBy { it1 ->
                    it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                }
            }

        }, {

            if (inputBarcodes.isEmpty()) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "شارژ صفر است!",
                        null,
                        SnackbarDuration.Long
                    )
                }

            } else {
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
            }

            isDataLoading = false

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
            }

        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {
                val json = JSONObject()
                val epcArray = JSONArray()

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                inputBarcodes.forEach {
                    barcodeArray.put(it)
                }

                json.put("KBarCodes", barcodeArray)

                return json.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private suspend fun startRFScan() {

        isScanning = true
        if (!setRFPower(state, rf, rfPower)) {
            isScanning = false
            return
        }

        var epcTablePreviousSize = scannedEpcTable.size

        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        scannedEpcTable.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            scannedEpcTable = scannedEpcTable.distinct().toMutableList()

            numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size

            val speed = scannedEpcTable.size - epcTablePreviousSize
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
            epcTablePreviousSize = scannedEpcTable.size

            saveToMemory()

            delay(1000)
        }

        rf.stopInventory()
        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
        saveToMemory()
    }

    private fun syncScannedItemsToServer() {

        val epcArray = JSONArray()
        val barcodeArray = JSONArray()

        val alreadySyncedEpcs = mutableListOf<String>()
        val alreadySyncedBarcodes = mutableListOf<String>()
        products.forEach {
            if (it.scannedEPCNumber > 0) {
                alreadySyncedEpcs.addAll(it.scannedEPCs)
            }
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
        }

        scannedEpcTable.forEach {
            if (it !in alreadySyncedEpcs) {
                epcArray.put(it)
            }
        }

        scannedBarcodeTable.forEach {
            if (it !in alreadySyncedBarcodes) {
                barcodeArray.put(it)
            } else {
                val productIndex = products.indexOf(products.last { it1 ->
                    it1.scannedBarcode == it
                })
                products[productIndex].scannedBarcodeNumber =
                    scannedBarcodeTable.count { it1 ->
                        it1 == it
                    }
            }
        }

        if (epcArray.length() == 0 && barcodeArray.length() == 0) {
            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false
            return
        }

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/products/v4"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")

            for (i in 0 until epcs.length()) {
                val refillProduct = Product(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),

                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedEPCNumber = 1,
                    productCode = epcs.getJSONObject(i).getString("K_Bar_Code"),
                    size = epcs.getJSONObject(i).getString("Size"),
                    color = epcs.getJSONObject(i).getString("Color"),
                    originalPrice = epcs.getJSONObject(i).getString("OrgPrice"),
                    salePrice = epcs.getJSONObject(i).getString("SalePrice"),
                    rfidKey = epcs.getJSONObject(i).getLong("RFID"),
                    wareHouseNumber = epcs.getJSONObject(i).getInt("depoCount"),
                    kName = epcs.getJSONObject(i).getString("K_Name"),
                    scannedBarcode = "",
                    scannedEPCs = mutableListOf(epcs.getJSONObject(i).getString("epc")),
                    scannedBarcodeNumber = 0,
                    requestedNum = 0,
                    storeNumber = 0,
                )

                var isInRefillProductList = false
                products.forEach { it1 ->
                    if (it1.KBarCode == refillProduct.KBarCode) {
                        it1.scannedEPCs.add(refillProduct.scannedEPCs[0])
                        it1.scannedEPCNumber += 1
                        isInRefillProductList = true
                        return@forEach
                    }
                }
                if (!isInRefillProductList) {
                    products.add(refillProduct)
                }
            }

            for (i in 0 until barcodes.length()) {
                val refillProduct = Product(
                    name = barcodes.getJSONObject(i).getString("productName"),
                    KBarCode = barcodes.getJSONObject(i).getString("KBarCode"),
                    imageUrl = barcodes.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = barcodes.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedBarcodeNumber = 1,
                    productCode = barcodes.getJSONObject(i).getString("K_Bar_Code"),
                    size = barcodes.getJSONObject(i).getString("Size"),
                    color = barcodes.getJSONObject(i).getString("Color"),
                    originalPrice = barcodes.getJSONObject(i).getString("OrgPrice"),
                    salePrice = barcodes.getJSONObject(i).getString("SalePrice"),
                    rfidKey = barcodes.getJSONObject(i).getLong("RFID"),
                    wareHouseNumber = barcodes.getJSONObject(i).getInt("depoCount"),
                    scannedBarcode = barcodes.getJSONObject(i).getString("kbarcode"),
                    scannedEPCs = mutableListOf(),
                    kName = barcodes.getJSONObject(i).getString("K_Name"),
                    scannedEPCNumber = 0,
                    requestedNum = 0,
                    storeNumber = 0,
                )
                var isInRefillProductList = false
                products.forEach { it1 ->
                    if (it1.KBarCode == refillProduct.KBarCode) {
                        it1.scannedBarcode = refillProduct.scannedBarcode
                        it1.scannedBarcodeNumber += 1
                        isInRefillProductList = true
                        return@forEach
                    }
                }
                if (!isInRefillProductList) {
                    products.add(refillProduct)
                }
            }

            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false

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

            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {
                val json = JSONObject()
                json.put("epcs", epcArray)
                json.put("KBarCodes", barcodeArray)
                return json.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            scannedBarcodeTable.add(barcode)
            numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString(
            "ManualRefillWarehouseManagerEPCTable",
            JSONArray(scannedEpcTable).toString()
        )
        edit.putString(
            "ManualRefillWarehouseManagerBarcodeTable",
            JSONArray(scannedBarcodeTable).toString()
        )

        edit.putString(
            "ManualRefillProducts",
            Gson().toJson(products).toString()
        )

        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        val type = object : TypeToken<List<Product>>() {}.type

        scannedEpcTable = Gson().fromJson(
            memory.getString("ManualRefillWarehouseManagerEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("ManualRefillWarehouseManagerBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        products = Gson().fromJson(
            memory.getString("ManualRefillProducts", ""),
            type
        ) ?: ArrayList()

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
    }

    fun clear(product: Product) {

        val removedRefillProducts = mutableListOf<Product>()

        products.forEach {
            if (it.KBarCode == product.KBarCode) {

                scannedEpcTable.removeAll(it.scannedEPCs)
                scannedBarcodeTable.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                removedRefillProducts.add(it)
            }
        }
        products.removeAll(removedRefillProducts)
        removedRefillProducts.clear()

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
        uiList.clear()
        uiList.addAll(products)
        uiList.sortBy { it1 ->
            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
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
        stopRFScan()
        stopBarcodeScan()
        beep.release()
        queue.stop()
        finish()
    }

    private fun openSendToStoreActivity() {

        Intent(this, SendToStoreActivity::class.java).also {
            it.putExtra("ManualRefillProducts", Gson().toJson(products.filter { it1 ->
                it1.scannedBarcodeNumber + it1.scannedEPCNumber > 0
            }).toString())
            it.putExtra("ManualRefillValidScannedProductsNumber", numberOfScanned)
            startActivity(it)
        }
    }

    private fun openSearchActivity(product: Product) {

        val searchResultProduct = Product(
            name = product.name,
            KBarCode = product.KBarCode,
            imageUrl = product.imageUrl,
            color = product.color,
            size = product.size,
            productCode = product.productCode,
            rfidKey = product.rfidKey,
            primaryKey = product.primaryKey,
            originalPrice = product.originalPrice,
            salePrice = product.salePrice,
        )

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(searchResultProduct).toString())
        startActivity(intent)
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    bottomBar = { BottomBar() },
                    content = { Content() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun BottomBar() {
        BottomAppBar(
            backgroundColor = JeanswestBottomBar,
            modifier = Modifier.wrapContentHeight()
        ) {

            Column {

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    ScanTypeDropDownList(
                        Modifier.align(Alignment.CenterVertically), scanTypeValue
                    ) {
                        scanTypeValue = it
                    }

                    Button(onClick = {
                        Intent(
                            this@ManualRefillActivity,
                            AddProductToRefillListActivity::class.java
                        ).apply {
                            startActivity(this)
                        }
                    }) {
                        Text(text = "تعریف شارژ")
                    }

                    Button(onClick = { openSendToStoreActivity() }) {
                        Text(text = "ارسال")
                    }
                }
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
                    text = stringResource(id = R.string.manualRefill),
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

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .background(JeanswestBackground, Shapes.small)
                    .fillMaxWidth()
            ) {

                PowerSlider(scanTypeValue == "RFID", rfPower) { rfPower = it }
                LoadingCircularProgressIndicator(isScanning, isDataLoading)
            }

            LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {

                items(uiList.size) { i ->
                    LazyColumnItem(i)
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
                text1 = "اسکن: " + uiList[i].scannedNumber,
                text2 = "انبار: " + uiList[i].wareHouseNumber,
                colorFull = uiList[i].scannedNumber >= uiList[i].requestedNum,
                enableWarehouseNumberCheck = true,
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
}