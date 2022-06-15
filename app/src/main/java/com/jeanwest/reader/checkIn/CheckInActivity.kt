package com.jeanwest.reader.checkIn

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
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.hardware.setRFEpcMode
import com.jeanwest.reader.hardware.setRFPower
import com.jeanwest.reader.manualRefill.Product
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.theme.*
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
class CheckInActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var epcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var barcodeTable = mutableListOf<String>()
    private var excelBarcodes = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    private val inputProducts = mutableListOf<CheckInInputProduct>()
    private val scannedProducts = mutableListOf<CheckInScannedProduct>()
    private var scanningJob: Job? = null
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    //ui parameters
    private var conflictResultProducts = mutableStateListOf<Product>()
    private var isScanning by mutableStateOf(false)
    private var isDataLoading by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var shortageCodesNumber by mutableStateOf(0)
    private var additionalCodesNumber by mutableStateOf(0)
    private var numberOfScanned by mutableStateOf(0)
    private var uiList = mutableStateListOf<Product>()
    private val scanValues = mutableListOf("همه اجناس", "تایید شده", "اضافی", "کسری")
    private var scanFilter by mutableStateOf(0)
    private var openClearDialog by mutableStateOf(false)
    var scanTypeValue by mutableStateOf("RFID")
    private var state = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        excelBarcodes = Gson().fromJson(
            intent.getStringExtra("CheckInFileBarcodeTable"),
            excelBarcodes.javaClass
        ) ?: mutableListOf()

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
        syncInputItemsToServer()
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
                        epcTable.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            epcTable = epcTable.distinct().toMutableList()

            numberOfScanned = epcTable.size + barcodeTable.size

            val speed = epcTable.size - epcTablePreviousSize
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
            epcTablePreviousSize = epcTable.size

            saveToMemory()

            delay(1000)
        }

        rf.stopInventory()
        numberOfScanned = epcTable.size + barcodeTable.size
        saveToMemory()
    }

    private fun getConflicts(
        fileProducts: MutableList<CheckInInputProduct>,
        scannedProducts: MutableList<CheckInScannedProduct>,
    ): SnapshotStateList<Product> {

        val result = ArrayList<Product>()

        val samePrimaryKeys = ArrayList<Long>()

        scannedProducts.forEach { scannedProduct ->

            fileProducts.forEach { fileProduct ->

                if (scannedProduct.primaryKey == fileProduct.primaryKey) {

                    val resultData = Product(
                        name = fileProduct.name,
                        KBarCode = fileProduct.KBarCode,
                        imageUrl = fileProduct.imageUrl,
                        matchedNumber = abs(scannedProduct.scannedNumber - fileProduct.number),
                        scannedEPCNumber = scannedProduct.scannedNumber,
                        fileNumber = fileProduct.number,
                        result =
                        when {
                            scannedProduct.scannedNumber > fileProduct.number -> {
                                "اضافی: " + (scannedProduct.scannedNumber - fileProduct.number)
                            }
                            scannedProduct.scannedNumber < fileProduct.number -> {
                                "کسری: " + (fileProduct.number - scannedProduct.scannedNumber)
                            }
                            else -> {
                                "تایید شده: " + fileProduct.number
                            }
                        },
                        scan = when {
                            scannedProduct.scannedNumber > fileProduct.number -> {
                                "اضافی"
                            }
                            scannedProduct.scannedNumber < fileProduct.number -> {
                                "کسری"
                            }
                            else -> {
                                "تایید شده"
                            }
                        },
                        productCode = fileProduct.productCode,
                        size = fileProduct.size,
                        color = fileProduct.color,
                        originalPrice = fileProduct.originalPrice,
                        salePrice = fileProduct.salePrice,
                        rfidKey = fileProduct.rfidKey,
                        primaryKey = fileProduct.primaryKey
                    )
                    result.add(resultData)
                    samePrimaryKeys.add(fileProduct.primaryKey)
                }
            }
            if (scannedProduct.primaryKey !in samePrimaryKeys) {
                val resultData = Product(
                    name = scannedProduct.name,
                    KBarCode = scannedProduct.KBarCode,
                    imageUrl = scannedProduct.imageUrl,
                    matchedNumber = scannedProduct.scannedNumber,
                    scannedEPCNumber = scannedProduct.scannedNumber,
                    result = "اضافی: " + scannedProduct.scannedNumber,
                    scan = "اضافی",
                    productCode = scannedProduct.productCode,
                    size = scannedProduct.size,
                    color = scannedProduct.color,
                    originalPrice = scannedProduct.originalPrice,
                    salePrice = scannedProduct.salePrice,
                    rfidKey = scannedProduct.rfidKey,
                    primaryKey = scannedProduct.primaryKey,
                    fileNumber = 0,
                )
                result.add(resultData)
            }
        }

        fileProducts.forEach { fileProduct ->
            if (fileProduct.primaryKey !in samePrimaryKeys) {
                val resultData = Product(
                    name = fileProduct.name,
                    KBarCode = fileProduct.KBarCode,
                    imageUrl = fileProduct.imageUrl,
                    matchedNumber = fileProduct.number,
                    scannedEPCNumber = 0,
                    result = "کسری: " + fileProduct.number,
                    scan = "کسری",
                    productCode = fileProduct.productCode,
                    size = fileProduct.size,
                    color = fileProduct.color,
                    originalPrice = fileProduct.originalPrice,
                    salePrice = fileProduct.salePrice,
                    rfidKey = fileProduct.rfidKey,
                    primaryKey = fileProduct.primaryKey,
                    fileNumber = fileProduct.number,
                )
                result.add(resultData)
            }
        }
        return result.toMutableStateList()
    }

    private fun filterResult(conflictResult: MutableList<Product>): SnapshotStateList<Product> {

        shortagesNumber = 0
        conflictResult.filter {
            it.scan == "کسری"
        }.forEach {
            shortagesNumber += it.matchedNumber
        }

        additionalNumber = 0
        conflictResult.filter {
            it.scan == "اضافی"
        }.forEach {
            additionalNumber += it.matchedNumber
        }

        shortageCodesNumber = conflictResult.filter { it.scan == "کسری" }.size
        additionalCodesNumber =
            conflictResult.filter { it.scan == "اضافی" }.size

        val uiListParameters =
            when {
                scanValues[scanFilter] == "همه اجناس" -> {
                    conflictResult
                }
                scanValues[scanFilter] == "اضافی" -> {
                    conflictResult.filter {
                        it.scan == "اضافی"
                    } as ArrayList<Product>
                }
                scanValues[scanFilter] == "کسری" -> {
                    conflictResult.filter {
                        it.scan == "کسری"
                    } as ArrayList<Product>
                }
                scanValues[scanFilter] == "تایید شده" -> {
                    conflictResult.filter {
                        it.scan == "تایید شده"
                    } as ArrayList<Product>
                }
                else -> {
                    conflictResult
                }
            }

        return uiListParameters.toMutableStateList()
    }

    private fun syncInputItemsToServer() {

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val fileJsonArray = it.getJSONArray("KBarCodes")

            inputProducts.clear()
            for (i in 0 until fileJsonArray.length()) {

                val fileProduct = CheckInInputProduct(
                    name = fileJsonArray.getJSONObject(i).getString("productName"),
                    KBarCode = fileJsonArray.getJSONObject(i).getString("KBarCode"),
                    imageUrl = fileJsonArray.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = fileJsonArray.getJSONObject(i).getLong("BarcodeMain_ID"),
                    number = fileJsonArray.getJSONObject(i).getInt("handheldCount"),
                    productCode = fileJsonArray.getJSONObject(i).getString("K_Bar_Code"),
                    size = fileJsonArray.getJSONObject(i).getString("Size"),
                    color = fileJsonArray.getJSONObject(i).getString("Color"),
                    originalPrice = fileJsonArray.getJSONObject(i).getString("OrgPrice"),
                    salePrice = fileJsonArray.getJSONObject(i).getString("SalePrice"),
                    rfidKey = fileJsonArray.getJSONObject(i).getLong("RFID"),
                )
                inputProducts.add(fileProduct)
            }

            isDataLoading = false

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
            } else {
                conflictResultProducts = getConflicts(inputProducts, scannedProducts)
                uiList = filterResult(conflictResultProducts)
            }

        }, {

            if (excelBarcodes.isEmpty()) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "هیچ شماره حواله ای وارد نشده است",
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

                excelBarcodes.forEach {
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

        val queue = Volley.newRequestQueue(this@CheckInActivity)
        queue.add(request)
    }

    private fun syncScannedItemsToServer() {

        isDataLoading = true


        val epcArray = JSONArray()
        val barcodeArray = JSONArray()

        val alreadySyncedEpcs = mutableListOf<String>()
        val alreadySyncedBarcodes = mutableListOf<String>()

        scannedProducts.forEach {
            if (it.scannedEPCNumber > 0) {
                alreadySyncedEpcs.addAll(it.scannedEPCs)
            }
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
        }

        epcTable.forEach {
            if (it !in alreadySyncedEpcs) {
                epcArray.put(it)
            }
        }

        barcodeTable.forEach {
            if (it !in alreadySyncedBarcodes) {
                barcodeArray.put(it)
            } else {
                val productIndex = scannedProducts.indexOf(
                    scannedProducts.last { it1 ->
                        it1.scannedBarcode == it
                    })
                scannedProducts[productIndex].scannedBarcodeNumber =
                    barcodeTable.count { it1 ->
                        it1 == it
                    }
            }
        }

        if (epcArray.length() == 0 && barcodeArray.length() == 0) {
            conflictResultProducts = getConflicts(inputProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts)
            isDataLoading = false
            return
        }

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/products/v4"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")

            for (i in 0 until epcs.length()) {

                val scannedProduct = CheckInScannedProduct(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    productCode = epcs.getJSONObject(i).getString("K_Bar_Code"),
                    size = epcs.getJSONObject(i).getString("Size"),
                    color = epcs.getJSONObject(i).getString("Color"),
                    originalPrice = epcs.getJSONObject(i).getString("OrgPrice"),
                    salePrice = epcs.getJSONObject(i).getString("SalePrice"),
                    rfidKey = epcs.getJSONObject(i).getLong("RFID"),
                    scannedEPCNumber = 1,
                    scannedBarcode = "",
                    scannedEPCs = mutableListOf(epcs.getJSONObject(i).getString("epc")),
                    scannedBarcodeNumber = 0,
                )
                var isInScannedProductsList = false
                scannedProducts.forEach { it1 ->
                    if (it1.KBarCode == scannedProduct.KBarCode) {
                        it1.scannedEPCs.add(scannedProduct.scannedEPCs[0])
                        it1.scannedEPCNumber += 1
                        isInScannedProductsList = true
                        return@forEach
                    }
                }
                if (!isInScannedProductsList) {
                    scannedProducts.add(scannedProduct)
                }
            }

            for (i in 0 until barcodes.length()) {

                val scannedProduct = CheckInScannedProduct(
                    name = barcodes.getJSONObject(i).getString("productName"),
                    KBarCode = barcodes.getJSONObject(i).getString("KBarCode"),
                    imageUrl = barcodes.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = barcodes.getJSONObject(i).getLong("BarcodeMain_ID"),
                    productCode = barcodes.getJSONObject(i).getString("K_Bar_Code"),
                    size = barcodes.getJSONObject(i).getString("Size"),
                    color = barcodes.getJSONObject(i).getString("Color"),
                    originalPrice = barcodes.getJSONObject(i).getString("OrgPrice"),
                    salePrice = barcodes.getJSONObject(i).getString("SalePrice"),
                    rfidKey = barcodes.getJSONObject(i).getLong("RFID"),
                    scannedEPCNumber = 0,
                    scannedBarcode = barcodes.getJSONObject(i).getString("kbarcode"),
                    scannedEPCs = mutableListOf(),
                    scannedBarcodeNumber = 1,
                )
                var isInScannedProductsList = false
                scannedProducts.forEach { it1 ->
                    if (it1.KBarCode == scannedProduct.KBarCode) {
                        it1.scannedBarcode = scannedProduct.scannedBarcode
                        it1.scannedBarcodeNumber += 1
                        isInScannedProductsList = true
                        return@forEach
                    }
                }
                if (!isInScannedProductsList) {
                    scannedProducts.add(scannedProduct)
                }
            }

            conflictResultProducts = getConflicts(inputProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts)

            isDataLoading = false

        }, {
            if ((epcTable.size + barcodeTable.size) == 0) {

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
            conflictResultProducts = getConflicts(inputProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts)

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

        val queue = Volley.newRequestQueue(this@CheckInActivity)
        queue.add(request)
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            barcodeTable.add(barcode)
            numberOfScanned = epcTable.size + barcodeTable.size
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("CheckInEPCTable", JSONArray(epcTable).toString())
        edit.putString("CheckInBarcodeTable", JSONArray(barcodeTable).toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        epcTable = Gson().fromJson(
            memory.getString("CheckInEPCTable", ""),
            epcTable.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = epcTable.size

        barcodeTable = Gson().fromJson(
            memory.getString("CheckInBarcodeTable", ""),
            barcodeTable.javaClass
        ) ?: mutableListOf()

        numberOfScanned = epcTable.size + barcodeTable.size
    }

    private fun clear() {

        barcodeTable.clear()
        epcTable.clear()
        epcTablePreviousSize = 0
        numberOfScanned = 0
        scannedProducts.clear()
        conflictResultProducts = getConflicts(inputProducts, scannedProducts)
        uiList = filterResult(conflictResultProducts)
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

    private fun openConfirmCheckInsActivity() {

        Intent(this, ConfirmCheckInsActivity::class.java).also {
            it.putExtra(
                "additionalAndShortageProducts",
                Gson().toJson(conflictResultProducts).toString()
            )
            it.putExtra("numberOfScanned", numberOfScanned)
            it.putExtra("shortagesNumber", shortagesNumber)
            it.putExtra("additionalNumber", additionalNumber)
            startActivity(it)
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                    snackbarHost = { ErrorSnackBar(state) },
                    bottomBar = { BottomBar() },
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

            actions = {

                IconButton(
                    modifier = Modifier.testTag("CheckInTestTag"),
                    onClick = { openClearDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.checkInText),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Right,
                )
            }
        )
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
                    ScanFilterDropDownList(modifier = Modifier.align(Alignment.CenterVertically))
                    Button(onClick = { openConfirmCheckInsActivity() }) {
                        Text(text = "تایید حواله ها")
                    }
                }
            }
        }
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        Column {

            if (openClearDialog) {
                ClearAlertDialog()
            }

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = "اسکن شده: $numberOfScanned",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .align(Alignment.CenterVertically)
                            .weight(1F),
                    )
                    Text(
                        text = "کسری: $shortagesNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1F),
                    )
                    Text(
                        text = "اضافی: $additionalNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1F),
                    )
                }

                PowerSlider(scanTypeValue == "RFID", rfPower) { rfPower = it }
                LoadingCircularProgressIndicator(isScanning, isDataLoading)
            }

            LazyColumn(modifier = Modifier.padding(top = 2.dp, bottom = 56.dp)) {

                items(uiList.size) { i ->
                    Item(
                        i,
                        uiList,
                        true,
                        text1 = "موجودی: " + uiList[i].fileNumber,
                        text2 = uiList[i].result
                    ) {
                        openSearchActivity(uiList[i])
                    }
                }
            }
        }
    }

    @Composable
    fun ScanFilterDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
            Row(
                modifier = Modifier
                    .testTag("checkInFilterDropDownList")
                    .clickable { expanded = true }) {
                Text(text = scanValues[scanFilter])
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                scanValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        scanFilter = scanValues.indexOf(it)
                        uiList = filterResult(conflictResultProducts)
                    }) {
                        Text(text = it)
                    }
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
                        text = "کالاهای اسکن شده پاک شوند؟",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 22.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openClearDialog = false
                            clear()

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
}