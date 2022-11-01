package com.jeanwest.reader.checkIn

import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.shared.*
import com.jeanwest.reader.shared.test.Barcode2D
import com.jeanwest.reader.shared.test.RFIDWithUHFUART
import com.jeanwest.reader.shared.theme.JeanswestBottomBar
import com.jeanwest.reader.shared.theme.MyApplicationTheme
import com.jeanwest.reader.shared.theme.borderColor
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalFoundationApi::class)
class CheckInActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var scannedEpcs = mutableListOf<String>()
    private var scannedBarcodes = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    val inputProducts = mutableMapOf<String, Product>()
    private var scannedProducts = mutableMapOf<String, Product>()
    private var scanningJob: Job? = null
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var inputBarcodeMapWithProperties = mutableMapOf<String, Product>()
    private var scannedEpcMapWithProperties = mutableMapOf<String, Product>()
    private var scannedBarcodeMapWithProperties = mutableMapOf<String, Product>()
    var draftProperties = DraftProperties(number = 0L, numberOfItems = 0)

    //ui parameters
    var productConflicts = mutableStateListOf<Product>()
    private var rfidScan by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var loading by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var shortageCodesNumber by mutableStateOf(0)
    private var additionalCodesNumber by mutableStateOf(0)
    private var numberOfScanned by mutableStateOf(0)
    private var uiList = mutableStateListOf<Product>()
    private val scanValues = mutableListOf("اضافی", "کسری")
    private var scanFilter by mutableStateOf(1)
    private var openClearDialog by mutableStateOf(false)
    var scanTypeValue by mutableStateOf("RFID")
    private var state = SnackbarHostState()
    private lateinit var queue: RequestQueue
    private var stockDraftNumber by mutableStateOf("")
    var scanningMode by mutableStateOf(false)
    private var openFinishDialog by mutableStateOf(false)

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

        if (scanningMode) {
            syncInputItemsToServer()
        }

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (scanningMode) {
                    if (scanTypeValue == "بارکد") {
                        stopRFScan()
                        startBarcodeScan()
                    } else {
                        if (!rfidScan) {

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
                } else {
                    startBarcodeScan()
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
                    if (uhfTagInfo.epc.startsWith("30") && uhfTagInfo.epc in draftProperties.epcTable) {
                        scannedEpcs.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            scannedEpcs = scannedEpcs.distinct().toMutableList()

            numberOfScanned = scannedEpcs.size + scannedBarcodes.size

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
        numberOfScanned = scannedEpcs.size + scannedBarcodes.size
        saveToMemory()
        scanning = false
    }

    private fun calculateConflicts() {

        val conflicts = mutableListOf<Product>()

        conflicts.addAll(inputProducts.filter {
            it.key !in scannedProducts.keys
        }.values)

        conflicts.addAll(scannedProducts.filter {
            it.key !in inputProducts.keys
        }.values)

        inputProducts.filter {
            it.key in scannedProducts.keys
        }.forEach {

            val product: Product = it.value.copy()
            product.scannedEPCs = scannedProducts[it.key]!!.scannedEPCs
            product.scannedBarcodeNumber = scannedProducts[it.key]!!.scannedBarcodeNumber
            product.scannedBarcode = scannedProducts[it.key]!!.scannedBarcode
            conflicts.add(product)
        }

        productConflicts.clear()
        productConflicts.addAll(conflicts)
    }

    private fun filterResult(conflictResult: MutableList<Product>) {

        shortagesNumber = 0
        conflictResult.filter {
            it.conflictType == "کسری"
        }.forEach {
            shortagesNumber += it.conflictNumber
        }

        additionalNumber = 0
        conflictResult.filter {
            it.conflictType == "اضافی"
        }.forEach {
            additionalNumber += it.conflictNumber
        }

        shortageCodesNumber = conflictResult.filter { it.conflictType == "کسری" }.size
        additionalCodesNumber =
            conflictResult.filter { it.conflictType == "اضافی" }.size

        val uiListParameters =
            when {
                scanValues[scanFilter] == "اضافی" -> {
                    conflictResult.filter {
                        it.conflictType == "اضافی"
                    } as ArrayList<Product>
                }
                scanValues[scanFilter] == "کسری" -> {
                    conflictResult.filter {
                        it.conflictType == "کسری"
                    } as ArrayList<Product>
                }
                else -> {
                    conflictResult
                }
            }
        uiList.clear()
        uiList.addAll(uiListParameters)
        uiList.sortBy {
            it.productCode
        }
        uiList.sortBy {
            it.name
        }
    }


    private fun syncInputItemsToServer() {

        loading = true

        val barcodeTableForV4 = mutableListOf<String>()
        var inputProductsBiggerThan1000 = false

        Log.e("draftBarcodes", draftProperties.barcodeTable.toString())

        run breakForEach@{
            draftProperties.barcodeTable.distinct().forEach {
                if (it !in inputBarcodeMapWithProperties.keys) {
                    if (barcodeTableForV4.size < 1000) {
                        barcodeTableForV4.add(it)
                    } else {
                        inputProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }

        if (barcodeTableForV4.size == 0) {
            syncScannedItemsToServer()
            loading = false
            return
        }

        getProductsV4(
            queue,
            state,
            mutableListOf(),
            barcodeTableForV4,
            { _, barcodes, _, _ ->

                barcodes.forEach { product ->
                    inputBarcodeMapWithProperties[product.scannedBarcode] = product
                }

                if (inputProductsBiggerThan1000) {
                    syncInputItemsToServer()
                } else {
                    makeInputProductMap()
                    syncScannedItemsToServer()
                }

            },
            {
                loading = false
            })
    }

    private fun makeInputProductMap() {

        inputProducts.clear()
        draftProperties.barcodeTable.distinct().forEach {
            val product = inputBarcodeMapWithProperties[it]!!.copy()
            product.draftNumber = draftProperties.barcodeTable.count { it1 ->
                it == it1
            }

            if (product.KBarCode !in inputProducts.keys) {
                inputProducts[product.KBarCode] = product.copy()
            } else {
                inputProducts[product.KBarCode]!!.draftNumber += product.draftNumber
            }
        }
    }

    private fun syncScannedItemsToServer() {

        loading = true

        if (numberOfScanned == 0) {
            calculateConflicts()
            filterResult(productConflicts)
            loading = false
            return
        }

        val epcArray = mutableListOf<String>()
        val barcodeArray = mutableListOf<String>()
        var scannedProductsBiggerThan1000 = false

        run breakForEach@{
            scannedEpcs.forEach {
                if (it !in scannedEpcMapWithProperties.keys) {
                    if (epcArray.size < 1000) {
                        epcArray.add(it)
                    } else {
                        scannedProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }
        run breakForEach@{
            scannedBarcodes.forEach {

                if (it !in scannedBarcodeMapWithProperties.keys && it !in barcodeArray) {
                    if (barcodeArray.size < 1000) {
                        barcodeArray.add(it)
                    } else {
                        scannedProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }

        if (epcArray.size == 0 && barcodeArray.size == 0) {

            makeScannedProductMap()
            calculateConflicts()
            filterResult(productConflicts)
            loading = false
            return
        }

        getProductsV4(
            queue,
            state,
            epcArray,
            barcodeArray,
            { epcs, barcodes, invalidEpcs, invalidBarcodes ->

                epcs.forEach {
                    scannedEpcMapWithProperties[it.scannedEPCs[0]] = it
                }

                barcodes.forEach {
                    scannedBarcodeMapWithProperties[it.scannedBarcode] = it
                }

                for (i in 0 until invalidBarcodes.length()) {
                    scannedBarcodes.remove(invalidBarcodes[i])
                }
                for (i in 0 until invalidEpcs.length()) {
                    scannedEpcs.remove(invalidEpcs[i])
                }

                if (scannedProductsBiggerThan1000) {
                    syncScannedItemsToServer()
                } else {
                    makeScannedProductMap()
                    calculateConflicts()
                    filterResult(productConflicts)
                    loading = false
                }

            },
            {
                calculateConflicts()
                filterResult(productConflicts)
                loading = false
            })
    }

    private fun makeScannedProductMap() {

        scannedProducts.clear()

        scannedEpcs.forEach {

            val product = scannedEpcMapWithProperties[it]!!

            if (product.KBarCode in scannedProducts.keys) {
                scannedProducts[product.KBarCode]!!.scannedEPCs.add(it)
            } else {
                scannedProducts[product.KBarCode] = product.copy(scannedEPCs = mutableListOf(it))
                scannedProducts[product.KBarCode]!!.scannedEPCs = mutableListOf(it)
            }
        }

        scannedBarcodes.distinct().forEach {

            val product = scannedBarcodeMapWithProperties[it]!!.copy()
            product.scannedBarcodeNumber = scannedBarcodes.count { it1 ->
                it == it1
            }

            if (product.KBarCode !in scannedProducts.keys) {
                scannedProducts[product.KBarCode] = product.copy()
            } else {
                scannedProducts[product.KBarCode]!!.scannedBarcodeNumber += product.scannedBarcodeNumber
            }
        }

        numberOfScanned = scannedBarcodes.size + scannedEpcs.size
    }

    private fun brokenEpcs() {

        loading = true
        val url =
            "http://rfid-api.avakatan.ir/stock-draft/not-found-epc"
        val request = object : JsonObjectRequest(Method.POST, url, null, {
            Log.e("info", "broken epcs log sent")
            loading = false

        }, {
            if (it is NoConnectionError) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {

                val error = it.networkResponse?.data?.decodeToString()?.let { it1 ->
                    JSONObject(it1).getJSONObject("error").getString("message")
                } ?: "ارتباط با سرور امکان پذیر نیست."

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        error,
                        null,
                        SnackbarDuration.Long
                    )
                }
            }
            loading = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {
                val body = JSONObject()
                val brokenEpcs = JSONArray()

                draftProperties.epcTable.forEach {
                    if (it !in scannedEpcs) {
                        brokenEpcs.put(it)
                    }
                }

                body.put("EPCs", brokenEpcs)
                body.put("StockDraftId", draftProperties.number)

                return body.toString().toByteArray()
            }
        }
        queue.add(request)
    }

    private fun confirmCheckIns() {

        loading = true

        val url =
            "https://rfid-api.avakatan.ir/stock-draft/${draftProperties.number}/confirm-via-erp"
        val request = object : JsonObjectRequest(Method.POST, url, null, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    it.getString("Message"),
                    null,
                    SnackbarDuration.Long
                )
            }
            clear()
            scanningMode = false
            saveToMemory()
            brokenEpcs()

        }, {
            if (it is NoConnectionError) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {

                val error = it.networkResponse?.data?.decodeToString()?.let { it1 ->
                    JSONObject(it1).getJSONObject("error").getString("message")
                } ?: "ارتباط با سرور امکان پذیر نیست."

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        error,
                        null,
                        SnackbarDuration.Long
                    )
                }
            }
            loading = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {
                val body = JSONObject()
                val products = JSONArray()

                productConflicts.forEach {
                    repeat(it.scannedBarcodeNumber + it.scannedEPCNumber) { _ ->
                        val productJson = JSONObject()
                        productJson.put("BarcodeMain_ID", it.primaryKey)
                        productJson.put("kbarcode", it.KBarCode)
                        productJson.put("K_Name", it.kName)
                        products.put(productJson)
                    }
                }
                body.put("kbarcodes", products)

                return body.toString().toByteArray()
            }
        }
        queue.add(request)
    }

    override fun getBarcode(barcode: String?) {

        if (scanningMode) {

            if (!barcode.isNullOrEmpty()) {

                scannedBarcodes.add(barcode)
                numberOfScanned = scannedEpcs.size + scannedBarcodes.size
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                saveToMemory()
                syncScannedItemsToServer()
            }
        } else {
            if (!barcode.isNullOrBlank()) {
                getWarehouseDetails(barcode)
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            }
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("CheckInEPCTable", JSONArray(scannedEpcs).toString())
        edit.putString("CheckInBarcodeTable", JSONArray(scannedBarcodes).toString())
        edit.putBoolean("CheckInScanningMode", scanningMode)
        edit.putString("CheckInDraftProperties", Gson().toJson(draftProperties))
        edit.putString("CheckInEPCTable", JSONArray(scannedEpcs).toString())
        edit.putString("inputProductsForTest", Gson().toJson(inputProducts.values).toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scanningMode = memory.getBoolean("CheckInScanningMode", false)

        if (scanningMode) {
            draftProperties = Gson().fromJson(
                memory.getString("CheckInDraftProperties", ""),
                draftProperties.javaClass
            )
            scannedEpcs = Gson().fromJson(
                memory.getString("CheckInEPCTable", ""),
                scannedEpcs.javaClass
            ) ?: mutableListOf()

            scannedBarcodes = Gson().fromJson(
                memory.getString("CheckInBarcodeTable", ""),
                scannedBarcodes.javaClass
            ) ?: mutableListOf()
        }

        numberOfScanned = scannedEpcs.size + scannedBarcodes.size
    }

    private fun clear() {

        scannedBarcodes.clear()
        scannedEpcs.clear()
        numberOfScanned = 0
        scannedProducts.clear()
        scannedBarcodeMapWithProperties.clear()
        scannedEpcMapWithProperties.clear()
        calculateConflicts()
        filterResult(productConflicts)
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
        queue.stop()
        beep.release()
        finish()
    }

    private fun openSearchActivity(product: Product) {

        val searchResultProduct = Product(
            name = product.name + " از حواله شماره " + draftProperties.number,
            KBarCode = product.KBarCode,
            imageUrl = product.imageUrl,
            color = product.color,
            size = product.size,
            productCode = product.productCode,
            rfidKey = product.rfidKey,
            primaryKey = product.primaryKey,
            originalPrice = product.originalPrice,
            salePrice = product.salePrice,
            storeNumber = product.storeNumber,
            wareHouseNumber = product.wareHouseNumber,
        )

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(searchResultProduct).toString())
        startActivity(intent)
    }

    private fun getWarehouseDetails(code: String) {

        loading = true

        if (code.isEmpty()) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "لطفا شماره حواله را وارد کنید",
                    null,
                    SnackbarDuration.Long
                )
            }
            loading = false
            return
        }

        val url = "https://rfid-api.avakatan.ir/stock-draft/$code/details"
        val request = object : JsonArrayRequest(url, fun(it) {

            val draftBarcodes = mutableListOf<String>()
            val draftEpcs = mutableListOf<String>()
            var numberOfItems = 0
            for (i in 0 until it.length()) {
                numberOfItems += it.getJSONObject(i).getInt("Qty")

                repeat(it.getJSONObject(i).getInt("Qty")) { _ ->
                    draftBarcodes.add(it.getJSONObject(i).getString("kbarcode"))
                }
                draftEpcs.add(it.getJSONObject(i).getString("EPC"))
            }

            draftProperties = DraftProperties(
                number = code.toLong(),
                numberOfItems = numberOfItems,
                barcodeTable = draftBarcodes,
                epcTable = draftEpcs
            )

            saveToMemory()
            clear()
            scanningMode = true
            saveToMemory()
            syncInputItemsToServer()

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

                    val error =
                        JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")

                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            error.getString("message"),
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
            }
            loading = false
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


    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @OptIn(ExperimentalCoilApi::class)
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { if (scanningMode) Content() else Content2() },
                    snackbarHost = { ErrorSnackBar(state) },
                    bottomBar = { if (scanningMode) BottomBar() },
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
                    onClick = {
                        if (!loading && !loading && !rfidScan) {
                            openClearDialog = true
                        }
                    }) {
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
                    Button(onClick = { openFinishDialog = true }) {
                        Text(text = "تایید نهایی")
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

            if (openFinishDialog) {
                FinishAlertDialog()
            }

            if (scanning || loading) {
                LoadingCircularProgressIndicator(scanning, loading)
            } else {

                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .border(
                            BorderStroke(1.dp, borderColor),
                            shape = MaterialTheme.shapes.small
                        )
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
                            text = "اسکن: $numberOfScanned",
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
                }

                LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {

                    items(uiList.size) { i ->
                        Item(
                            i,
                            uiList,
                            true,
                            text3 = "موجودی: " + uiList[i].draftNumber,
                            text4 = uiList[i].conflictType + ":" + " " + uiList[i].conflictNumber
                        ) {
                            openSearchActivity(uiList[i])
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Content2() {

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
                    hint = "لطفا شماره حواله را وارد یا اسکن کنید",
                    onSearch = { getWarehouseDetails(stockDraftNumber) },
                    onValueChange = { stockDraftNumber = it },
                    value = stockDraftNumber,
                    isError = (stockDraftNumber.toLongOrNull() == null && stockDraftNumber != ""),
                    keyboardType = KeyboardType.Number
                )
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
                        filterResult(productConflicts)
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

    @Composable
    fun FinishAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openFinishDialog = false
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
                        text = "کالاهای اسکن شده ثبت شوند؟",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 22.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openFinishDialog = false
                            confirmCheckIns()

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = {
                                openFinishDialog = false
                                clear()
                                scanningMode = false
                                saveToMemory()
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "خیر، نتایج پاک شوند")
                        }
                    }
                }
            }
        )
    }
}
