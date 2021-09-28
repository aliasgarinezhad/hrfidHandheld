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
import androidx.lifecycle.ViewModel
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
import com.jeanwest.reader.warehouseScanning.WarehouseScanningActivity
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class FileAttachment : ComponentActivity(), IBarcodeResult {

    lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    var epcTable: MutableMap<String, Int> = HashMap()
    var epcTablePreviousSize = 0
    var barcodeTable = ArrayList<String>()
    private val barcode2D = Barcode2D(this)
    private var barcodeIsEnabled = false

    //ui parameters
    private var isScanning by mutableStateOf(false)
    private var shortageNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var matchedNumber by mutableStateOf(0)
    private var resultLists by mutableStateOf(ConflictLists())
    private var number by mutableStateOf(0)
    private var filter by mutableStateOf(0)
    private var fileName by mutableStateOf("خروجی")
    private var openDialog by mutableStateOf(false)

    private val apiTimeout = 20000
    private var fileJsonArray = JSONArray()
    private var scannedJsonArray = JSONArray()
    var beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open()

        setContent {
            Page()
        }

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        epcTable =
            if (memory.getString("FileAttachmentEPCTable", "").isNullOrEmpty()) {
                HashMap<String, Int>()
            } else {
                Gson().fromJson(
                    memory.getString("FileAttachmentEPCTable", ""),
                    epcTable.javaClass
                )
            }

        epcTablePreviousSize = epcTable.size

        barcodeTable =
            if (memory.getString("FileAttachmentBarcodeTable", "").isNullOrEmpty()) {
                ArrayList<String>()
            } else {
                Gson().fromJson(
                    memory.getString(
                        "FileAttachmentBarcodeTable",
                        ""
                    ),
                    barcodeTable.javaClass
                )
            }

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        if (intent.action == Intent.ACTION_SEND) {
            if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" == intent.type
                || "application/vnd.ms-excel" == intent.type
            ) {

                if (memory.getString("username", "empty") != "") {

                    MainActivity.username = memory.getString("username", "empty")!!
                    MainActivity.token = memory.getString("accessToken", "empty")!!
                } else {
                    Toast.makeText(
                        this,
                        "لطفا ابتدا به حساب کاربری خود وارد شوید",
                        Toast.LENGTH_LONG
                    ).show()
                }

                while (!rf.init()) {
                    rf.free()
                }
                if (Build.MODEL == "EXARKXK650") {
                    while (!rf.setFrequencyMode(0x08)) {
                        rf.free()
                    }
                } else if (Build.MODEL == "c72") {
                    while (!rf.setFrequencyMode(0x04)) {
                        rf.free()
                    }
                }
                while (!rf.setRFLink(2)) {
                    rf.free()
                }
                while (!rf.setEPCMode()) {
                }

                val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri

                if (intent.type == "application/vnd.ms-excel") {
                    readXLSFile(uri)
                } else if (intent.type == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") {
                    readXLSXFile(uri)
                }

            } else {
                while (!rf.setEPCMode()) {
                }
                Toast.makeText(this, "فرمت فایل باید اکسل باشد", Toast.LENGTH_LONG).show()
            }
        } else {
            while (!rf.setEPCMode()) {
            }
        }

        syncScannedItemsToServer()
        resultLists = comparison(fileJsonArray, scannedJsonArray)
        refreshUI(0)

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == 280 || keyCode == 293) {

            if (barcodeIsEnabled) {
                isScanning = false // cause scanning routine loop to stop (if running)
                start()
            } else {
                if (!isScanning) {

                    CoroutineScope(IO).launch {
                        scanningRoutine()
                    }

                } else {
                    isScanning = false // cause scanning routine loop to stop
                    syncScannedItemsToServer()
                }
            }
        } else if (keyCode == 4) {
            back()
        } else if (keyCode == 139) {
            isScanning = false // cause scanning routine loop to stop (if running)
        }
        return true
    }

    private suspend fun scanningRoutine() {

        while (!rf.setPower(rfPower)) {
        }
        rf.startInventoryTag(0, 0, 0)
        isScanning = true

        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null && uhfTagInfo.epc.startsWith("30")) {
                    epcTable[uhfTagInfo.epc] = 1
                } else {
                    break
                }
            }
            refreshUI(epcTable.size - epcTablePreviousSize)

            epcTablePreviousSize = epcTable.size

            delay(1000)
        }

        rf.stopInventory()

        refreshUI(0)
    }

    private fun syncScannedItemsToServer() {

        if ((epcTable.size + barcodeTable.size) == 0) {
            Toast.makeText(this, "کالایی جهت بررسی وجود ندارد", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val response = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")
            val similarIndexes = arrayListOf<Int>()

            for (i in 0 until barcodes.length()) {

                for (j in 0 until response.length()) {

                    if (barcodes.getJSONObject(i).getString("KBarCode") == response.getJSONObject(j)
                            .getString("KBarCode")
                    ) {
                        response.getJSONObject(j).put(
                            "handheldCount",
                            response.getJSONObject(j)
                                .getInt("handheldCount") + barcodes.getJSONObject(i)
                                .getInt("handheldCount")
                        )
                        similarIndexes.add(i)
                        break
                    }
                }
            }
            for (i in 0 until barcodes.length()) {

                if (!similarIndexes.contains(i)) {
                    response.put(it.getJSONArray("KBarCodes")[i])
                }
            }

            scannedJsonArray = response
            resultLists = comparison(fileJsonArray, scannedJsonArray)

            refreshUI(0)

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
                for ((key) in epcTable) {
                    epcArray.put(key)
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

        val sheet = workbook.getSheet("Amar")
        if (sheet == null) {
            Toast.makeText(this, "فایل خالی است", Toast.LENGTH_LONG).show()
            return
        }

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {

                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    excelBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            fileJsonArray = it.getJSONArray("KBarCodes")

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

        val sheet = workbook.getSheet("Amar")
        if (sheet == null) {
            Toast.makeText(this, "فایل خالی است", Toast.LENGTH_LONG).show()
            return
        }

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {

                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    excelBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            fileJsonArray = it.getJSONArray("KBarCodes")

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
            refreshUI(0)
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            syncScannedItemsToServer()
        }
    }

    data class ConflictLists(
        var additional: JSONArray = JSONArray(),
        var shortage: JSONArray = JSONArray(),
        var all: JSONArray = JSONArray(),
        var matched: JSONArray = JSONArray(),
    )

    private fun comparison(fileJSONArray: JSONArray, scannedJSONArray: JSONArray): ConflictLists {

        val result = ConflictLists()

        var difference: Int

        val similarIndexesInScannedArray = arrayListOf<Int>()
        val similarIndexesInFileArray = arrayListOf<Int>()

        for (i in 0 until scannedJSONArray.length()) {

            val scannedJSONObject = scannedJSONArray.getJSONObject(i)

            for (j in 0 until fileJSONArray.length()) {

                val fileJSONObject = fileJSONArray.getJSONObject(j)

                if (scannedJSONObject.getString("KBarCode") == fileJSONObject.getString("KBarCode")
                ) {
                    difference =
                        scannedJSONObject.getInt("handheldCount") - fileJSONObject.getInt("handheldCount")
                    when {
                        difference > 0 -> {
                            scannedJSONObject.put(
                                "numberWithExplanation",
                                "تعداد اضافی: " + difference
                            )
                            scannedJSONObject.put("number", difference)
                            result.additional.put(scannedJSONObject)
                            result.all.put(scannedJSONObject)
                        }
                        difference < 0 -> {
                            scannedJSONObject.put(
                                "numberWithExplanation",
                                "تعداد کسری: " + -difference
                            )
                            scannedJSONObject.put("number", -difference)
                            result.shortage.put(scannedJSONObject)
                            result.all.put(scannedJSONObject)
                        }
                        else -> {
                            scannedJSONObject.put(
                                "numberWithExplanation",
                                "تعداد تایید شده: " + scannedJSONObject.getInt("handheldCount")
                            )
                            scannedJSONObject.put(
                                "number",
                                scannedJSONObject.getInt("handheldCount")
                            )
                            result.matched.put(scannedJSONObject)
                            result.all.put(scannedJSONObject)
                        }
                    }
                    similarIndexesInScannedArray.add(i)
                    similarIndexesInFileArray.add(j)
                    break
                }
            }
        }

        for (i in 0 until fileJSONArray.length()) {
            if (!similarIndexesInFileArray.contains(i)) {
                val template = fileJSONArray.getJSONObject(i)
                template.put(
                    "numberWithExplanation", "تعداد کسری: " + template.getInt("handheldCount")
                )
                template.put("number", template.getInt("handheldCount"))
                result.shortage.put(template)
                result.all.put(template)
            }
        }

        for (i in 0 until scannedJSONArray.length()) {
            if (!similarIndexesInScannedArray.contains(i)) {
                val template = scannedJSONArray.getJSONObject(i)
                template.put(
                    "numberWithExplanation",
                    "تعداد اضافی: " + template.getInt("handheldCount")
                )
                template.put("number", template.getInt("handheldCount"))
                result.additional.put(template)
                result.all.put(template)
            }
        }

        additionalNumber = 0
        for (i in 0 until result.additional.length()) {
            val template = result.additional.getJSONObject(i)
            additionalNumber += template.getInt("number")
        }

        shortageNumber = 0
        for (i in 0 until result.shortage.length()) {
            val template = result.shortage.getJSONObject(i)
            shortageNumber += template.getInt("number")
        }


        matchedNumber = 0
        for (i in 0 until result.matched.length()) {
            val template = result.matched.getJSONObject(i)
            matchedNumber += template.getInt("number")
        }

        return result
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        val epcJson = JSONObject(epcTable as Map<*, *>)
        edit.putString("FileAttachmentEPCTable", epcJson.toString())
        val barcodesJson = JSONArray(barcodeTable)
        edit.putString("FileAttachmentBarcodeTable", barcodesJson.toString())
        edit.apply()
    }

    private fun clear() {
        barcodeTable.clear()
        epcTable.clear()
        epcTablePreviousSize = 0
        fileJsonArray = JSONArray()

        shortageNumber = 0
        additionalNumber = 0
        matchedNumber = 0
        resultLists = ConflictLists()
        number = 0
        filter = 0
        fileName = "خروجی"
        openDialog = false

        resultLists =
            comparison(fileJSONArray = fileJsonArray, scannedJSONArray = JSONArray())
        saveToMemory()
        refreshUI(0)
    }

    private fun start() {

        barcode2D.startScan(this)
    }

    private fun open() {
        barcode2D.open(this, this)
    }

    private fun close() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
    }

    private fun back() {

        saveToMemory()
        isScanning = false // cause scanning routine loop to stop
        close()
        finish()
    }

    private fun refreshUI(speed: Int) {

        number = epcTable.size + barcodeTable.size

        if (isScanning) {
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
        }
    }

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("AMAR")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        val headerCell = headerRow.createCell(0)
        headerCell.setCellValue("کد جست و جو")
        val headerCell2 = headerRow.createCell(1)
        headerCell2.setCellValue("تعداد")
        val headerCell3 = headerRow.createCell(2)
        headerCell3.setCellValue("کسری")
        val headerCell4 = headerRow.createCell(3)
        headerCell4.setCellValue("اضافی")

        for (i in 0 until resultLists.matched.length()) {
            val template = resultLists.matched.getJSONObject(i)
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            val cell = row.createCell(0)
            cell.setCellValue(template.getString("KBarCode"))
            val cell2 = row.createCell(1)
            cell2.setCellValue(template.getInt("handheldCount").toDouble())
        }

        for (i in 0 until resultLists.shortage.length()) {
            val template = resultLists.shortage.getJSONObject(i)
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            val cell = row.createCell(0)
            cell.setCellValue(template.getString("KBarCode"))
            val cell2 = row.createCell(1)
            cell2.setCellValue(template.getInt("handheldCount").toDouble())
            val cell3 = row.createCell(2)
            cell3.setCellValue(template.getInt("number").toDouble())
        }

        for (i in 0 until resultLists.additional.length()) {
            val template = resultLists.additional.getJSONObject(i)
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            val cell = row.createCell(0)
            cell.setCellValue(template.getString("KBarCode"))
            val cell2 = row.createCell(1)
            cell2.setCellValue(template.getInt("handheldCount").toDouble())
            val cell3 = row.createCell(3)
            cell3.setCellValue(template.getInt("number").toDouble())
        }

        var dir = File(Environment.getExternalStorageDirectory(), "/RFID")
        dir.mkdir()
        dir = File(Environment.getExternalStorageDirectory(), "/RFID/خروجی/")
        dir.mkdir()

        val outFile = File(dir, fileName + ".xlsx")

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
        Toast.makeText(this, "فایل در مسیر " + "/RFID/خروجی" + " " + "ذخیره شد", Toast.LENGTH_LONG)
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = "تعداد اسکن شده: " + number,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(horizontal = 8.dp),
                    )

                    DropDownList()

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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = "کسری: " + shortageNumber,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                    Text(
                        text = "اضافی: " + additionalNumber,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                    Text(
                        text = "تایید شده: " + matchedNumber,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = "کد کسری: " + resultLists.shortage.length(),
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                    Text(
                        text = "کد اضافی: " + resultLists.additional.length(),
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                    )
                    Text(
                        text = "کد تایید شده: " + resultLists.matched.length(),
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
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

                val jsonArray = when (filter) {
                    0 -> resultLists.all
                    1 -> resultLists.matched
                    2 -> resultLists.additional
                    else -> resultLists.shortage
                }

                items(jsonArray.length()) { i ->
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
                                text = jsonArray.getJSONObject(i)
                                    .getString("productName"),
                                fontSize = 20.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Brown)
                            )

                            Text(
                                text = jsonArray.getJSONObject(i)
                                    .getString("KBarCode"),
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.DarkGreen)
                            )

                            Text(
                                text = jsonArray.getJSONObject(i)
                                    .getString("numberWithExplanation"),
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Goldenrod)
                            )
                        }

                        Image(
                            painter = rememberImagePainter(
                                jsonArray.getJSONObject(i)
                                    .getString("ImgUrl"),
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
    fun DropDownList() {

        val list = arrayListOf("همه", "تایید شده", "اضافی", "کسری")
        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = list[filter])
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                DropdownMenuItem(onClick = {
                    filter = 0
                    expanded = false
                }) {
                    Text(text = list[0])
                }
                DropdownMenuItem(onClick = {
                    filter = 1
                    expanded = false
                }) {
                    Text(text = list[1])
                }
                DropdownMenuItem(onClick = {
                    filter = 2
                    expanded = false
                }) {
                    Text(text = list[2])
                }
                DropdownMenuItem(onClick = {
                    filter = 3
                    expanded = false
                }) {
                    Text(text = list[3])
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
        matchedNumber = 0
        resultLists = ConflictLists()
        number = 0
        filter = 0
        fileName = "خروجی"
        openDialog = false

        Page()
    }
}