package com.jeanwest.reader.inventory

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.sharedClassesAndFiles.*
import com.jeanwest.reader.sharedClassesAndFiles.theme.*
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs


@ExperimentalFoundationApi
class InventoryActivity : ComponentActivity() {

    private var resultsBiggerThan500 = false
    private var resultIndexForApi2 = 0
    private var saveToServerId = ""
    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var epcTablePreviousSize = 0
    private var inputBarcodesTable = mutableListOf<String>()
    private val inputProducts = mutableMapOf<String, Product>()
    private val scannedProducts = mutableMapOf<String, Product>()
    private var scanningJob: Job? = null
    private var scannedEpcMapWithProperties = mutableMapOf<String, Product>()
    private var scannedProductsBiggerThan1000 = false
    private var inputProductsBiggerThan1000 = false
    private var warehouseCode by mutableStateOf("")
    private var locations = mutableMapOf<String, String>()
    private var inputBarcodeMapWithProperties = mutableMapOf<String, Product>()
    private var currentScannedProductKBarCodes = mutableListOf<String>()
    private var currentScannedEpcTable = mutableListOf<String>()
    private var isInProgress = false

    //ui parameters
    private var openFinishDialog by mutableStateOf(false)
    private var uiList = mutableStateListOf<Product>()
    private var inventoryResult = mutableStateMapOf<String, Product>()
    private var syncScannedProductsRunning by mutableStateOf(false)
    private var syncInputProductsRunning by mutableStateOf(false)
    private var saveToServerInProgress by mutableStateOf(false)
    private var isScanning by mutableStateOf(false)
    private var number by mutableStateOf(0)
    private var fileName by mutableStateOf("خروجی")
    private var openDialog by mutableStateOf(false)
    private var openClearDialog by mutableStateOf(false)
    private var openStartOrContinueDialog by mutableStateOf(false)
    private var state = SnackbarHostState()
    private var inventoryProgress by mutableStateOf(0F)
    private var inventoryStarted by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var numberOfScanned by mutableStateOf(0)
    private var isInShortageAdditionalPage by mutableStateOf(false)
    private lateinit var queue: RequestQueue

    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    companion object {
        var scannedEpcTable = mutableListOf<String>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }
        queue = Volley.newRequestQueue(this)
        loadMemory()

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode(rf, state)

        if (inventoryStarted) {
            getWarehouseBarcodes()
        }
    }

    override fun onResume() {
        super.onResume()
        if(!syncInputProductsRunning) {
            number = scannedEpcTable.size
            syncScannedItemsToServer()
        }
    }

    private fun getWarehouseBarcodes() {

        syncInputProductsRunning = true

        val url = "http://rfid-api.avakatan.ir/products/$warehouseCode"

        val request = object : JsonObjectRequest(url, {

            val products = it.getJSONArray("products")
            val mojodiReviewPackage = it.getJSONObject("mojodiReviewPackage")

            isInProgress = mojodiReviewPackage.getBoolean("IsInProgress")

            inputBarcodesTable.clear()
            inputBarcodeMapWithProperties.clear()
            inputProducts.clear()
            for (i in 0 until products.length()) {
                inputBarcodesTable.add(products.getJSONObject(i).getString("KBarCode"))
            }
            syncInputProductsRunning = false
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

                    val error = JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")

                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            error.getString("message"),
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
            }
            syncInputProductsRunning = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val header = mutableMapOf<String, String>()
                header["Content-Type"] = "application/json;charset=UTF-8"
                header["Authorization"] = "Bearer " + MainActivity.token
                return header
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun getConflicts() {

        Log.e("error", "getConflicts")

        syncScannedProductsRunning = true

        val isInDepo = locations[warehouseCode]?.contains("دپو") ?: false

        val result = mutableStateMapOf<String, Product>()

        inputProducts.forEach {

            val diff = if (isInDepo) it.value.rfidWareHouseNumber else it.value.rfidStoreNumber

            if ((it.value.brandName == "JeansWest" || it.value.brandName == "JootiJeans" || it.value.brandName == "Baleno") && !it.value.name.contains(
                    "جوراب"
                )
            ) {

                if (it.value.KBarCode in scannedProducts) {

                    val resultData = Product(
                        name = it.value.name,
                        KBarCode = it.value.KBarCode,
                        storeNumber = it.value.storeNumber,
                        wareHouseNumber = it.value.wareHouseNumber,
                        imageUrl = it.value.imageUrl,
                        matchedNumber = when {
                            diff == 0 -> {
                                diff
                            }
                            diff < 0 -> {
                                abs(scannedProducts[it.value.KBarCode]!!.scannedNumber - it.value.desiredNumber)
                            }
                            else -> {
                                if(scannedProducts[it.value.KBarCode]!!.scannedNumber == it.value.desiredNumber) {
                                    0
                                } else {
                                    diff
                                }
                            }
                        },
                        scannedEPCNumber = scannedProducts[it.value.KBarCode]!!.scannedNumber,
                        desiredNumber = it.value.desiredNumber,
                        result = when {

                            diff == 0 -> {
                                "تایید شده"
                            }
                            diff < 0 -> {
                                if(scannedProducts[it.value.KBarCode]!!.scannedNumber > it.value.desiredNumber) {
                                    "اضافی"
                                }
                                else if (scannedProducts[it.value.KBarCode]!!.scannedNumber < it.value.desiredNumber) {
                                    "کسری"
                                }
                                else {
                                    "تایید شده"
                                }
                            }
                            diff > 0 -> {
                                if(scannedProducts[it.value.KBarCode]!!.scannedNumber == it.value.desiredNumber) {
                                    "تایید شده"
                                } else {
                                    "اضافی"
                                }
                            }
                            else -> {
                                "تایید شده"
                            }
                        },
                        scan = when {

                            diff == 0 -> {
                                "تایید شده"
                            }
                            diff < 0 -> {
                                if(scannedProducts[it.value.KBarCode]!!.scannedNumber > it.value.desiredNumber) {
                                    "اضافی فایل"
                                }
                                else if(scannedProducts[it.value.KBarCode]!!.scannedNumber < it.value.desiredNumber) {
                                    "کسری"
                                }
                                else {
                                    "تایید شده"
                                }
                            }
                            diff > 0 -> {
                                if(scannedProducts[it.value.KBarCode]!!.scannedNumber == it.value.desiredNumber) {
                                    "تایید شده"
                                } else {
                                    "اضافی فایل"
                                }
                            }
                            else -> {
                                "تایید شده"
                            }
                        },
                        productCode = it.value.productCode,
                        size = it.value.size,
                        color = it.value.color,
                        originalPrice = it.value.originalPrice,
                        salePrice = it.value.salePrice,
                        rfidKey = it.value.rfidKey,
                        primaryKey = it.value.primaryKey,
                    )
                    result[it.value.KBarCode] = resultData

                } else {
                    val resultData = Product(
                        name = it.value.name,
                        KBarCode = it.value.KBarCode,
                        imageUrl = it.value.imageUrl,
                        storeNumber = it.value.storeNumber,
                        wareHouseNumber = it.value.wareHouseNumber,
                        matchedNumber = when {
                            diff >= 0 -> {
                                diff
                            }
                            else -> {
                                it.value.desiredNumber
                            }
                        },
                        scannedEPCNumber = 0,
                        result = when {
                            diff == 0 -> {
                                "تایید شده"
                            }
                            diff < 0 -> {
                                "کسری"
                            }
                            else -> {
                                "اضافی"
                            }
                        },
                        scan = when {
                            diff == 0 -> {
                                "تایید شده"
                            }
                            diff < 0 -> {
                                "کسری"
                            }
                            else -> {
                                "اضافی فایل"
                            }
                        },
                        productCode = it.value.productCode,
                        size = it.value.size,
                        color = it.value.color,
                        originalPrice = it.value.originalPrice,
                        salePrice = it.value.salePrice,
                        rfidKey = it.value.rfidKey,
                        primaryKey = it.value.primaryKey,
                        desiredNumber = it.value.desiredNumber
                    )
                    result[it.value.KBarCode] = (resultData)
                }
            }
        }

        scannedProducts.forEach {
            if ((it.value.brandName == "JeansWest" || it.value.brandName == "JootiJeans" || it.value.brandName == "Baleno") && !it.value.name.contains(
                    "جوراب"
                )
            ) {
                if (it.key !in result.keys) {
                    val resultData = Product(
                        name = it.value.name,
                        KBarCode = it.value.KBarCode,
                        storeNumber = it.value.storeNumber,
                        wareHouseNumber = it.value.wareHouseNumber,
                        imageUrl = it.value.imageUrl,
                        category = "نامعلوم",
                        matchedNumber = it.value.scannedNumber,
                        scannedEPCNumber = it.value.scannedNumber,
                        result = "اضافی",
                        scan = "اضافی",
                        productCode = it.value.productCode,
                        size = it.value.size,
                        color = it.value.color,
                        originalPrice = it.value.originalPrice,
                        salePrice = it.value.salePrice,
                        rfidKey = it.value.rfidKey,
                        primaryKey = it.value.primaryKey,
                        desiredNumber = 0
                    )
                    result[it.key] = (resultData)
                }
            }
        }

        inventoryResult.clear()
        inventoryResult.putAll(result)

        inventoryProgress = if (inputProducts.isEmpty()) {
            0F
        } else {
            (inventoryResult.filter {
                it.value.result == "تایید شده"
            }.size.toFloat()) / inventoryResult.size.toFloat()
        }

        shortagesNumber = 0
        additionalNumber = 0
        numberOfScanned = 0

        inventoryResult.values.toMutableStateList().forEach {
            if (it.result == "کسری") {
                shortagesNumber += it.matchedNumber
            } else if (it.result == "اضافی") {
                additionalNumber += it.matchedNumber
            }
            numberOfScanned += it.scannedNumber
        }

        val shortageAndAdditional = inventoryResult.filter { it1 ->
            it1.value.result == "کسری" || it1.value.result == "اضافی"
        }.toMutableMap()

        val uiListTemp = mutableListOf<Product>()
        uiListTemp.addAll(shortageAndAdditional.values.toMutableStateList())
        uiListTemp.sortBy {
            it.KBarCode !in currentScannedProductKBarCodes
        }
        uiList.clear()
        uiList.addAll(uiListTemp)

        syncScannedProductsRunning = false
    }

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("conflicts")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("کد جست و جو")
        headerRow.createCell(1).setCellValue("تعداد اسکن شده")
        headerRow.createCell(2).setCellValue("کسری")
        headerRow.createCell(3).setCellValue("اضافی")

        uiList.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.KBarCode)
            row.createCell(1).setCellValue(it.scannedNumber.toDouble())

            if (it.scan == "کسری") {
                row.createCell(2).setCellValue(it.matchedNumber.toDouble())
            } else if (it.scan == "اضافی" || it.scan == "اضافی فایل") {
                row.createCell(3).setCellValue(it.matchedNumber.toDouble())
            }
        }

        val row = sheet.createRow(sheet.physicalNumberOfRows)
        row.createCell(0).setCellValue("مجموع")
        row.createCell(1).setCellValue(number.toDouble())

        val sheet2 = workbook.createSheet("کسری")

        val header2Row = sheet2.createRow(sheet2.physicalNumberOfRows)
        header2Row.createCell(0).setCellValue("کد جست و جو")
        header2Row.createCell(1).setCellValue("موجودی")
        header2Row.createCell(2).setCellValue("کسری")

        uiList.forEach {

            if (it.scan == "کسری") {
                val shortageRow = sheet2.createRow(sheet2.physicalNumberOfRows)
                shortageRow.createCell(0).setCellValue(it.KBarCode)
                shortageRow.createCell(1)
                    .setCellValue(it.scannedNumber.toDouble() + it.matchedNumber.toDouble())
                shortageRow.createCell(2).setCellValue(it.matchedNumber.toDouble())
            }
        }

        val sheet3 = workbook.createSheet("اضافی")

        val header3Row = sheet3.createRow(sheet3.physicalNumberOfRows)
        header3Row.createCell(0).setCellValue("کد جست و جو")
        header3Row.createCell(1).setCellValue("موجودی")
        header3Row.createCell(2).setCellValue("اضافی")

        uiList.forEach {

            if (it.scan == "اضافی") {
                val additionalRow = sheet3.createRow(sheet3.physicalNumberOfRows)
                additionalRow.createCell(0).setCellValue(it.KBarCode)
                additionalRow.createCell(1)
                    .setCellValue(it.matchedNumber - it.scannedNumber.toDouble())
                additionalRow.createCell(2).setCellValue(it.matchedNumber.toDouble())
            }
        }

        val dir = File(this.getExternalFilesDir(null), "/")

        val outFile = File(dir, "$fileName.xlsx")

        val outputStream = FileOutputStream(outFile.absolutePath)
        workbook.write(outputStream)
        outputStream.flush()
        outputStream.close()

        val uri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName + ".provider",
            outFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "application/octet-stream"
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this.startActivity(shareIntent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (inventoryStarted) {

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
                if (isInShortageAdditionalPage) {
                    isInShortageAdditionalPage = false
                } else {
                    back()
                }
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

        if (rfPower < 10) {
            rfPower = 30
        }
    }

    private suspend fun startRFScan() {

        isScanning = true
        if (!setRFPower(state, rf, rfPower)) {
            isScanning = false
            return
        }

        if (rfPower < 10) {
            currentScannedEpcTable.clear()
        }

        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        scannedEpcTable.add(uhfTagInfo.epc)
                        if (rfPower < 10) {
                            currentScannedEpcTable.add(uhfTagInfo.epc)
                        }
                    }
                } else {
                    break
                }
            }

            scannedEpcTable = scannedEpcTable.distinct().toMutableList()

            number = scannedEpcTable.size

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
        number = scannedEpcTable.size
        saveToMemory()
    }

    private fun syncInputItemsToServer() {

        val barcodeTableForV4 = mutableListOf<String>()

        syncInputProductsRunning = true
        inputProductsBiggerThan1000 = false

        run breakForEach@{
            inputBarcodesTable.forEach {
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
            syncInputProductsRunning = false
            return
        }

        syncInputProductsRunning = true

        getProductsV4(queue, state, mutableListOf(), barcodeTableForV4, { _, barcodes ->

            barcodes.forEach { product ->
                inputBarcodeMapWithProperties[product.scannedBarcode] = product
            }

            syncInputProductsRunning = false
            makeInputProductMap()

            if (inputProductsBiggerThan1000) {
                syncInputItemsToServer()
            } else {
                syncScannedItemsToServer()
            }

        }, {
            syncInputProductsRunning = false
        })
    }

    private fun makeInputProductMap() {

        Log.e("error", "makeInputProductMap")

        syncInputProductsRunning = true

        val isInDepo = locations[warehouseCode]?.contains("دپو") ?: false

        inputBarcodeMapWithProperties.forEach { it1 ->

            if (it1.value.KBarCode !in inputProducts.keys) {
                inputProducts[it1.value.KBarCode] = it1.value
                inputProducts[it1.value.KBarCode]!!.desiredNumber = if(isInDepo) {
                    inputProducts[it1.value.KBarCode]!!.wareHouseNumber
                } else {
                    inputProducts[it1.value.KBarCode]!!.storeNumber
                }
                if(!isInProgress) {
                    if(isInDepo) {
                        inputProducts[it1.value.KBarCode]!!.rfidWareHouseNumber = -1 * inputProducts[it1.value.KBarCode]!!.wareHouseNumber
                    } else {
                        inputProducts[it1.value.KBarCode]!!.rfidStoreNumber = -1 * inputProducts[it1.value.KBarCode]!!.storeNumber
                    }
                }
            }
        }

        syncInputProductsRunning = false
    }

    private fun syncScannedItemsToServer() {

        if (number == 0) {
            getConflicts()
            return
        }

        syncScannedProductsRunning = true

        val epcTableForV4 = mutableListOf<String>()
        scannedProductsBiggerThan1000 = false

        run breakForEach@{
            scannedEpcTable.forEach {
                if (it !in scannedEpcMapWithProperties.keys) {
                    if (epcTableForV4.size < 1000) {
                        epcTableForV4.add(it)
                    } else {
                        scannedProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }

        if (epcTableForV4.size == 0) {

            syncScannedProductsRunning = false
            getConflicts()
            return
        }

        getProductsV4(queue, state, epcTableForV4, mutableListOf(), { epcs, _ ->

            epcs.forEach { product ->
                scannedEpcMapWithProperties[product.scannedEPCs[0]] = product
            }

            syncScannedProductsRunning = false
            makeScannedProductMap()

            if (scannedProductsBiggerThan1000) {
                syncScannedItemsToServer()
            } else {
                getConflicts()
            }

        }, {
            getConflicts()
            syncScannedProductsRunning = false
        })
    }

    private fun makeScannedProductMap() {

        Log.e("error", "makeScannedProductMap")

        val isInDepo = locations[warehouseCode]?.contains("دپو") ?: false

        syncScannedProductsRunning = true

        scannedProducts.clear()
        currentScannedProductKBarCodes.clear()

        scannedEpcMapWithProperties.forEach { it1 ->

            currentScannedEpcTable.forEach {
                if (it == it1.key) {
                    currentScannedProductKBarCodes.add(it1.value.KBarCode)
                }
            }

            if (it1.value.KBarCode in scannedProducts.keys) {
                if(it1.value.scannedEPCs[0] !in scannedProducts[it1.value.KBarCode]!!.scannedEPCs) {
                    scannedProducts[it1.value.KBarCode]!!.scannedEPCNumber += 1
                    scannedProducts[it1.value.KBarCode]!!.scannedEPCs.add(it1.value.scannedEPCs[0])
                }
            } else {
                scannedProducts[it1.value.KBarCode] = it1.value
                if(!isInProgress) {
                    if(isInDepo) {
                        scannedProducts[it1.value.KBarCode]!!.rfidWareHouseNumber = -1 * scannedProducts[it1.value.KBarCode]!!.wareHouseNumber
                    } else {
                        scannedProducts[it1.value.KBarCode]!!.rfidStoreNumber = -1 * scannedProducts[it1.value.KBarCode]!!.storeNumber
                    }
                }
            }
        }
        syncScannedProductsRunning = false
    }

    private fun saveResultsToServer() {
        saveToServerInProgress = true

        val url = "http://rfid-api.avakatan.ir/mojodi-review/header"
        val request = object : JsonObjectRequest(Method.POST, url, null, {

            saveToServerId = it.getString("MojodiReviewInfo_ID")
            saveToServerInProgress = false
            Log.e("error", "1 passed")
            saveApi2()

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

                val error = JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        error.getString("message"),
                        null,
                        SnackbarDuration.Long
                    )
                }
            }

            saveToServerInProgress = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = mutableMapOf<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] =
                    "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

                val body = JSONObject()
                body.put("desc", "انبارگردانی با RFID")
                body.put("createDate", sdf.format(Date()))
                body.put("Warehouse_ID", warehouseCode.toInt())

                return body.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun saveApi2() {
        saveToServerInProgress = true

        val url = "http://rfid-api.avakatan.ir/mojodi-review/products"
        val request = object : StringRequest(Method.POST, url, {

            saveToServerInProgress = false
            if (resultsBiggerThan500) {
                Log.e("error", "2 running")
                saveApi2()
            } else {
                resultIndexForApi2 = 0
                Log.e("error", "2 passed")
                saveApi3()
            }
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

                val error = JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        error.getString("message"),
                        null,
                        SnackbarDuration.Long
                    )
                }
            }

            saveToServerInProgress = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = mutableMapOf<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {

                val body = JSONObject()
                val products = JSONArray()

                resultsBiggerThan500 = false

                for (i in resultIndexForApi2 until inventoryResult.values.toMutableList().size) {
                    val it = inventoryResult.values.toMutableList()[i]
                    val productJson = JSONObject()
                    productJson.put("BarcodeMain_ID", it.primaryKey)
                    productJson.put("kbarcode", it.KBarCode)
                    productJson.put("K_Name", it.kName)
                    productJson.put(
                        "diffCount",
                        if (it.result == "کسری") (it.matchedNumber * -1) else it.matchedNumber
                    )
                    products.put(productJson)

                    if (products.length() > 500) {
                        resultsBiggerThan500 = true
                        resultIndexForApi2 = i
                        break
                    }
                }

                body.put("products", products)

                return body.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun saveApi3() {
        saveToServerInProgress = true

        val url = "http://rfid-api.avakatan.ir/mojodi-review/$saveToServerId/submit"
        val request = object : StringRequest(Method.POST, url, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "اطلاعات انبارگردانی با موفقیت ثبت شدند",
                    null,
                    SnackbarDuration.Long
                )
            }
            saveToServerInProgress = false
            inventoryStarted = false
            inputBarcodesTable.clear()
            inputProducts.clear()
            inputBarcodeMapWithProperties.clear()
            clear()
            Log.e("error", "3 passed")

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
                        "اطلاعات انبارگردانی با موفقیت ثبت شدند",
                        null,
                        SnackbarDuration.Long
                    )
                }
                saveToServerInProgress = false
                inventoryStarted = false

                clear()
                saveToMemory()
                Log.e("error", "3 passed")
            }

        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = mutableMapOf<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] =
                    "Bearer " + MainActivity.token
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            5000,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("InventoryEPCTable", JSONArray(scannedEpcTable).toString())
        edit.putString("warehouseCodeForInventory", warehouseCode)
        edit.putBoolean("inventoryStarted", inventoryStarted)
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        locations = Gson().fromJson(
            memory.getString("userWarehouses", ""),
            locations.javaClass
        ) ?: mutableMapOf()
        warehouseCode = memory.getString("warehouseCodeForInventory", "") ?: ""
        inventoryStarted = memory.getBoolean("inventoryStarted", false)

        if (!inventoryStarted || warehouseCode !in locations.keys) {
            if (locations.keys.isEmpty()) {
                CoroutineScope(Main).launch {
                    state.showSnackbar(
                        "لطفا دوباره وارد حساب کاربری خود شوید",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {
                warehouseCode = locations.keys.toMutableList()[0]
            }
        }

        scannedEpcTable = Gson().fromJson(
            memory.getString("InventoryEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = scannedEpcTable.size

        number = scannedEpcTable.size
    }

    private fun finishPackage() {

        saveToServerInProgress = true

        val url = "http://rfid-api.avakatan.ir/mojodi-review/package/submit"
        val request = object : StringRequest(Method.POST, url, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "انبارگردانی دوره ای با موفقیت به پایان رسید",
                    null,
                    SnackbarDuration.Long
                )
            }
            saveToServerInProgress = false
            Log.e("error", "4 passed")

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
                val error = JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        error.getString("message"),
                        null,
                        SnackbarDuration.Long
                    )
                }
            }
            saveToServerInProgress = false

        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = mutableMapOf<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] =
                    "Bearer " + MainActivity.token
                return params
            }
            override fun getBody(): ByteArray {

                val body = JSONObject()

                body.put("Warehouse_ID", warehouseCode.toInt())

                return body.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            5000,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun clear() {

        scannedEpcTable.clear()
        epcTablePreviousSize = 0
        number = 0
        scannedProducts.clear()
        scannedEpcMapWithProperties.clear()
        inventoryResult.clear()
        getConflicts()
        saveToMemory()
        openDialog = false
    }

    private fun back() {

        saveToMemory()
        stopRFScan()
        queue.stop()
        beep.release()
        syncInputProductsRunning = false
        syncScannedProductsRunning = false
        isInProgress = false
        isScanning = false
        scannedProductsBiggerThan1000 = false
        inputProductsBiggerThan1000 = false
        resultsBiggerThan500 = false
        saveToServerInProgress = false
        finish()
    }

    @OptIn(ExperimentalCoilApi::class)
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { if (isInShortageAdditionalPage) ListAppBar() else AppBar() },
                    content = { if (isInShortageAdditionalPage) ListContent() else Content() },
                    bottomBar = { if (isInShortageAdditionalPage) ListBottomAppBar() },
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
                IconButton(modifier = Modifier.testTag("InventoryClearButton"),
                    onClick = { openClearDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.inventoryText),
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

        Box(
            modifier = Modifier
                .padding(bottom = 150.dp)
                .fillMaxSize()
        ) {

            if (syncInputProductsRunning || syncScannedProductsRunning || isScanning || saveToServerInProgress) {
                LoadingCircularProgressIndicator(
                    isScanning,
                    syncInputProductsRunning || syncScannedProductsRunning || saveToServerInProgress
                )
            }

            Box(
                modifier = Modifier
                    .shadow(1.dp, shape = Shapes.small)
                    .size(200.dp)
                    .align(
                        Alignment.Center
                    )
                    .background(color = Color.White, shape = Shapes.medium),
            ) {

                CircularProgressIndicator(
                    inventoryProgress,
                    modifier = Modifier
                        .size(150.dp)
                        .align(
                            Alignment.Center
                        ),
                    Jeanswest,
                    8.dp
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "${inventoryProgress * 100}%", modifier = Modifier
                        .fillMaxWidth()
                        .align(
                            Alignment.Center
                        ),
                    textAlign = TextAlign.Center,
                    style = Typography.h5
                )
            }

        }

        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {

            if (openDialog) {
                FileAlertDialog()
            }
            if (openClearDialog) {
                ClearAlertDialog()
            }
            if (openFinishDialog) {
                FinishAlertDialog()
            }
            if (openStartOrContinueDialog) {
                StartOrContinueAlertDialog()
            }

            Column(
                modifier = Modifier
                    .shadow(1.dp)
                    .fillMaxWidth()
                    .background(color = Color.White)
            ) {

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
                ) {

                    FilterDropDownList(
                        modifier = Modifier.wrapContentWidth(),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_location_city_24),
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
                                text = locations[warehouseCode] ?: "",
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 4.dp)
                            )
                        },
                        values = locations.values.toMutableList(),
                        onClick = {
                            if (inventoryStarted) {
                                CoroutineScope(Main).launch {
                                    state.showSnackbar(
                                        "در هنگام انبارگردانی امکان تغییر مکان وجود ندارد. بعد از پایان انبارگردانی فعلی، می توانید مکان جدیدی را برای انبارگردانی انتخاب کنید",
                                        null,
                                        SnackbarDuration.Long
                                    )
                                }
                            } else {
                                warehouseCode = locations.filter { it1 ->
                                    it1.value == it
                                }.keys.toMutableList()[0]
                                saveToMemory()
                            }
                        }
                    )

                    Button(modifier = Modifier
                        .padding(start = 24.dp)
                        .fillMaxWidth()
                        .height(48.dp),
                        onClick = {
                            isInShortageAdditionalPage = true
                        }) {
                        Text(text = "مغایرت ها", style = Typography.h2)
                    }
                }
                Button(modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp),
                    onClick = {
                        if (!inventoryStarted) {
                            openStartOrContinueDialog = true
                        } else {
                            openFinishDialog = true
                        }
                    }) {
                    Text(
                        text = if (inventoryStarted) "پایان انبارگردانی" else "شروع انبارگردانی",
                        style = Typography.h2
                    )
                }
            }
        }
    }

    @Composable
    fun ListBottomAppBar() {
        BottomAppBar(backgroundColor = MaterialTheme.colors.background) {

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "کسری: " + inventoryResult.values.toMutableStateList().filter {
                        it.result == "کسری"
                    }.size,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )

                Text(
                    text = "اضافی: " + inventoryResult.values.toMutableStateList().filter {
                        it.result == "اضافی"
                    }.size,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }

    @Composable
    fun ListAppBar() {

        TopAppBar(

            navigationIcon = {
                IconButton(onClick = { isInShortageAdditionalPage = false }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.inventoryText),
                    modifier = Modifier
                        .padding(end = 50.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun ListContent() {

        Column(Modifier.fillMaxSize()) {

            if (syncInputProductsRunning || syncScannedProductsRunning || isScanning) {
                LoadingCircularProgressIndicator(
                    isScanning,
                    syncInputProductsRunning || syncScannedProductsRunning
                )
            }

            Button(modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally), onClick = {

                if (inventoryStarted) {
                    stopRFScan()
                    rfPower = 5
                    scanningJob = CoroutineScope(IO).launch {
                        startRFScan()
                    }
                }
            }) {
                Text(text = "تعریف ناحیه جست و جو")
            }

            LazyColumn(modifier = Modifier.padding(top = 8.dp, bottom = 56.dp)) {

                items(uiList.size) { i ->
                    Item(
                        i,
                        uiList,
                        text1 = "موجودی: " + uiList[i].desiredNumber,
                        text2 = uiList[i].result + ":" + " " + uiList[i].matchedNumber,
                        clickable = true,
                        onClick = {
                            Intent(
                                this@InventoryActivity,
                                SearchSpecialProductActivity::class.java
                            ).apply {
                                this.putExtra(
                                    "product",
                                    Gson().toJson(uiList[i]).toString()
                                )
                                startActivity(this)
                            }
                        }
                    )
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
                            exportFile()
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
                            saveResultsToServer()

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = {
                                openFinishDialog = false
                                inventoryStarted = false
                                queue.stop()
                                syncInputProductsRunning = false
                                syncScannedProductsRunning = false
                                inputBarcodesTable.clear()
                                inputProducts.clear()
                                inputBarcodeMapWithProperties.clear()
                                clear()
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

    @Composable
    fun StartOrContinueAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openStartOrContinueDialog = false
            },
            buttons = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Button(onClick = {
                        openStartOrContinueDialog = false
                        inventoryStarted = true
                        clear()
                        getWarehouseBarcodes()

                    }, modifier = Modifier.padding(top = 10.dp)) {
                        Text(text = "ادامه انبارگردانی قبلی")
                    }
                    Button(
                        onClick = {
                            openStartOrContinueDialog = false
                            finishPackage()
                            inventoryStarted = true
                            clear()
                            getWarehouseBarcodes()
                        },
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(text = "شروع انبارگردانی جدید")
                    }
                }
            }
        )
    }
}