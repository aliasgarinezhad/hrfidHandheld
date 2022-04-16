package com.jeanwest.reader.refill

//import com.jeanwest.reader.hardware.Barcode2D
//import com.rscja.deviceapi.RFIDWithUHFUART
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.hardware.setRFEpcMode
import com.jeanwest.reader.hardware.setRFPower
import com.jeanwest.reader.search.SearchResultProducts
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.hardware.Barcode2D
import com.rscja.deviceapi.RFIDWithUHFUART
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject


@ExperimentalCoilApi
class RefillActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 5
    private var scannedEpcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var scannedBarcodeTable = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    val inputBarcodes = ArrayList<String>()
    private var refillProducts = mutableListOf<RefillProduct>()
    private var scanningJob: Job? = null

    //ui parameters
    private var isScanning by mutableStateOf(false)
    private var numberOfScanned by mutableStateOf(0)
    private var unFoundProductsNumber by mutableStateOf(0)
    private var uiList by mutableStateOf(mutableListOf<RefillProduct>())
    private var openClearDialog by mutableStateOf(false)
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    var refillSignedProductCodes = mutableListOf<String>()
    private val scanTypeValues = mutableListOf("RFID", "بارکد")
    private var scanTypeValue by mutableStateOf("بارکد")
    private var state = SnackbarHostState()
    private var isDataLoading by mutableStateOf(false)
    private var selectMode by mutableStateOf(false)

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()

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
        getRefillBarcodes()
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

    private suspend fun startRFScan() {

        isScanning = true
        if (!setRFPower(state, rf, rfPower)) {
            isScanning = false
            return
        }

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

    private fun getRefillBarcodes() {

        val url = "http://rfid-api.avakatan.ir/refill"

        val request = object : JsonArrayRequest(Method.GET, url, null, {

            inputBarcodes.clear()

            for (i in 0 until it.length()) {

                inputBarcodes.add(it.getJSONObject(i).getString("KBarCode"))
            }
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

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun getRefillItems() {

        val url = "http://rfid-api.avakatan.ir/products/v4"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val refillItemsJsonArray = it.getJSONArray("KBarCodes")
            refillProducts.clear()

            for (i in 0 until refillItemsJsonArray.length()) {

                val fileProduct = RefillProduct(
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
                    scannedEPCNumber = 0
                )
                refillProducts.add(fileProduct)
            }

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
            } else {

                uiList = mutableListOf()
                refillProducts.sortBy { refillProduct ->
                    refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
                }
                uiList = refillProducts
                unFoundProductsNumber = uiList.size
            }

            isDataLoading = false

        }, {

            if (inputBarcodes.isEmpty()) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "خطی صفر است!",
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

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
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

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun syncScannedItemsToServer() {

        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")

            scannedEpcTable.clear()
            for (i in 0 until epcs.length()) {

                val isInRefillList = refillProducts.any { refillProduct ->
                    refillProduct.primaryKey == epcs.getJSONObject(i).getLong("BarcodeMain_ID")
                }

                if (isInRefillList) {

                    val productIndex = refillProducts.indexOf(refillProducts.last { refillProduct ->
                        refillProduct.primaryKey == epcs.getJSONObject(i).getLong("BarcodeMain_ID")
                    })

                    refillProducts[productIndex].scannedEPCNumber =
                        epcs.getJSONObject(i).getInt("handheldCount")

                    refillProducts[productIndex].scannedEPCs.clear()
                    for (j in 0 until epcs.getJSONObject(i).getJSONArray("reportedEpcs").length()) {
                        val epc = epcs.getJSONObject(i).getJSONArray("reportedEpcs").getString(j)
                        refillProducts[productIndex].scannedEPCs.add(epc)
                        scannedEpcTable.add(epc)
                    }
                }
            }

            scannedBarcodeTable.clear()
            for (i in 0 until barcodes.length()) {

                val isInRefillList = refillProducts.any { refillProduct ->
                    refillProduct.primaryKey == barcodes.getJSONObject(i).getLong("BarcodeMain_ID")
                }

                if (isInRefillList) {

                    val productIndex = refillProducts.indexOf(refillProducts.last { refillProduct ->
                        refillProduct.primaryKey == barcodes.getJSONObject(i)
                            .getLong("BarcodeMain_ID")
                    })

                    refillProducts[productIndex].scannedBarcodeNumber =
                        barcodes.getJSONObject(i).getInt("handheldCount")

                    refillProducts[productIndex].scannedBarcode =
                        barcodes.getJSONObject(i).getString("kbarcode")

                    repeat(refillProducts[productIndex].scannedBarcodeNumber) {
                        scannedBarcodeTable.add(refillProducts[productIndex].scannedBarcode)
                    }
                }
            }

            uiList = mutableListOf()
            refillProducts.sortBy { refillProduct ->
                refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
            }
            uiList = refillProducts
            unFoundProductsNumber = uiList.filter { refillProduct ->
                refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber == 0
            }.size

            numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size

        }, {
            if ((scannedEpcTable.size + scannedBarcodeTable.size) == 0) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "کالایی جهت بررسی وجود ندارد",
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
            uiList = mutableListOf()
            uiList = refillProducts
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

                scannedEpcTable.forEach {
                    epcArray.put(it)
                }

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                scannedBarcodeTable.forEach {
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

        val queue = Volley.newRequestQueue(this)
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

        edit.putString("RefillBarcodeTable", JSONArray(scannedBarcodeTable).toString())
        edit.putString("RefillEPCTable", JSONArray(scannedEpcTable).toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scannedEpcTable = Gson().fromJson(
            memory.getString("RefillEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = scannedEpcTable.size

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("RefillBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
    }

    fun refillClear() {

        refillProducts.forEach {
            if (it.KBarCode in refillSignedProductCodes) {
                it.scannedEPCNumber = 0
                it.scannedBarcodeNumber = 0
                scannedEpcTable.removeAll(it.scannedEPCs)
                scannedBarcodeTable.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
            }
        }
        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
        uiList = mutableListOf()
        uiList = refillProducts
        refillSignedProductCodes = mutableListOf()
        unFoundProductsNumber = uiList.filter { refillProduct ->
            refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber == 0
        }.size
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
        finish()
    }

    private fun openSendToStoreActivity() {

        Intent(this, SendRefillProductsToStoreActivity::class.java).also {
            it.putExtra("RefillProducts", Gson().toJson(refillProducts.filter { it1 ->
                it1.scannedBarcodeNumber + it1.scannedEPCNumber > 0
            }).toString())
            it.putExtra("validScannedProductsNumber", numberOfScanned)
            startActivity(it)
        }
    }

    private fun openSearchActivity(product: RefillProduct) {

        val searchResultProduct = SearchResultProducts(
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
            shoppingNumber = 0,
            warehouseNumber = 0
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
                    content = { Content() },
                    bottomBar = { if (selectMode) SelectedBottomAppBar() else BottomAppBar() },
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
                    text = stringResource(id = R.string.refill),
                    modifier = Modifier
                        .padding(end = 50.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @Composable
    fun BottomAppBar() {

        BottomAppBar(
            backgroundColor = colorResource(id = R.color.JeanswestBottomBar),
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
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Text(
                        text = "خطی: ${uiList.size}",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Text(
                        text = "پیدا نشده: $unFoundProductsNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Button(onClick = { openSendToStoreActivity() }) {
                        Text(text = "ارسال")
                    }
                }
            }
        }
    }

    @Composable
    fun SelectedBottomAppBar() {

        BottomAppBar(
            backgroundColor = colorResource(id = R.color.JeanswestBottomBar),
            modifier = Modifier.wrapContentHeight()
        ) {

            Column {

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = {
                        uiList.forEach {
                            refillSignedProductCodes.add(it.KBarCode)
                            uiList = mutableListOf()
                            uiList = refillProducts
                        }
                    }) {
                        Text(text = "انتخاب همه")
                    }
                    Button(onClick = {
                        openClearDialog = true
                    }) {
                        Text(text = "پاک کردن")
                    }
                    Button(onClick = {
                        refillSignedProductCodes.clear()
                        selectMode = false
                        uiList = mutableListOf()
                        uiList = refillProducts
                    }) {
                        Text(text = "بازگشت")
                    }
                }
            }
        }
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        var slideValue by rememberSaveable { mutableStateOf(rfPower.toFloat()) }

        Column {

            if (openClearDialog) {
                ClearAlertDialog()
            }

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
            ) {

                if (scanTypeValue == "RFID") {
                    Row {

                        Text(
                            text = "قدرت آنتن (" + slideValue.toInt() + ")  ",
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .align(Alignment.CenterVertically),
                            textAlign = TextAlign.Center
                        )

                        Slider(
                            value = slideValue,
                            onValueChange = {
                                slideValue = it
                                rfPower = it.toInt()
                            },
                            enabled = true,
                            valueRange = 5f..30f,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                }

                if (isScanning || isDataLoading) {
                    Row(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.primary)

                        if (isScanning) {
                            Text(
                                text = "در حال اسکن",
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                        if (isDataLoading) {
                            Text(
                                text = "در حال بارگذاری",
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 8.dp, bottom = 56.dp).testTag("RefillActivityLazyColumn")) {

                items(uiList.size) { i ->
                    LazyColumnItem(i)
                }
            }
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun LazyColumnItem(i: Int) {

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .background(
                    color = if (uiList[i].scannedBarcodeNumber + uiList[i].scannedEPCNumber == 0) {
                        MaterialTheme.colors.onPrimary
                    } else {
                        MaterialTheme.colors.onSecondary

                    },
                    shape = MaterialTheme.shapes.small
                )

                .fillMaxWidth()
                .height(80.dp)
                .combinedClickable(
                    onClick = {
                        if (!selectMode) {
                            openSearchActivity(uiList[i])
                        } else {
                            if (uiList[i].KBarCode !in refillSignedProductCodes) {
                                refillSignedProductCodes.add(uiList[i].KBarCode)
                            } else {
                                refillSignedProductCodes.remove(uiList[i].KBarCode)
                            }
                            uiList = mutableListOf()
                            uiList = refillProducts
                        }
                    },
                    onLongClick = {
                        selectMode = true
                        if (uiList[i].KBarCode !in refillSignedProductCodes) {
                            refillSignedProductCodes.add(uiList[i].KBarCode)
                        } else {
                            refillSignedProductCodes.remove(uiList[i].KBarCode)
                        }
                        uiList = mutableListOf()
                        uiList = refillProducts
                    },
                )
                .testTag("refillItems"),
        ) {

            if (uiList[i].KBarCode in refillSignedProductCodes) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_check_24),
                    tint = MaterialTheme.colors.primary,
                    contentDescription = "",
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterVertically)
                )
            }

            Image(
                painter = rememberImagePainter(
                    uiList[i].imageUrl,
                ),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            )

            Row(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.5F)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = uiList[i].name,
                        style = MaterialTheme.typography.h1,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.testTag("refillBarcodes")
                    )

                    Text(
                        text = uiList[i].KBarCode,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1F)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "موجودی انبار: " + uiList[i].wareHouseNumber,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                    Text(
                        text = "اسکن شده: " + (uiList[i].scannedEPCNumber + uiList[i].scannedBarcodeNumber).toString(),
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                }
            }
        }
    }

    @Composable
    fun ClearAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openClearDialog = false
            },
            buttons = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = "کالاهای انتخاب شده پاک شوند؟",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 22.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openClearDialog = false
                            refillClear()
                            selectMode = false

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = { openClearDialog = false },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "خیر")
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun ScanTypeDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = true }) {
                Text(text = scanTypeValue)
                Icon(
                    painter = if (!expanded) {
                        painterResource(id = R.drawable.ic_baseline_arrow_drop_down_24)
                    } else {
                        painterResource(id = R.drawable.ic_baseline_arrow_drop_up_24)
                    }, ""
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                scanTypeValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        scanTypeValue = it
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }
}