package com.jeanwest.reader.fileAttachment

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.compose.rememberImagePainter
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs


class FileAttachment : ComponentActivity(), IBarcodeResult {

    lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    var epcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    var barcodeTable = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    private var barcodeIsEnabled = false
    private val fileProducts = ArrayList<FileProduct>()
    private val scannedProducts = ArrayList<ScannedProduct>()
    private val invalidEpcs = ArrayList<String>()
    private var scanningJob: Job? = null

    //ui parameters
    private var conflictResultProducts by mutableStateOf(ArrayList<conflictResultProduct>())
    private var isScanning by mutableStateOf(false)
    private var shortageNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var number by mutableStateOf(0)
    private var fileName by mutableStateOf("خروجی")
    private var openDialog by mutableStateOf(false)
    private var uiList by mutableStateOf(ArrayList<conflictResultProduct>())
    private var categoryFilter by mutableStateOf(0)
    private var categoryValues by mutableStateOf(arrayListOf("همه دسته ها", "نامعلوم"))
    private val scanValues =
        arrayListOf("همه اجناس", "تایید شده", "اضافی", "کسری", "اضافی فایل", "خراب")
    private var scanFilter by mutableStateOf(0)

    private val apiTimeout = 30000
    val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        setContent {
            Page()
        }
        loadMemory()
        number = epcTable.size + barcodeTable.size

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        if (intent.action == Intent.ACTION_SEND) {

            if (getString(R.string.xlsx) == intent.type || getString(R.string.xls) == intent.type) {

                checkLogin()
                rfInit()

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
                Toast.makeText(this, "فرمت فایل باید اکسل باشد", Toast.LENGTH_LONG).show()
                if (number != 0) {
                    syncScannedItemsToServer()
                }
            }
        } else {
            while (!rf.setEPCMode()) {
                rf.free()
            }
            if (number != 0) {
                syncScannedItemsToServer()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (barcodeIsEnabled) {
                    stopRFScan()
                    startBarcodeScan()
                } else {
                    if (!isScanning) {

                        scanningJob = CoroutineScope(IO).launch {
                            startRFScan()
                        }

                    } else {

                        stopRFScan()
                        if (number != 0) {
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

    private fun checkLogin() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        if (memory.getString("username", "") != "") {

            MainActivity.username = memory.getString("username", "")!!
            MainActivity.token = memory.getString("accessToken", "")!!
        } else {
            Toast.makeText(
                this,
                "لطفا ابتدا به حساب کاربری خود وارد شوید",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun rfInit() {

        val frequency: Int
        val rfLink = 2

        when (Build.MODEL) {
            getString(R.string.EXARK) -> {
                frequency = 0x08
            }
            getString(R.string.chainway) -> {
                frequency = 0x04
            }
            else -> {
                Toast.makeText(
                    this,
                    "دستگاه فاقد ماژول RFID است",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        for (i in 0..11) {

            if (rf.init()) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            } else {
                rf.free()
            }
        }

        for (i in 0..11) {

            if (rf.setFrequencyMode(frequency)) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        for (i in 0..11) {

            if (rf.setRFLink(rfLink)) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
    }

    private suspend fun startRFScan() {

        isScanning = true
        if (rf.power != rfPower) {

            for (i in 0..11) {

                if (rf.setPower(rfPower)) {
                    break
                } else if (i == 10) {
                    CoroutineScope(Main).launch {
                        Toast.makeText(
                            this@FileAttachment,
                            "مشکلی در سخت افزار پیش آمده است",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    isScanning = false
                    return
                }
            }
        }

        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null && uhfTagInfo.epc.startsWith("30")) {
                    epcTable.add(uhfTagInfo.epc)
                } else {
                    break
                }
            }

            epcTable = epcTable.distinct().toMutableList()

            number = epcTable.size + barcodeTable.size

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
        number = epcTable.size + barcodeTable.size
        saveToMemory()
    }

    private fun getConflicts(
        fileProducts: ArrayList<FileProduct>,
        scannedProducts: ArrayList<ScannedProduct>,
        invalidEPCs: ArrayList<String>
    ): ArrayList<conflictResultProduct> {

        val result = ArrayList<conflictResultProduct>()

        val samePrimaryKeys = ArrayList<Long>()

        scannedProducts.forEach { scannedProduct ->

            fileProducts.forEach { fileProduct ->

                if (scannedProduct.primaryKey == fileProduct.primaryKey) {

                    val resultData = conflictResultProduct(
                        name = fileProduct.name,
                        KBarCode = fileProduct.KBarCode,
                        imageUrl = fileProduct.imageUrl,
                        category = fileProduct.category,
                        matchedNumber = abs(scannedProduct.scannedNumber - fileProduct.number),
                        scannedNumber = scannedProduct.scannedNumber,
                        result =
                        when {
                            scannedProduct.scannedNumber > fileProduct.number -> {
                                "اضافی: " + (scannedProduct.scannedNumber - fileProduct.number) + "(${fileProduct.number})"
                            }
                            scannedProduct.scannedNumber < fileProduct.number -> {
                                "کسری: " + (fileProduct.number - scannedProduct.scannedNumber) + "(${fileProduct.number})"
                            }
                            else -> {
                                "تایید شده: " + fileProduct.number
                            }
                        },
                        scan = when {
                            scannedProduct.scannedNumber > fileProduct.number -> {
                                "اضافی فایل"
                            }
                            scannedProduct.scannedNumber < fileProduct.number -> {
                                "کسری"
                            }
                            else -> {
                                "تایید شده"
                            }
                        },
                    )
                    result.add(resultData)
                    samePrimaryKeys.add(fileProduct.primaryKey)
                }
            }
            if (scannedProduct.primaryKey !in samePrimaryKeys) {
                val resultData = conflictResultProduct(
                    name = scannedProduct.name,
                    KBarCode = scannedProduct.KBarCode,
                    imageUrl = scannedProduct.imageUrl,
                    category = "نامعلوم",
                    matchedNumber = scannedProduct.scannedNumber,
                    scannedNumber = scannedProduct.scannedNumber,
                    result = "اضافی: " + scannedProduct.scannedNumber + "(0)",
                    scan = "اضافی"
                )
                result.add(resultData)
            }
        }

        fileProducts.forEach { fileProduct ->
            if (fileProduct.primaryKey !in samePrimaryKeys) {
                val resultData = conflictResultProduct(
                    name = fileProduct.name,
                    KBarCode = fileProduct.KBarCode,
                    imageUrl = fileProduct.imageUrl,
                    category = fileProduct.category,
                    matchedNumber = fileProduct.number,
                    scannedNumber = 0,
                    result = "کسری: " + fileProduct.number + "(${fileProduct.number})",
                    scan = "کسری"
                )
                result.add(resultData)
            }
        }

        invalidEPCs.forEach {
            val resultData = conflictResultProduct(
                name = it,
                KBarCode = "",
                imageUrl = "",
                category = "نامعلوم",
                matchedNumber = 0,
                scannedNumber = 1,
                result = "خراب: " + 1,
                scan = "خراب"
            )
            result.add(resultData)
        }

        shortageNumber = 0
        result.filter {
            it.scan == "کسری"
        }.forEach {
            shortageNumber += it.matchedNumber
        }

        additionalNumber = 0
        result.filter {
            it.scan == "اضافی" || it.scan == "اضافی فایل"
        }.forEach {
            additionalNumber += it.matchedNumber
        }

        return result
    }

    private fun filterResult(conflictResult: ArrayList<conflictResultProduct>): ArrayList<conflictResultProduct> {

        val scanFilterOutput =
            when {
                scanValues[scanFilter] == "همه اجناس" -> {
                    conflictResult
                }
                scanValues[scanFilter] == "اضافی" -> {
                    conflictResult.filter {
                        it.scan == "اضافی" || it.scan == "اضافی فایل"
                    } as ArrayList<conflictResultProduct>
                }
                else -> {
                    conflictResult.filter {
                        it.scan == scanValues[scanFilter]
                    } as ArrayList<conflictResultProduct>
                }
            }
        val output =
            if (categoryValues[categoryFilter] == "همه دسته ها") {
                scanFilterOutput
            } else {
                scanFilterOutput.filter {
                    it.category == categoryValues[categoryFilter]
                } as ArrayList<conflictResultProduct>
            }

        return output
    }

    private fun syncScannedItemsToServer() {

        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

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

                val scannedProduct = ScannedProduct(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedNumber = epcs.getJSONObject(i).getInt("handheldCount"),
                )
                scannedProducts.add(scannedProduct)
            }

            invalidEpcs.clear()
            for (i in 0 until invalids.length()) {
                invalidEpcs.add(invalids.getString(i))
            }

            conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)

        }, { response ->
            if ((epcTable.size + barcodeTable.size) == 0) {
                Toast.makeText(this, "کالایی جهت بررسی وجود ندارد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@FileAttachment, response.toString(), Toast.LENGTH_LONG).show()
            }
            conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
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

        val queue = Volley.newRequestQueue(this@FileAttachment)
        queue.add(request)
    }

    private fun readXLSXFile(uri: Uri) {

        val excelBarcodes = ArrayList<String>()
        val workbook: XSSFWorkbook
        try {
            workbook = XSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {
            Toast.makeText(this, "فایل نامعتبر است", Toast.LENGTH_LONG).show()
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {
            Toast.makeText(this, "فایل خالی است", Toast.LENGTH_LONG).show()
            return
        }

        val barcodeToCategoryMap = HashMap<String, String>()

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

        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

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
            if (number != 0) {
                syncScannedItemsToServer()
            } else {
                conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
                uiList = filterResult(conflictResultProducts)
            }
        }, { response ->
            Toast.makeText(this@FileAttachment, response.toString(), Toast.LENGTH_LONG).show()
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

        val queue = Volley.newRequestQueue(this@FileAttachment)
        queue.add(request)
    }

    private fun readXLSFile(uri: Uri) {

        val excelBarcodes = ArrayList<String>()

        val workbook: HSSFWorkbook
        try {
            workbook = HSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {
            Toast.makeText(this, "فایل نامعتبر است", Toast.LENGTH_LONG).show()
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {
            Toast.makeText(this, "فایل خالی است", Toast.LENGTH_LONG).show()
            return
        }

        val barcodeToCategoryMap = HashMap<String, String>()

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

        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

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
            if (number != 0) {
                syncScannedItemsToServer()
            } else {
                conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
                uiList = filterResult(conflictResultProducts)
            }
        }, { response ->
            Toast.makeText(this@FileAttachment, response.toString(), Toast.LENGTH_LONG).show()
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

        val queue = Volley.newRequestQueue(this@FileAttachment)
        queue.add(request)
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            barcodeTable.add(barcode)
            number = epcTable.size + barcodeTable.size
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        val epcJson = JSONArray(epcTable)
        edit.putString("FileAttachmentEPCTable", epcJson.toString())
        val barcodesJson = JSONArray(barcodeTable)
        edit.putString("FileAttachmentBarcodeTable", barcodesJson.toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        epcTable = Gson().fromJson(
            memory.getString("FileAttachmentEPCTable", ""),
            epcTable.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = epcTable.size

        barcodeTable = Gson().fromJson(
            memory.getString("FileAttachmentBarcodeTable", ""),
            barcodeTable.javaClass
        ) ?: mutableListOf()
    }

    private fun clear() {
        barcodeTable.clear()
        epcTable.clear()
        epcTablePreviousSize = 0
        shortageNumber = 0
        additionalNumber = 0
        number = 0
        saveToMemory()
        number = epcTable.size + barcodeTable.size

        invalidEpcs.clear()
        fileProducts.clear()
        scannedProducts.clear()
        conflictResultProducts = ArrayList()
        uiList = ArrayList()
        openDialog = false
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

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("conflicts")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        val headerCell = headerRow.createCell(0)
        headerCell.setCellValue("کد جست و جو")
        val headerCell2 = headerRow.createCell(1)
        headerCell2.setCellValue("تعداد")
        val headerCell3 = headerRow.createCell(2)
        headerCell3.setCellValue("دسته")
        val headerCell4 = headerRow.createCell(3)
        headerCell4.setCellValue("کسری")
        val headerCell5 = headerRow.createCell(4)
        headerCell5.setCellValue("اضافی")

        conflictResultProducts.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.KBarCode)
            row.createCell(1).setCellValue(it.scannedNumber.toDouble())
            row.createCell(2).setCellValue(it.category)

            if (it.scan == "کسری") {
                row.createCell(3).setCellValue(it.matchedNumber.toDouble())
            } else if (it.scan == "اضافی" || it.scan == "اضافی فایل") {
                row.createCell(4).setCellValue(it.matchedNumber.toDouble())
            }
        }

        var dir = File(Environment.getExternalStorageDirectory(), "/RFID")
        dir.mkdir()
        dir = File(Environment.getExternalStorageDirectory(), "/RFID/خروجی/")
        dir.mkdir()

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
        applicationContext.startActivity(shareIntent)
        Toast.makeText(
            this,
            "فایل در مسیر " + "/RFID/خروجی" + " " + "ذخیره شد",
            Toast.LENGTH_LONG
        )
            .show()
    }

    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
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
                IconButton(onClick = { }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_check_box_outline_blank_24),
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
                IconButton(onClick = { clear() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = "پیوست فایل",
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @Composable
    fun Content() {

        var slideValue by rememberSaveable { mutableStateOf(30F) }
        var switchValue by rememberSaveable { mutableStateOf(false) }
        val modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .wrapContentWidth()

        Column {

            if (openDialog) {
                FileAlertDialog()
            }

            Column(
                modifier = Modifier
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .fillMaxWidth()
            ) {

                Row {

                    Text(
                        text = "اندازه توان(" + slideValue.toInt() + ")",
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(start = 8.dp, end = 8.dp)
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
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = "تعداد اسکن شده: $number",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(horizontal = 8.dp),
                    )

                    Row {
                        Text(
                            text = "بارکد",
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 10.dp)
                                .align(Alignment.CenterVertically),
                        )

                        Switch(
                            checked = switchValue,
                            onCheckedChange = {
                                barcodeIsEnabled = it
                                switchValue = it
                            },
                            modifier = Modifier.padding(end = 4.dp, bottom = 10.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = "کسری: $shortageNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                    Text(
                        text = "کد: " + conflictResultProducts.filter { it.scan == "کسری" }.size,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                    Text(
                        text = "اضافی: $additionalNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                    Text(
                        text = "کد: " + conflictResultProducts.filter { it.scan == "اضافی" }.size,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ScanFilterDropDownList()
                    CategoryFilterDropDownList()
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
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .background(
                                MaterialTheme.colors.onPrimary,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = uiList[i].name,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Brown)
                            )

                            Text(
                                text = uiList[i].KBarCode,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.DarkGreen)
                            )

                            Text(
                                text = uiList[i].result,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Goldenrod)
                            )
                        }

                        Image(
                            painter = rememberImagePainter(
                                uiList[i].imageUrl,
                            ),
                            contentDescription = "",
                            modifier = Modifier
                                .height(100.dp)
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        }

    }

    @Composable
    fun ScanFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
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
                        uiList = ArrayList()
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
    fun CategoryFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = categoryValues[categoryFilter])
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
                        uiList = ArrayList()
                        categoryFilter = categoryValues.indexOf(it)
                        uiList = filterResult(conflictResultProducts)
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

    @Preview
    @Composable
    fun Preview() {

        shortageNumber = 0
        additionalNumber = 0
        number = 0
        scanFilter = 0
        fileName = "خروجی"
        openDialog = false

        Page()
    }
}