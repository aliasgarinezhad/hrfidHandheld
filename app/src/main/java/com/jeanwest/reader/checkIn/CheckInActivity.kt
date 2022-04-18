package com.jeanwest.reader.checkIn

import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil.compose.rememberImagePainter
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.testClasses.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.search.SearchResultProducts
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.hardware.setRFEpcMode
import com.jeanwest.reader.hardware.setRFPower
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

@ExperimentalCoilApi
class CheckInActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var epcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var barcodeTable = mutableListOf<String>()
    private var excelBarcodes = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    private val inputProducts = ArrayList<CheckInInputProduct>()
    private val scannedProducts = ArrayList<CheckInScannedProduct>()
    private val invalidEpcs = ArrayList<String>()
    private var scanningJob: Job? = null

    //ui parameters
    private var conflictResultProducts by mutableStateOf(mutableListOf<CheckInConflictResultProduct>())
    private var isScanning by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var shortageCodesNumber by mutableStateOf(0)
    private var additionalCodesNumber by mutableStateOf(0)
    private var numberOfScanned by mutableStateOf(0)
    private var uiList by mutableStateOf(mutableListOf<CheckInConflictResultProduct>())
    private val scanValues =
        arrayListOf("همه اجناس", "تایید شده", "اضافی", "کسری")
    private var scanFilter by mutableStateOf(0)
    private var openClearDialog by mutableStateOf(false)
    private val scanTypeValues = mutableListOf("RFID", "بارکد")
    var scanTypeValue by mutableStateOf("RFID")
    private var state = SnackbarHostState()

    private val apiTimeout = 30000

    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
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
        invalidEPCs: MutableList<String>
    ): MutableList<CheckInConflictResultProduct> {

        val result = ArrayList<CheckInConflictResultProduct>()

        val samePrimaryKeys = ArrayList<Long>()

        scannedProducts.forEach { scannedProduct ->

            fileProducts.forEach { fileProduct ->

                if (scannedProduct.primaryKey == fileProduct.primaryKey) {

                    val resultData = CheckInConflictResultProduct(
                        name = fileProduct.name,
                        KBarCode = fileProduct.KBarCode,
                        imageUrl = fileProduct.imageUrl,
                        matchedNumber = abs(scannedProduct.scannedNumber - fileProduct.number),
                        scannedNumber = scannedProduct.scannedNumber,
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
                val resultData = CheckInConflictResultProduct(
                    name = scannedProduct.name,
                    KBarCode = scannedProduct.KBarCode,
                    imageUrl = scannedProduct.imageUrl,
                    matchedNumber = scannedProduct.scannedNumber,
                    scannedNumber = scannedProduct.scannedNumber,
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
                val resultData = CheckInConflictResultProduct(
                    name = fileProduct.name,
                    KBarCode = fileProduct.KBarCode,
                    imageUrl = fileProduct.imageUrl,
                    matchedNumber = fileProduct.number,
                    scannedNumber = 0,
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

        invalidEPCs.forEach {
            val resultData = CheckInConflictResultProduct(
                name = it,
                KBarCode = "",
                imageUrl = "",
                matchedNumber = 0,
                scannedNumber = 1,
                result = "خراب: ",
                scan = "خراب",
                productCode = "",
                size = "",
                color = "",
                originalPrice = "",
                salePrice = "",
                rfidKey = 0L,
                primaryKey = 0L,
                fileNumber = 0,
            )
            result.add(resultData)
        }

        return result
    }

    private fun filterResult(conflictResult: MutableList<CheckInConflictResultProduct>): MutableList<CheckInConflictResultProduct> {

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
                    } as ArrayList<CheckInConflictResultProduct>
                }
                scanValues[scanFilter] == "کسری" -> {
                    conflictResult.filter {
                        it.scan == "کسری"
                    } as ArrayList<CheckInConflictResultProduct>
                }
                scanValues[scanFilter] == "تایید شده" -> {
                    conflictResult.filter {
                        it.scan == "تایید شده"
                    } as ArrayList<CheckInConflictResultProduct>
                }
                else -> {
                    conflictResult
                }
            }

        return uiListParameters
    }

    private fun syncInputItemsToServer() {

        val url = "http://rfid-api.avakatan.ir/products/v3"

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

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
            } else {
                conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
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

        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")
            val invalids = it.getJSONArray("invalidEpcs")
            val similarIndexes = arrayListOf<Int>()

            for (i in 0 until barcodes.length()) {

                for (j in 0 until epcs.length()) {

                    if (barcodes.getJSONObject(i)
                            .getString("KBarCode") == epcs.getJSONObject(j)
                            .getString("KBarCode")
                    ) {
                        epcs.getJSONObject(j).put(
                            "handheldCount",
                            epcs.getJSONObject(j)
                                .getInt("handheldCount") + barcodes.getJSONObject(i)
                                .getInt("handheldCount")
                        )
                        similarIndexes.add(i)
                        break
                    }
                }
            }
            for (i in 0 until barcodes.length()) {

                if (i !in similarIndexes) {
                    epcs.put(it.getJSONArray("KBarCodes")[i])
                }
            }

            scannedProducts.clear()

            for (i in 0 until epcs.length()) {

                val scannedProduct = CheckInScannedProduct(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedNumber = epcs.getJSONObject(i).getInt("handheldCount"),
                    productCode = epcs.getJSONObject(i).getString("K_Bar_Code"),
                    size = epcs.getJSONObject(i).getString("Size"),
                    color = epcs.getJSONObject(i).getString("Color"),
                    originalPrice = epcs.getJSONObject(i).getString("OrgPrice"),
                    salePrice = epcs.getJSONObject(i).getString("SalePrice"),
                    rfidKey = epcs.getJSONObject(i).getLong("RFID"),
                )
                scannedProducts.add(scannedProduct)
            }

            invalidEpcs.clear()
            for (i in 0 until invalids.length()) {
                invalidEpcs.add(invalids.getString(i))
            }

            conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)

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
                        }                    }
                }
            }
            conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)

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

                epcTable.forEach {
                    epcArray.put(it)
                }

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                barcodeTable.forEach {
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

        if (numberOfScanned != 0) {
            barcodeTable.clear()
            epcTable.clear()
            invalidEpcs.clear()
            epcTablePreviousSize = 0
            numberOfScanned = 0
            scannedProducts.clear()
            conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)
            saveToMemory()
        } else {
            barcodeTable.clear()
            epcTable.clear()
            invalidEpcs.clear()
            epcTablePreviousSize = 0
            numberOfScanned = 0
            excelBarcodes.clear()
            scannedProducts.clear()
            inputProducts.clear()
            conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)
            saveToMemory()
        }
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

    private fun openSearchActivity(product: CheckInConflictResultProduct) {

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
                IconButton(onClick = { openConfirmCheckInsActivity() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_check_24),
                        contentDescription = ""
                    )
                }
                IconButton(modifier = Modifier.testTag("CheckInTestTag"), onClick = { openClearDialog = true }) {
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
                        .padding(start = 35.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Right,
                )
            }
        )
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        var slideValue by rememberSaveable { mutableStateOf(30F) }

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
                        .padding(top = 8.dp, bottom = 8.dp)
                        .height(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScanTypeDropDownList(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 16.dp, end = 24.dp)
                    )

                    if (scanTypeValue == "RFID") {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "قدرت آنتن (" + slideValue.toInt() + ")",
                                modifier = Modifier
                                    .padding(start = 8.dp)
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = "تعداد اسکن شده: $numberOfScanned",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .align(Alignment.CenterVertically)
                            .weight(1F),
                    )
                    Text(
                        //text = "کسری ها(یکتا): $shortagesNumber($shortageCodesNumber)",
                        text = "کسری: $shortagesNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1F),
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {

                    ScanFilterDropDownList(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 16.dp)
                    )

                    Text(
                        //text = "اضافی ها(یکتا): $additionalNumber($additionalCodesNumber)",
                        text = "اضافی: $additionalNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1F),
                    )
                }

                if (isScanning) {
                    Row(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.primary)
                    }
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 2.dp)) {

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
                    color = MaterialTheme.colors.onPrimary,
                    shape = MaterialTheme.shapes.small
                )

                .fillMaxWidth()
                .height(80.dp)
                .clickable(
                    onClick = {
                        openSearchActivity(uiList[i])
                    },
                )
                .testTag("CheckInActivityLazyColumnItem"),
        ) {

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
                        text = "موجودی: " + uiList[i].fileNumber,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                    Text(
                        text = uiList[i].result,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
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
                        text = if (numberOfScanned == 0) {
                            "فایل پاک شود؟"
                        } else {
                            "کالاهای اسکن شده پاک شوند؟"
                        },
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