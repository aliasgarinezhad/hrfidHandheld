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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import androidx.preference.PreferenceManager
import coil.compose.rememberImagePainter
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class FileAttachment : ComponentActivity(), IBarcodeResult {

    private var isScanning = false
    lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    var epcTable: MutableMap<String, Int> = HashMap()
    var epcTablePreviousSize = 0
    var barcodeTable = ArrayList<String>()
    private val barcode2D = Barcode2D(this)
    private var barcodeIsEnabled = false
    var uiParameters = UIParameters()

    private val apiTimeout = 20000
    private var fileJsonArray = JSONArray()
    private var scannedJsonArray = JSONArray()
    private var conflictJsonObject = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        if (intent.action == Intent.ACTION_SEND) {
            if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" == intent.type) {

                val memory = PreferenceManager.getDefaultSharedPreferences(this)
                if (memory.getString("username", "empty") != "") {

                    MainActivity.username = memory.getString("username", "empty")!!
                    MainActivity.token = memory.getString("accessToken", "empty")!!
                } else {
                    Toast.makeText(this, "لطفا ابتدا به حساب کاربری خود وارد شوید", Toast.LENGTH_LONG).show()
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
                val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri
                readFile(uri)

            } else {

                Toast.makeText(this, "فرمت فایل باید اکسل باشد", Toast.LENGTH_LONG).show()
                return
            }
        }

        while (!rf.setEPCMode()) {
        }
        conflictJsonObject = comparison(fileJsonArray, scannedJsonArray)
        refreshUI(0)
        open()
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
            withContext(Main) {
                refreshUI(epcTable.size - epcTablePreviousSize)
            }
            epcTablePreviousSize = epcTable.size

            delay(1000)
        }

        rf.stopInventory()
        withContext(Main) {
            refreshUI(0)
        }
    }

    private fun syncScannedItemsToServer() {
        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val response = it.getJSONArray("epcs")
            for (i in 0 until it.getJSONArray("KBarCodes").length()) {
                response.put(it.getJSONArray("KBarCodes")[i])
            }
            scannedJsonArray = response
            conflictJsonObject = comparison(fileJsonArray, scannedJsonArray)

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

    private fun readFile(uri: Uri) {

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
            if (sheet.getRow(i).getCell(0) == null) {
                break
            } else {
                excelBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
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

    private fun refreshUI(speed: Int) {

        uiParameters.text = "تعداد اسکن شده: " + (epcTable.size + barcodeTable.size)

        if (isScanning) {
            when {
                speed > 100 -> {
                    uiParameters.beep.startTone(ToneGenerator.TONE_CDMA_PIP, 700)
                }
                speed > 30 -> {
                    uiParameters.beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                }
                speed > 10 -> {
                    uiParameters.beep.startTone(ToneGenerator.TONE_CDMA_PIP, 300)
                }
                speed > 0 -> {
                    uiParameters.beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                }
            }
        }
        setContent { Page() }
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            barcodeTable.add(barcode)
            refreshUI(0)
            uiParameters.beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            syncScannedItemsToServer()
        }
    }

    private fun comparison(fileJSONArray: JSONArray, scannedJSONArray: JSONArray): JSONObject {

        val result = JSONObject()
        val matched = JSONArray()
        val shortage = JSONArray()
        val additional = JSONArray()

        var template: JSONObject
        uiParameters = UIParameters()

        var difference = 0
        val fileJSONArrayCopy = JSONArray()
        val responseCopy = JSONArray()

        for (i in 0 until fileJSONArray.length()) {
            fileJSONArrayCopy.put(fileJSONArray.getJSONObject(i))
        }
        for (i in 0 until scannedJSONArray.length()) {
            responseCopy.put(scannedJSONArray.getJSONObject(i))
        }

        for (i in 0 until scannedJSONArray.length()) {
            template = scannedJSONArray.getJSONObject(i)
            for (j in 0 until fileJSONArray.length()) {
                if (template.getString("KBarCode") == fileJSONArray.getJSONObject(j).getString("KBarCode")) {
                    difference = template.getInt("handheldCount") - fileJSONArray.getJSONObject(j).getInt("handheldCount")
                    when {
                        difference > 0 -> {
                            template.put("additionalNumber", difference)
                            additional.put(template)
                        }
                        difference < 0 -> {
                            template.put("shortageNumber", difference)
                            shortage.put(template)
                        }
                        else -> {
                            template.put("matchedNumber", template.getInt("handheldCount"))
                            matched.put(template)
                        }
                    }
                    responseCopy.remove(i)
                    fileJSONArrayCopy.remove(j)
                }
            }
        }

        for (i in 0 until fileJSONArrayCopy.length()) {
            template = fileJSONArrayCopy.getJSONObject(i)
            template.put("shortageNumber", template.getInt("handheldCount"))
            shortage.put(template)
        }

        for (i in 0 until responseCopy.length()) {
            template = scannedJSONArray.getJSONObject(i)
            template.put("additionalNumber", template.getInt("handheldCount"))
            additional.put(template)
        }
        result.put("additional", additional)
        result.put("shortage", shortage)
        result.put("matched", matched)
        return result
    }

    private fun clear() {
        barcodeTable.clear()
        epcTable.clear()
        epcTablePreviousSize = 0
        conflictJsonObject = comparison(fileJSONArray = fileJsonArray, scannedJSONArray = JSONArray())
        fileJsonArray = JSONArray()
        uiParameters = UIParameters()
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
        isScanning = false // cause scanning routine loop to stop
        close()
        finish()
    }

    private fun exportFile() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("AMAR")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        val headerCell = headerRow.createCell(0)
        headerCell.setCellValue("کد جست و جو")
        val headerCell2 = headerRow.createCell(1)
        headerCell2.setCellValue("تعداد")

        barcodeTable.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            val cell = row.createCell(0)
            cell.setCellValue(it)
            val cell2 = row.createCell(1)
            cell2.setCellValue(1.0)
        }

        epcTable.keys.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            val cell = row.createCell(0)
            cell.setCellValue(it)
            val cell2 = row.createCell(1)
            cell2.setCellValue(1.0)
        }
        val outFile = File(Environment.getExternalStorageDirectory(), "output.xlsx")
        val outputStream = FileOutputStream(outFile.absolutePath)
        workbook.write(outputStream)
        outputStream.flush()
        outputStream.close()

        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(outFile.absolutePath))
        shareIntent.type = "application/octet-stream"
        applicationContext.startActivity(shareIntent)
        Toast.makeText(this, "فایل در روت حافظه ذخیره شد", Toast.LENGTH_LONG).show()
    }

    data class UIParameters(
            var filter: Int = 2,
            var text: String = "",
            var beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100),
    )

    @Composable
    fun Page() {
        MyApplicationTheme() {
            Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
            )
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
                    IconButton(onClick = { exportFile() }) {
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

        val slideValue = rememberSaveable { mutableStateOf(30F) }
        val switchValue = rememberSaveable { mutableStateOf(false) }
        val modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .wrapContentWidth()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

            Column {

                Column(modifier = Modifier
                        .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                        .background(MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(10.dp))
                        .fillMaxWidth()) {

                    Row() {

                        Text(
                                text = "اندازه توان(" + slideValue.value.toInt() + ")",
                                modifier = Modifier
                                        .wrapContentSize()
                                        .padding(start = 8.dp, end = 8.dp)
                                        .align(Alignment.CenterVertically),
                                textAlign = TextAlign.Center
                        )

                        Slider(
                                value = slideValue.value,
                                onValueChange = {
                                    slideValue.value = it
                                    rfPower = it.toInt()
                                },
                                enabled = true,
                                valueRange = 5f..30f,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {

                        Text(
                                text = uiParameters.text,
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
                                    checked = switchValue.value,
                                    onCheckedChange = {
                                        barcodeIsEnabled = it
                                        switchValue.value = it
                                    },
                                    modifier = Modifier.padding(end = 4.dp, bottom = 10.dp),
                            )
                        }
                    }

                    if (isScanning) {
                        Row(modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colors.primary)
                        }
                    }
                }

                LazyColumn(modifier = Modifier.padding(top = 2.dp)) {

                    items(when (uiParameters.filter) {
                        0 -> conflictJsonObject.getJSONArray("matched").length()
                        1 -> conflictJsonObject.getJSONArray("additional").length()
                        2 -> conflictJsonObject.getJSONArray("shortage").length()
                        else -> 0
                    }) { i ->
                        Row(horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                        .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                                        .background(MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(10.dp))
                                        .fillMaxWidth()
                        ) {
                            Column {
                                Text(text = when (uiParameters.filter) {
                                    0 -> conflictJsonObject.getJSONArray("matched").getJSONObject(i).getString("productName")
                                    1 -> conflictJsonObject.getJSONArray("additional").getJSONObject(i).getString("productName")
                                    2 -> conflictJsonObject.getJSONArray("shortage").getJSONObject(i).getString("productName")
                                    else -> ""
                                },
                                        fontSize = 20.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier,
                                        color = colorResource(id = R.color.Brown))

                                Text(text = when (uiParameters.filter) {
                                    0 -> conflictJsonObject.getJSONArray("matched").getJSONObject(i).getString("KBarCode")
                                    1 -> conflictJsonObject.getJSONArray("additional").getJSONObject(i).getString("KBarCode")
                                    2 -> conflictJsonObject.getJSONArray("shortage").getJSONObject(i).getString("KBarCode")
                                    else -> ""
                                },
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier,
                                        color = colorResource(id = R.color.DarkGreen)
                                )

                                Text(text = when (uiParameters.filter) {
                                    0 -> conflictJsonObject.getJSONArray("matched").getJSONObject(i).getString("matchedNumber")
                                    1 -> conflictJsonObject.getJSONArray("additional").getJSONObject(i).getString("additionalNumber")
                                    2 -> conflictJsonObject.getJSONArray("shortage").getJSONObject(i).getString("shortageNumber")
                                    else -> ""
                                },
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier,
                                        color = colorResource(id = R.color.Goldenrod))
                            }

                            Image(
                                    painter = rememberImagePainter(when (uiParameters.filter) {
                                        0 -> conflictJsonObject.getJSONArray("matched").getJSONObject(i).getString("ImgUrl")
                                        1 -> conflictJsonObject.getJSONArray("additional").getJSONObject(i).getString("ImgUrl")
                                        2 -> conflictJsonObject.getJSONArray("shortage").getJSONObject(i).getString("ImgUrl")
                                        else -> ""
                                    }),
                                    contentDescription = "",
                                    modifier = Modifier
                                            .height(128.dp)
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            )

                        }
                    }
                }
            }
        }
    }

    @Preview
    @Composable
    fun Preview() {
        Page()
    }
}