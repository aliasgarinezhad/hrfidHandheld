package com.jeanwest.reader.checkIn

//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.JalaliDate.JalaliDate
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs


class CheckInActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var epcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var barcodeTable = mutableListOf<String>()
    private var excelBarcodes = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    private var barcodeIsEnabled = false
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
    private var number by mutableStateOf(0)
    private var fileName by mutableStateOf("حواله تایید شده تاریخ ")
    private var openDialog by mutableStateOf(false)
    private var uiList by mutableStateOf(mutableListOf<CheckInConflictResultProduct>())
    private val scanValues =
        arrayListOf("همه اجناس", "تایید شده", "اضافی", "کسری", "اضافی فایل", "خراب")
    private var scanFilter by mutableStateOf(0)
    private var signedValue by mutableStateOf(mutableListOf("همه", "نشانه دار", "بی نشانه"))
    private var signedFilter by mutableStateOf(0)
    private var signedProductCodes = mutableListOf<String>()
    private var openClearDialog by mutableStateOf(false)

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
        rfInit()
        setContent {
            Page()
        }
        loadMemory()
        syncInputItemsToServer()

        val util = JalaliDate()
        fileName += util.currentShamsidate
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

    private fun rfInit(): Boolean {

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        for (i in 0..11) {
            if (rf.setEPCMode()) {
                return true
            }
        }
        CoroutineScope(Main).launch {
            Toast.makeText(
                this@CheckInActivity,
                "مشکلی در سخت افزار پیش آمده است",
                Toast.LENGTH_LONG
            ).show()
        }
        return false
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
        if (!setRFPower(rfPower)) {
            isScanning = false
            return
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
                        productCode = fileProduct.productCode
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
                    result = "اضافی: " + scannedProduct.scannedNumber + "(0)",
                    scan = "اضافی",
                    productCode = scannedProduct.productCode
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
                    result = "کسری: " + fileProduct.number + "(${fileProduct.number})",
                    scan = "کسری",
                    productCode = fileProduct.productCode
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
                result = "خراب: " + 1,
                scan = "خراب",
                productCode = ""
            )
            result.add(resultData)
        }

        return result
    }

    private fun filterResult(conflictResult: MutableList<CheckInConflictResultProduct>): MutableList<CheckInConflictResultProduct> {

        val signedFilterOutput =
            when {
                signedValue[signedFilter] == "همه" -> {
                    conflictResult
                }
                signedValue[signedFilter] == "نشانه دار" -> {
                    conflictResult.filter {
                        it.KBarCode in signedProductCodes
                    } as ArrayList<CheckInConflictResultProduct>
                }
                signedValue[signedFilter] == "بی نشانه" -> {
                    conflictResult.filter {
                        it.KBarCode !in signedProductCodes
                    } as ArrayList<CheckInConflictResultProduct>
                }
                else -> {
                    conflictResult
                }
            }

        shortagesNumber = 0
        signedFilterOutput.filter {
            it.scan == "کسری"
        }.forEach {
            shortagesNumber += it.matchedNumber
        }

        additionalNumber = 0
        signedFilterOutput.filter {
            it.scan == "اضافی" || it.scan == "اضافی فایل"
        }.forEach {
            additionalNumber += it.matchedNumber
        }

        shortageCodesNumber = signedFilterOutput.filter { it.scan == "کسری" }.size
        additionalCodesNumber =
            signedFilterOutput.filter { it.scan == "اضافی" || it.scan == "اضافی فایل" }.size

        val uiListParameters =
            when {
                scanValues[scanFilter] == "همه اجناس" -> {
                    signedFilterOutput
                }
                scanValues[scanFilter] == "اضافی" -> {
                    signedFilterOutput.filter {
                        it.scan == "اضافی" || it.scan == "اضافی فایل"
                    } as ArrayList<CheckInConflictResultProduct>
                }
                else -> {
                    signedFilterOutput.filter {
                        it.scan == scanValues[scanFilter]
                    } as ArrayList<CheckInConflictResultProduct>
                }
            }

        return uiListParameters
    }

    private fun setRFPower(power: Int): Boolean {
        if (rf.power != power) {

            for (i in 0..11) {
                if (rf.setPower(power)) {
                    return true
                }
            }
            CoroutineScope(Main).launch {
                Toast.makeText(
                    this@CheckInActivity,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        } else {
            return true
        }
    }

    private fun syncInputItemsToServer() {

        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

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
                    productCode = fileJsonArray.getJSONObject(i).getString("K_Bar_Code")
                )
                inputProducts.add(fileProduct)
            }

            if (number != 0) {
                syncScannedItemsToServer()
            } else {
                conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
                uiList = filterResult(conflictResultProducts)
            }
        }, { response ->

            if (excelBarcodes.isEmpty()) {
                Toast.makeText(
                    this@CheckInActivity,
                    "هیچ بارکدی در فایل یافت نشد",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this@CheckInActivity, response.toString(), Toast.LENGTH_LONG)
                    .show()
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

                val scannedProduct = CheckInScannedProduct(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedNumber = epcs.getJSONObject(i).getInt("handheldCount"),
                    productCode = epcs.getJSONObject(i).getString("K_Bar_Code")
                )
                scannedProducts.add(scannedProduct)
            }

            invalidEpcs.clear()
            for (i in 0 until invalids.length()) {
                invalidEpcs.add(invalids.getString(i))
            }

            conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)

        }, { response ->
            if ((epcTable.size + barcodeTable.size) == 0) {
                Toast.makeText(this, "کالایی جهت بررسی وجود ندارد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CheckInActivity, response.toString(), Toast.LENGTH_LONG)
                    .show()
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
            number = epcTable.size + barcodeTable.size
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
        edit.putString(
            "CheckInSignedCodesTable",
            JSONArray(signedProductCodes).toString()
        )
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

        signedProductCodes = Gson().fromJson(
            memory.getString("CheckInSignedCodesTable", ""),
            signedProductCodes.javaClass
        ) ?: mutableListOf()

        number = epcTable.size + barcodeTable.size
    }

    private fun clear() {

        if (number != 0) {
            barcodeTable.clear()
            epcTable.clear()
            invalidEpcs.clear()
            epcTablePreviousSize = 0
            number = 0
            scannedProducts.clear()
            conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)
            openDialog = false
            saveToMemory()
        } else {
            barcodeTable.clear()
            epcTable.clear()
            invalidEpcs.clear()
            epcTablePreviousSize = 0
            number = 0
            excelBarcodes.clear()
            scannedProducts.clear()
            inputProducts.clear()
            conflictResultProducts = getConflicts(inputProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)
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

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("تروفالس")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("کد جست و جو")
        headerRow.createCell(1).setCellValue("تعداد")
        headerRow.createCell(2).setCellValue("کسری")
        headerRow.createCell(3).setCellValue("اضافی")
        headerRow.createCell(4).setCellValue("نشانه")

        conflictResultProducts.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.KBarCode)
            row.createCell(1).setCellValue(it.scannedNumber.toDouble())

            if (it.scan == "کسری") {
                row.createCell(2).setCellValue(it.matchedNumber.toDouble())
            } else if (it.scan == "اضافی" || it.scan == "اضافی فایل") {
                row.createCell(3).setCellValue(it.matchedNumber.toDouble())
            }

            if (it.KBarCode in signedProductCodes) {
                row.createCell(4).setCellValue("نشانه دار")
            }
        }

        val row = sheet.createRow(sheet.physicalNumberOfRows)
        row.createCell(0).setCellValue("مجموع")
        row.createCell(1).setCellValue(number.toDouble())
        row.createCell(2).setCellValue(shortagesNumber.toDouble())
        row.createCell(3).setCellValue(additionalNumber.toDouble())

        val sheet2 = workbook.createSheet("کسری")

        val header2Row = sheet2.createRow(sheet2.physicalNumberOfRows)
        header2Row.createCell(0).setCellValue("کد جست و جو")
        header2Row.createCell(1).setCellValue("موجودی")
        header2Row.createCell(2).setCellValue("کسری")
        header2Row.createCell(3).setCellValue("نشانه")

        conflictResultProducts.forEach {

            if (it.scan == "کسری") {
                val row = sheet2.createRow(sheet2.physicalNumberOfRows)
                row.createCell(0).setCellValue(it.KBarCode)
                row.createCell(1)
                    .setCellValue(it.scannedNumber.toDouble() + it.matchedNumber.toDouble())
                row.createCell(2).setCellValue(it.matchedNumber.toDouble())

                if (it.KBarCode in signedProductCodes) {
                    row.createCell(3).setCellValue("نشانه دار")
                }
            }
        }

        val sheet3 = workbook.createSheet("اضافی")

        val header3Row = sheet3.createRow(sheet3.physicalNumberOfRows)
        header3Row.createCell(0).setCellValue("کد جست و جو")
        header3Row.createCell(1).setCellValue("موجودی")
        header3Row.createCell(2).setCellValue("اضافی")
        header3Row.createCell(3).setCellValue("نشانه")

        conflictResultProducts.forEach {

            if (it.scan == "اضافی") {
                val row = sheet3.createRow(sheet3.physicalNumberOfRows)
                row.createCell(0).setCellValue(it.KBarCode)
                row.createCell(1).setCellValue(it.matchedNumber - it.scannedNumber.toDouble())
                row.createCell(2).setCellValue(it.matchedNumber.toDouble())

                if (it.KBarCode in signedProductCodes) {
                    row.createCell(3).setCellValue("نشانه دار")
                }
            }
        }

        var dir = File(this.getExternalFilesDir(null), "/RFID")
        dir.mkdir()
        dir = File(this.getExternalFilesDir(null), "/RFID/خروجی/")
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
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(shareIntent)
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
                IconButton(onClick = { openClearDialog = true }) {
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
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
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

            if (openClearDialog) {
                ClearAlertDialog()
            }

            Column(
                modifier = Modifier
                    .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
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
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
                            .align(Alignment.CenterVertically)
                            .weight(1F),
                    )
                    Text(
                        text = "کسری ها(یکتا): $shortagesNumber($shortageCodesNumber)",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
                            .weight(1F),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .weight(1F)
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            text = "بارکد",
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 10.dp),
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

                    Text(
                        text = "اضافی ها(یکتا): $additionalNumber($additionalCodesNumber)",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
                            .weight(1F),
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ScanFilterDropDownList(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 8.dp, end = 8.dp)
                    )
                    SignedFilterDropDownList(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 8.dp, end = 8.dp)
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
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .background(
                                color = if (uiList[i].KBarCode !in signedProductCodes) {
                                    MaterialTheme.colors.onPrimary
                                } else {
                                    MaterialTheme.colors.primary
                                },
                                shape = MaterialTheme.shapes.small
                            )
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {

                                },
                                onLongClick = {
                                    if (uiList[i].KBarCode !in signedProductCodes) {
                                        signedProductCodes.add(uiList[i].KBarCode)
                                    } else {
                                        signedProductCodes.remove(uiList[i].KBarCode)
                                    }
                                    uiList = mutableListOf()
                                    uiList = filterResult(conflictResultProducts)
                                },
                            ),
                    ) {
                        Column {
                            Text(
                                text = uiList[i].name,
                                style = MaterialTheme.typography.h1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Brown)
                            )

                            Text(
                                text = uiList[i].KBarCode,
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.DarkGreen)
                            )

                            Text(
                                text = uiList[i].result,
                                style = MaterialTheme.typography.body1,
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
    fun ScanFilterDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
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
    fun SignedFilterDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = signedValue[signedFilter])
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
                        signedFilter = signedValue.indexOf(it)
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

    @ExperimentalFoundationApi
    @Preview
    @Composable
    fun Preview() {

        openClearDialog = true

        ClearAlertDialog()
    }
}