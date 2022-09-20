package com.jeanwest.reader.centralWarehouseCheckIn

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.checkIn.DraftProperties
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.sharedClassesAndFiles.theme.JeanswestBottomBar
import com.jeanwest.reader.sharedClassesAndFiles.theme.MyApplicationTheme
import com.jeanwest.reader.sharedClassesAndFiles.theme.borderColor
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import com.jeanwest.reader.sharedClassesAndFiles.*
import com.jeanwest.reader.sharedClassesAndFiles.hardware.Barcode2D
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
class CentralWarehouseCheckInActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var epcTable = mutableListOf<String>()
    private var barcodeTable = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    val inputProducts = mutableMapOf<String, Product>()
    private var scannedProducts = mutableMapOf<String, Product>()
    private var scanningJob: Job? = null
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var inputBarcodeMapWithProperties = mutableMapOf<String, Product>()
    private var scannedEpcMapWithProperties = mutableMapOf<String, Product>()
    private var scannedBarcodeMapWithProperties = mutableMapOf<String, Product>()
    private var scannedProductsBiggerThan1000 = false
    private var inputProductsBiggerThan1000 = false
    private var draftProperties = DraftProperties(number = 0L, numberOfItems = 0)

    //ui parameters
    var conflictResultProducts = mutableStateListOf<Product>()
    private var isScanning by mutableStateOf(false)
    private var syncScannedProductsRunning by mutableStateOf(false)
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
    private var syncInputProductsRunning by mutableStateOf(false)
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

        var epcTablePreviousSize = epcTable.size

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

    private fun getConflicts() {

        val result = ArrayList<Product>()

        val samePrimaryKeys = ArrayList<Long>()

        scannedProducts.forEach { scannedProduct ->

            inputProducts.forEach { fileProduct ->

                if (scannedProduct.value.primaryKey == fileProduct.value.primaryKey) {

                    val resultData = Product(
                        name = fileProduct.value.name,
                        KBarCode = fileProduct.value.KBarCode,
                        imageUrl = fileProduct.value.imageUrl,
                        storeNumber = fileProduct.value.storeNumber,
                        wareHouseNumber = fileProduct.value.wareHouseNumber,
                        matchedNumber = abs(scannedProduct.value.scannedNumber - fileProduct.value.desiredNumber),
                        scannedEPCNumber = scannedProduct.value.scannedNumber,
                        desiredNumber = fileProduct.value.desiredNumber,
                        scan = when {
                            scannedProduct.value.scannedNumber > fileProduct.value.desiredNumber -> {
                                "اضافی"
                            }
                            scannedProduct.value.scannedNumber < fileProduct.value.desiredNumber -> {
                                "کسری"
                            }
                            else -> {
                                "تایید شده"
                            }
                        },
                        productCode = fileProduct.value.productCode,
                        size = fileProduct.value.size,
                        color = fileProduct.value.color,
                        originalPrice = fileProduct.value.originalPrice,
                        salePrice = fileProduct.value.salePrice,
                        rfidKey = fileProduct.value.rfidKey,
                        primaryKey = fileProduct.value.primaryKey,
                        scannedEPCs = scannedProduct.value.scannedEPCs
                    )
                    result.add(resultData)
                    samePrimaryKeys.add(fileProduct.value.primaryKey)
                }
            }
            if (scannedProduct.value.primaryKey !in samePrimaryKeys) {
                val resultData = Product(
                    name = scannedProduct.value.name,
                    KBarCode = scannedProduct.value.KBarCode,
                    imageUrl = scannedProduct.value.imageUrl,
                    storeNumber = scannedProduct.value.storeNumber,
                    wareHouseNumber = scannedProduct.value.wareHouseNumber,
                    matchedNumber = scannedProduct.value.scannedNumber,
                    scannedEPCNumber = scannedProduct.value.scannedNumber,
                    scan = "اضافی",
                    productCode = scannedProduct.value.productCode,
                    size = scannedProduct.value.size,
                    color = scannedProduct.value.color,
                    originalPrice = scannedProduct.value.originalPrice,
                    salePrice = scannedProduct.value.salePrice,
                    rfidKey = scannedProduct.value.rfidKey,
                    primaryKey = scannedProduct.value.primaryKey,
                    desiredNumber = 0,
                    scannedEPCs = scannedProduct.value.scannedEPCs
                )
                result.add(resultData)
            }
        }

        inputProducts.forEach { fileProduct ->
            if (fileProduct.value.primaryKey !in samePrimaryKeys) {
                val resultData = Product(
                    name = fileProduct.value.name,
                    KBarCode = fileProduct.value.KBarCode,
                    imageUrl = fileProduct.value.imageUrl,
                    storeNumber = fileProduct.value.storeNumber,
                    wareHouseNumber = fileProduct.value.wareHouseNumber,
                    matchedNumber = fileProduct.value.desiredNumber,
                    scannedEPCNumber = 0,
                    scan = "کسری",
                    productCode = fileProduct.value.productCode,
                    size = fileProduct.value.size,
                    color = fileProduct.value.color,
                    originalPrice = fileProduct.value.originalPrice,
                    salePrice = fileProduct.value.salePrice,
                    rfidKey = fileProduct.value.rfidKey,
                    primaryKey = fileProduct.value.primaryKey,
                    desiredNumber = fileProduct.value.desiredNumber,
                )
                result.add(resultData)
            }
        }
        conflictResultProducts.clear()
        conflictResultProducts.addAll(result)
    }

    private fun filterResult(conflictResult: MutableList<Product>) {

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

        val barcodeTableForV4 = mutableListOf<String>()

        syncInputProductsRunning = true
        inputProductsBiggerThan1000 = false

        run breakForEach@{
            draftProperties.barcodeTable.forEach {
                if (it !in inputBarcodeMapWithProperties.keys && it !in barcodeTableForV4) {
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
            syncInputProductsRunning = false
            return
        }

        syncInputProductsRunning = true

        getProductsV4(queue, state, mutableListOf(), barcodeTableForV4, { _, barcodes ->

            barcodes.forEach { product ->
                inputBarcodeMapWithProperties[product.scannedBarcode] = product
            }

            makeInputProductMap()
            getConflicts()
            filterResult(conflictResultProducts)

            if (inputProductsBiggerThan1000) {
                syncInputItemsToServer()
            } else {
                syncInputProductsRunning = false
                syncScannedItemsToServer()
            }

        }, {
            syncScannedItemsToServer()
            syncInputProductsRunning = false
        })
    }


    private fun makeInputProductMap() {

        inputBarcodeMapWithProperties.forEach { it1 ->

            if (it1.value.KBarCode !in inputProducts.keys) {
                inputProducts[it1.value.KBarCode] = it1.value.copy()
                inputProducts[it1.value.KBarCode]!!.desiredNumber =
                    draftProperties.barcodeTable.count {
                        it == it1.key
                    }
            }
        }

        Log.e("error", draftProperties.barcodeTable.toString())
        Log.e("error", inputBarcodeMapWithProperties.keys.toString())

        /*val rfidLogList = mutableMapOf<Long, Int>()
        val barcodeLogList = mutableMapOf<String, Int>()

        inputProducts.forEach {
            if ((it.value.brandName == "JeansWest" || it.value.brandName == "JootiJeans" || it.value.brandName == "Baleno")
                && !it.value.name.contains("جوراب")
                && !it.value.name.contains("عينك")
                && !it.value.name.contains("شاپينگ")
            ) {
                rfidLogList[it.value.rfidKey] = it.value.desiredNumber
            } else {
                barcodeLogList[it.value.scannedBarcode] = it.value.desiredNumber
            }
        }

        Log.e("rfidLogList", rfidLogList.toString())
        Log.e("barcodeLogList", barcodeLogList.toString())*/
    }

    private fun syncScannedItemsToServer() {

        if (numberOfScanned == 0) {
            return
        }

        syncScannedProductsRunning = true

        val epcArray = mutableListOf<String>()
        val barcodeArray = mutableListOf<String>()
        scannedProductsBiggerThan1000 = false

        run breakForEach@{
            epcTable.forEach {
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
            barcodeTable.forEach {

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
            getConflicts()
            filterResult(conflictResultProducts)
            syncScannedProductsRunning = false
            return
        }

        syncScannedProductsRunning = true

        getProductsV4(queue, state, epcArray, barcodeArray, { epcs, barcodes ->

            epcs.forEach {
                scannedEpcMapWithProperties[it.scannedEPCs[0]] = it
            }

            barcodes.forEach {
                scannedBarcodeMapWithProperties[it.scannedBarcode] = it
            }

            makeScannedProductMap()
            getConflicts()
            filterResult(conflictResultProducts)

            if (scannedProductsBiggerThan1000) {
                syncScannedItemsToServer()
            } else {
                syncScannedProductsRunning = false
            }

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
            getConflicts()
            filterResult(conflictResultProducts)

            syncScannedProductsRunning = false

        })
    }

    private fun makeScannedProductMap() {

        scannedProducts.clear()

        scannedEpcMapWithProperties.forEach { it1 ->

            if (it1.value.KBarCode in scannedProducts.keys) {
                scannedProducts[it1.value.KBarCode]!!.scannedEPCNumber += 1
                scannedProducts[it1.value.KBarCode]?.scannedEPCs?.add(it1.key)
            } else {
                scannedProducts[it1.value.KBarCode] = it1.value.copy()
            }
        }

        val shouldBeCleanedFromScannedBarcodes = mutableListOf<String>()

        scannedBarcodeMapWithProperties.forEach { it1 ->

            if ((it1.value.brandName == "JeansWest" || it1.value.brandName == "JootiJeans" || it1.value.brandName == "Baleno")
                && !it1.value.name.contains("جوراب")
                && !it1.value.name.contains("عينك")
                && !it1.value.name.contains("شاپينگ")
            ) {
                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "این کالا باید با RFID اسکن شود",
                        null,
                        SnackbarDuration.Long
                    )
                }
                shouldBeCleanedFromScannedBarcodes.add(it1.key)
            } else {
                if (it1.value.KBarCode !in scannedProducts.keys) {

                    scannedProducts[it1.value.KBarCode] = it1.value.copy()
                    scannedProducts[it1.value.KBarCode]!!.scannedBarcodeNumber =
                        barcodeTable.count { innerIt2 ->
                            innerIt2 == it1.key
                        }

                } else {
                    scannedProducts[it1.value.KBarCode]!!.scannedBarcodeNumber =
                        barcodeTable.count { innerIt2 ->
                            innerIt2 == it1.key
                        }
                    scannedProducts[it1.value.KBarCode]?.scannedBarcode = it1.key
                }
            }
        }

        shouldBeCleanedFromScannedBarcodes.forEach {
            scannedBarcodeMapWithProperties.remove(it)
            barcodeTable.remove(it)
        }
        numberOfScanned = barcodeTable.size + epcTable.size
    }

    override fun getBarcode(barcode: String?) {

        if (scanningMode) {

            if (!barcode.isNullOrEmpty()) {

                barcodeTable.add(barcode)
                numberOfScanned = epcTable.size + barcodeTable.size
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

        edit.putString("CentralWarehouseCheckInEPCTable", JSONArray(epcTable).toString())
        edit.putString("CentralWarehouseCheckInBarcodeTable", JSONArray(barcodeTable).toString())
        edit.putBoolean("scanningMode", scanningMode)
        edit.putString("draftProperties", Gson().toJson(draftProperties))
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scanningMode = memory.getBoolean("scanningMode", false)

        if (scanningMode) {
            draftProperties = Gson().fromJson(
                memory.getString("draftProperties", ""),
                draftProperties.javaClass
            )
            epcTable = Gson().fromJson(
                memory.getString("CentralWarehouseCheckInEPCTable", ""),
                epcTable.javaClass
            ) ?: mutableListOf()

            barcodeTable = Gson().fromJson(
                memory.getString("CentralWarehouseCheckInBarcodeTable", ""),
                barcodeTable.javaClass
            ) ?: mutableListOf()
        }

        numberOfScanned = epcTable.size + barcodeTable.size
    }

    private fun clear() {

        barcodeTable.clear()
        epcTable.clear()
        numberOfScanned = 0
        scannedProducts.clear()
        scannedBarcodeMapWithProperties.clear()
        scannedEpcMapWithProperties.clear()
        getConflicts()
        filterResult(conflictResultProducts)
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

    private fun editCheckIns() {

        if (uiList.size != 0) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "لطفا ابتدا مغایرت ها را برطرف نمایید.",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        val url = "https://rfid-api.avakatan.ir/stock-draft/${draftProperties.number}"

        val request = object : StringRequest(Method.PATCH, url, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "حواله " + draftProperties.number + "با موفقیت تایید شد.",
                    null,
                    SnackbarDuration.Long
                )
            }
            clear()
            scanningMode = false
            saveToMemory()
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

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        it.toString(),
                        null,
                        SnackbarDuration.Long
                    )
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
                val body = JSONArray()

                conflictResultProducts.forEach {

                    val item = JSONObject()
                    item.put("BarcodeMain_ID", it.primaryKey)
                    item.put("epcs", JSONArray(it.scannedEPCs))
                    body.put(item)
                }

                Log.e("error", body.toString())

                return body.toString().toByteArray()
            }
        }
        queue.add(request)

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

        val url = "https://rfid-api.avakatan.ir/stock-draft/$code/details"
        val request = object : JsonArrayRequest(url, fun(it) {

            val draftBarcodes = mutableListOf<String>()
            var numberOfItems = 0
            for (i in 0 until it.length()) {
                numberOfItems += it.getJSONObject(i).getInt("Qty")
                repeat(it.getJSONObject(i).getInt("Qty")) { _ ->
                    draftBarcodes.add(it.getJSONObject(i).getString("kbarcode"))
                }
            }

            draftProperties = DraftProperties(
                number = code.toLong(),
                numberOfItems = numberOfItems,
                barcodeTable = draftBarcodes,
            )

            saveToMemory()
            stopBarcodeScan()
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
                    onClick = { openClearDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.centralCheckInCheckInText),
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
                        Text(text = "پایان تروفالس")
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

                PowerSlider(scanTypeValue == "RFID", rfPower) { rfPower = it }
                LoadingCircularProgressIndicator(
                    isScanning,
                    syncScannedProductsRunning || syncInputProductsRunning
                )
            }

            LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {

                items(uiList.size) { i ->
                    Item(
                        i,
                        uiList,
                        true,
                        text1 = "موجودی: " + uiList[i].desiredNumber,
                        text2 = uiList[i].scan + ":" + " " + uiList[i].matchedNumber
                    ) {
                        openSearchActivity(uiList[i])
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
                    value = stockDraftNumber
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
                        filterResult(conflictResultProducts)
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
                            editCheckIns()

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = {
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