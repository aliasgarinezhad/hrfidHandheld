package com.jeanwest.reader.count

//import com.rscja.deviceapi.RFIDWithUHFUART
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
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
import com.jeanwest.reader.hardware.*
import com.jeanwest.reader.search.SearchResultProducts
import com.jeanwest.reader.search.SearchSubActivity
import com.rscja.deviceapi.RFIDWithUHFUART
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException


@ExperimentalFoundationApi
@ExperimentalCoilApi
class CountActivity : ComponentActivity(), IBarcodeResult {

    private var searchModeEpcTable = mutableListOf<String>()
    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var scannedEpcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var scannedBarcodeTable = mutableListOf<String>()
    var excelBarcodes = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    private val fileProducts = mutableListOf<FileProduct>()
    private val scannedProducts = mutableMapOf<String, ScannedProduct>()
    private var scanningJob: Job? = null
    private var barcodeToCategoryMap = mutableMapOf<String, String>()
    private var scannedEpcMapWithProperties = mutableMapOf<String, ScannedProductsOneByOne>()
    private var scannedBarcodeMapWithProperties = mutableMapOf<String, ScannedProductsOneByOne>()
    private var scannedProductsBiggerThan1000 = false

    //ui parameters
    private var conflictResultProducts = mutableStateMapOf<String, ConflictResultProduct>()
    private var syncScannedProductsRunning by mutableStateOf(false)
    private var isScanning by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var number by mutableStateOf(0)
    private var fileName by mutableStateOf("خروجی")
    private var openDialog by mutableStateOf(false)
    private var uiList = mutableStateListOf<ConflictResultProduct>()
    private var categoryFilter by mutableStateOf("همه دسته ها")
    private var categoryValues = mutableStateListOf("همه دسته ها", "نامعلوم")
    private var scanFilter by mutableStateOf("همه اجناس")
    private var searchMode by mutableStateOf(false)
    private var signedFilter by mutableStateOf("همه")
    private var searchModeProductCodes = mutableListOf<String>()
    private var signedProductCodes = mutableListOf<String>()
    private var openClearDialog by mutableStateOf(false)
    private val scanTypeValues = mutableListOf("RFID", "بارکد")
    private var scanTypeValue by mutableStateOf("RFID")
    private var state = SnackbarHostState()

    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        setContent {
            Page()
        }
        loadMemory()

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode(rf, state)

        if (intent.action == Intent.ACTION_SEND) {

            if (getString(R.string.xlsx) == intent.type || getString(R.string.xls) == intent.type) {

                if (!checkLogin(this)) {
                    return
                }
                rfInit(rf, this, state)

                while (!rf.setEPCMode()) {
                    rf.free()
                }

                val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri

                if (intent.type == getString(R.string.xls)) {
                    readXLSFile(uri)
                } else if (intent.type == getString(R.string.xlsx)) {
                    readXLSXFile(uri)
                }

            } else {
                while (!rf.setEPCMode()) {
                    rf.free()
                }

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "فرمت فایل باید اکسل باشد",
                        null,
                        SnackbarDuration.Long
                    )
                }
                syncFileItemsToServer()
            }
        } else {
            while (!rf.setEPCMode()) {
                rf.free()
            }
            syncFileItemsToServer()
        }
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
                        syncScannedItemsToServer()
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

        searchModeEpcTable.clear()
        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        searchModeEpcTable.add(uhfTagInfo.epc)
                        scannedEpcTable.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            scannedEpcTable = scannedEpcTable.distinct().toMutableList()
            searchModeEpcTable = searchModeEpcTable.distinct().toMutableList()

            number = scannedEpcTable.size + scannedBarcodeTable.size

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
        if (searchMode && searchModeProductCodes.size == 0) {
            getSearchModeProductsProperties()
        }
        number = scannedEpcTable.size + scannedBarcodeTable.size
        saveToMemory()
    }

    private fun filterResult(
        conflictResult: MutableMap<String, ConflictResultProduct>,
        signedFilter: String,
        scanFilter: String,
        categoryFilter: String,
    ): SnapshotStateList<ConflictResultProduct> {

        val signedFilterOutput =
            when (signedFilter) {
                "همه" -> {
                    conflictResult.values
                }
                "نشانه دار" -> {
                    conflictResult.values.filter {
                        it.KBarCode in signedProductCodes
                    } as MutableList<ConflictResultProduct>
                }
                "بی نشانه" -> {
                    conflictResult.values.filter {
                        it.KBarCode !in signedProductCodes
                    } as MutableList<ConflictResultProduct>
                }
                else -> {
                    conflictResult.values
                }
            }

        val zoneFilterOutput =
            if (searchMode) {
                signedFilterOutput.filter {
                    it.productCode in searchModeProductCodes
                } as MutableList<ConflictResultProduct>
            } else {
                signedFilterOutput
            }

        val categoryFilterOutput =
            if (categoryFilter == "همه دسته ها") {
                zoneFilterOutput
            } else {
                zoneFilterOutput.filter {
                    it.category == categoryFilter
                } as MutableList<ConflictResultProduct>
            }

        shortagesNumber = 0
        categoryFilterOutput.filter {
            it.scan == "کسری"
        }.forEach {
            shortagesNumber += it.matchedNumber
        }

        additionalNumber = 0
        categoryFilterOutput.filter {
            it.scan == "اضافی" || it.scan == "اضافی فایل"
        }.forEach {
            additionalNumber += it.matchedNumber
        }

        val uiListParameters =
            when (scanFilter) {
                "همه اجناس" -> {
                    categoryFilterOutput
                }
                "اضافی" -> {
                    categoryFilterOutput.filter {
                        it.scan == "اضافی" || it.scan == "اضافی فایل"
                    } as MutableList<ConflictResultProduct>
                }
                else -> {
                    categoryFilterOutput.filter {
                        it.scan == scanFilter
                    } as MutableList<ConflictResultProduct>
                }
            }

        return uiListParameters.toMutableStateList()
    }

    private fun syncFileItemsToServer() {

        if (excelBarcodes.isEmpty()) {
            syncScannedItemsToServer()
            return
        }

        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val fileJsonArray = it.getJSONArray("KBarCodes")
            fileProducts.clear()

            for (i in 0 until fileJsonArray.length()) {

                val fileProduct = FileProduct(
                    name = fileJsonArray.getJSONObject(i).getString("productName"),
                    KBarCode = fileJsonArray.getJSONObject(i).getString("KBarCode"),
                    imageUrl = fileJsonArray.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = fileJsonArray.getJSONObject(i).getLong("BarcodeMain_ID"),
                    number = fileJsonArray.getJSONObject(i).getInt("handheldCount"),
                    category = barcodeToCategoryMap[fileJsonArray.getJSONObject(i)
                        .getString("KBarCode")] ?: "نامعلوم",
                    productCode = fileJsonArray.getJSONObject(i).getString("K_Bar_Code"),
                    size = fileJsonArray.getJSONObject(i).getString("Size"),
                    color = fileJsonArray.getJSONObject(i).getString("Color"),
                    originalPrice = fileJsonArray.getJSONObject(i).getString("OrgPrice"),
                    salePrice = fileJsonArray.getJSONObject(i).getString("SalePrice"),
                    rfidKey = fileJsonArray.getJSONObject(i).getLong("RFID"),
                )
                fileProducts.add(fileProduct)
            }

            categoryValues.clear()
            categoryValues.add("همه دسته ها")
            categoryValues.add("نامعلوم")
            barcodeToCategoryMap.values.forEach { category ->
                if (category !in categoryValues) {
                    categoryValues.add(category)
                }
            }
            conflictResultProducts = getConflicts(fileProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)
            syncScannedItemsToServer()

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
            syncScannedItemsToServer()

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
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        val queue = Volley.newRequestQueue(this@CountActivity)
        queue.add(request)
    }

    private fun syncScannedItemsToServer() {

        if (number == 0) {
            return
        }

        syncScannedProductsRunning = true

        val epcArray = JSONArray()
        val barcodeArray = JSONArray()
        scannedProductsBiggerThan1000 = false

        run breakForEach@{
            scannedEpcTable.forEach {
                if (it !in scannedEpcMapWithProperties.keys) {
                    if (epcArray.length() < 1000) {
                        epcArray.put(it)
                    } else {
                        scannedProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }
        run breakForEach@{
            scannedBarcodeTable.forEach {
                if (it !in scannedBarcodeMapWithProperties.keys) {
                    if (barcodeArray.length() < 1000) {
                        barcodeArray.put(it)
                    } else {
                        scannedProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }

        if (epcArray.length() == 0 && barcodeArray.length() == 0) {

            makeScannedProductMap()
            conflictResultProducts = getConflicts(fileProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)

            syncScannedProductsRunning = false
            return
        }

        val url = "http://rfid-api.avakatan.ir/products/v4"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")

            for (i in 0 until epcs.length()) {

                val scannedEpcWithProperties = ScannedProductsOneByOne(
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
                    warehouseNumber = epcs.getJSONObject(i).getInt("depoCount"),
                    storeNumber = epcs.getJSONObject(i).getInt("storeCount"),
                )
                scannedEpcMapWithProperties[epcs.getJSONObject(i).getString("epc")] =
                    scannedEpcWithProperties
            }

            for (i in 0 until barcodes.length()) {

                val scannedBarcodeWithProperties = ScannedProductsOneByOne(
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
                    warehouseNumber = barcodes.getJSONObject(i).getInt("depoCount"),
                    storeNumber = barcodes.getJSONObject(i).getInt("storeCount"),
                )

                scannedBarcodeMapWithProperties[barcodes.getJSONObject(i).getString("kbarcode")] =
                    scannedBarcodeWithProperties
            }

            makeScannedProductMap()
            conflictResultProducts = getConflicts(fileProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)

            if (scannedProductsBiggerThan1000) {
                syncScannedItemsToServer()
            } else {
                syncScannedProductsRunning = false
            }

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
            conflictResultProducts = getConflicts(fileProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)
            syncScannedProductsRunning = false
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

        val queue = Volley.newRequestQueue(this@CountActivity)
        queue.add(request)
    }

    private fun makeScannedProductMap() {

        scannedProducts.clear()

        scannedEpcMapWithProperties.forEach { it1 ->

            if (it1.value.KBarCode in scannedProducts.keys) {
                scannedProducts[it1.value.KBarCode]!!.scannedNumber += 1
            } else {
                scannedProducts[it1.value.KBarCode] = ScannedProduct(
                    name = it1.value.name,
                    KBarCode = it1.value.KBarCode,
                    imageUrl = it1.value.imageUrl,
                    primaryKey = it1.value.primaryKey,
                    productCode = it1.value.productCode,
                    size = it1.value.size,
                    color = it1.value.color,
                    originalPrice = it1.value.originalPrice,
                    salePrice = it1.value.salePrice,
                    rfidKey = it1.value.rfidKey,
                    scannedNumber = 1
                )
            }
        }

        scannedBarcodeMapWithProperties.forEach { it1 ->

            if (it1.value.KBarCode in scannedProducts.keys) {
                scannedProducts[it1.value.KBarCode]!!.scannedNumber += scannedBarcodeTable.count { innerIt2 ->
                    innerIt2 == it1.key
                }
            } else {
                scannedProducts[it1.value.KBarCode] = ScannedProduct(
                    name = it1.value.name,
                    KBarCode = it1.value.KBarCode,
                    imageUrl = it1.value.imageUrl,
                    primaryKey = it1.value.primaryKey,
                    productCode = it1.value.productCode,
                    size = it1.value.size,
                    color = it1.value.color,
                    originalPrice = it1.value.originalPrice,
                    salePrice = it1.value.salePrice,
                    rfidKey = it1.value.rfidKey,
                    scannedNumber = scannedBarcodeTable.count { innerIt2 ->
                        innerIt2 == it1.key
                    }
                )
            }
        }
    }

    private fun getSearchModeProductsProperties() {
        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")

            searchModeProductCodes.clear()
            for (i in 0 until epcs.length()) {
                searchModeProductCodes.add(epcs.getJSONObject(i).getString("K_Bar_Code"))
            }
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "بارکد های جست و جو مشخص شدند. حال می توانید جنس های مشابه این بارکد ها را جست و جو کنید. برای دیدن همه اجناس، جست و جو را غیر فعال کنید.",
                    null,
                    SnackbarDuration.Long
                )
            }

        }, {
            if (searchModeEpcTable.size == 0) {

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

                searchModeEpcTable.forEach {
                    epcArray.put(it)
                }

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                json.put("KBarCodes", barcodeArray)

                return json.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        val queue = Volley.newRequestQueue(this@CountActivity)
        queue.add(request)
    }

    private fun readXLSXFile(uri: Uri) {

        val workbook: XSSFWorkbook
        try {
            workbook = XSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل نامعتبر است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل خالی است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        barcodeToCategoryMap.clear()
        excelBarcodes.clear()

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {

                barcodeToCategoryMap[sheet.getRow(i).getCell(0).stringCellValue] =
                    sheet.getRow(i).getCell(2)?.stringCellValue ?: "نامعلوم"

                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    excelBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        saveToMemory()
        syncFileItemsToServer()
    }

    private fun readXLSFile(uri: Uri) {

        val workbook: HSSFWorkbook
        try {
            workbook = HSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل نامعتبر است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل خالی است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        barcodeToCategoryMap.clear()
        excelBarcodes.clear()

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {

                barcodeToCategoryMap[sheet.getRow(i).getCell(0).stringCellValue] =
                    sheet.getRow(i).getCell(2)?.stringCellValue ?: "نامعلوم"

                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    excelBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        saveToMemory()
        syncFileItemsToServer()
    }

    private fun searchModeButton() {
        if (!searchMode) {
            searchModeProductCodes.clear()

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "ابتدا بارکد هایی که می خواهید جست و جو کنید را با اسکن مشخص کنید",
                    null,
                    SnackbarDuration.Long
                )
            }
            searchMode = true
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)

        } else {
            searchMode = false
            searchModeProductCodes.clear()
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)
        }
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            scannedBarcodeTable.add(barcode)
            number = scannedEpcTable.size + scannedBarcodeTable.size
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("FileAttachmentEPCTable", JSONArray(scannedEpcTable).toString())
        edit.putString("FileAttachmentBarcodeTable", JSONArray(scannedBarcodeTable).toString())
        edit.putString(
            "FileAttachmentFileZoneCodesTable",
            JSONArray(searchModeProductCodes).toString()
        )
        edit.putString(
            "FileAttachmentFileSignedCodesTable",
            JSONArray(signedProductCodes).toString()
        )
        edit.putString("FileAttachmentFileBarcodeTable", JSONArray(excelBarcodes).toString())
        edit.putString(
            "FileAttachmentBarcodeToCategoryMapTable",
            JSONObject(barcodeToCategoryMap as Map<*, *>).toString()
        )
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scannedEpcTable = Gson().fromJson(
            memory.getString("FileAttachmentEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = scannedEpcTable.size

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("FileAttachmentBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        excelBarcodes = Gson().fromJson(
            memory.getString("FileAttachmentFileBarcodeTable", ""),
            excelBarcodes.javaClass
        ) ?: mutableListOf()

        barcodeToCategoryMap = Gson().fromJson(
            memory.getString("FileAttachmentBarcodeToCategoryMapTable", ""),
            barcodeToCategoryMap.javaClass
        ) ?: mutableMapOf()

        searchModeProductCodes = Gson().fromJson(
            memory.getString("FileAttachmentFileZoneCodesTable", ""),
            searchModeProductCodes.javaClass
        ) ?: mutableListOf()

        signedProductCodes = Gson().fromJson(
            memory.getString("FileAttachmentFileSignedCodesTable", ""),
            signedProductCodes.javaClass
        ) ?: mutableListOf()

        number = scannedEpcTable.size + scannedBarcodeTable.size
    }

    private fun clear() {

        if (number != 0) {
            scannedBarcodeTable.clear()
            scannedEpcTable.clear()
            searchModeEpcTable.clear()
            searchModeProductCodes.clear()
            epcTablePreviousSize = 0
            number = 0
            scannedProducts.clear()
            conflictResultProducts = getConflicts(fileProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)
            openDialog = false
            saveToMemory()
        } else {
            scannedBarcodeTable.clear()
            scannedEpcTable.clear()
            searchModeEpcTable.clear()
            searchModeProductCodes.clear()
            epcTablePreviousSize = 0
            number = 0
            excelBarcodes.clear()
            barcodeToCategoryMap.clear()
            scannedProducts.clear()
            fileProducts.clear()
            conflictResultProducts = getConflicts(fileProducts, scannedProducts)
            uiList = filterResult(conflictResultProducts, signedFilter, scanFilter, categoryFilter)
            openDialog = false
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

    private fun openSearchActivity(product: ConflictResultProduct) {

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
                IconButton(onClick = { openDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_share_24),
                        contentDescription = ""
                    )
                }
                IconButton(modifier = Modifier.testTag("CountActivityClearButton"),
                    onClick = { openClearDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = "شمارش",
                    modifier = Modifier
                        .padding(start = 35.dp)
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

        var slideValue by rememberSaveable { mutableStateOf(30F) }

        Column {

            if (openDialog) {
                FileAlertDialog()
            }

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

                Row(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 8.dp)
                        .height(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    ScanTypeDropDownList(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 16.dp, end = 16.dp)
                    )

                    if (scanTypeValue == "RFID") {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "قدرت آنتن (" + slideValue.toInt() + ")",
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
                }

                if (scanTypeValue == "RFID") {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        Button(
                            onClick = { searchModeButton() }) {
                            Text(
                                text = if (!searchMode) {
                                    "فعال کردن جست و جو"
                                } else {
                                    "خروج از جست و جو"
                                },
                                textAlign = TextAlign.Right,
                            )
                        }
                    }
                }

                if (isScanning || syncScannedProductsRunning) {
                    Row(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
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
                        if (syncScannedProductsRunning) {
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

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                ) {

                    Text(
                        text = "اسکن شده: $number",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1.1F),
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

                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth(),
                ) {

                    Row(
                        modifier = Modifier
                            .weight(1.1F)
                            .padding(start = 16.dp),
                    ) {
                        ScanFilterDropDownList()
                    }
                    Row(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 16.dp),
                    ) {
                        CategoryFilterDropDownList()
                    }
                    Row(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 16.dp),
                    ) {
                        SignedFilterDropDownList()
                    }
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {

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
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .background(
                    color = if (uiList[i].KBarCode !in signedProductCodes) {
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
                        openSearchActivity(uiList[i])
                    },
                    onLongClick = {
                        if (uiList[i].KBarCode !in signedProductCodes) {
                            signedProductCodes.add(uiList[i].KBarCode)
                        } else {
                            signedProductCodes.remove(uiList[i].KBarCode)
                        }
                        uiList = filterResult(
                            conflictResultProducts,
                            signedFilter,
                            scanFilter,
                            categoryFilter
                        )
                    },
                )
                .testTag("CountActivityLazyColumnItem"),
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
                    .fillMaxHeight()
                    .padding(start = 8.dp)
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
                        text = uiList[i].result + ":" + " " + uiList[i].matchedNumber,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                }
            }
        }
    }

    @Composable
    fun ScanFilterDropDownList() {

        val scanValues =
            mutableListOf("همه اجناس", "تایید شده", "اضافی", "کسری", "اضافی فایل", "خراب")

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier
                .clickable { expanded = true }
                .testTag("CountActivityFilterDropDownList")
            ) {
                Text(text = scanFilter)
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
                        scanFilter = it
                        uiList = filterResult(
                            conflictResultProducts,
                            signedFilter,
                            scanFilter,
                            categoryFilter
                        )
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun CategoryFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = categoryFilter)
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                categoryValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        categoryFilter = it
                        uiList = filterResult(
                            conflictResultProducts,
                            signedFilter,
                            scanFilter,
                            categoryFilter
                        )
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun SignedFilterDropDownList() {

        val signedValue = mutableListOf("همه", "نشانه دار", "بی نشانه")
        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = signedFilter)
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                signedValue.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        signedFilter = it
                        uiList = filterResult(
                            conflictResultProducts,
                            signedFilter,
                            scanFilter,
                            categoryFilter
                        )
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun FileAlertDialog() {

        AlertDialog(

            buttons = {

                Column {

                    Text(
                        text = "نام فایل خروجی را وارد کنید", modifier = Modifier
                            .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                    )

                    OutlinedTextField(
                        value = fileName, onValueChange = {
                            fileName = it
                        },
                        modifier = Modifier
                            .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                            .align(Alignment.CenterHorizontally)
                    )

                    Button(modifier = Modifier
                        .padding(bottom = 10.dp, top = 10.dp, start = 10.dp, end = 10.dp)
                        .align(Alignment.CenterHorizontally),
                        onClick = {
                            openDialog = false
                            exportFile(
                                conflictResultProducts,
                                shortagesNumber,
                                additionalNumber,
                                number,
                                signedProductCodes,
                                fileName,
                                this@CountActivity
                            )
                        }) {
                        Text(text = "ذخیره")
                    }
                }
            },

            onDismissRequest = {
                openDialog = false
            }
        )
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
                        text = if (number == 0) {
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
                        if (scanTypeValue == "بارکد") {
                            searchModeProductCodes.clear()
                            searchMode = false
                        }
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }
}