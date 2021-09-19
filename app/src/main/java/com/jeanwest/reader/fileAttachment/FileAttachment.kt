package com.jeanwest.reader.fileAttachment

import android.content.Context
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
import androidx.core.content.FileProvider
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
    var rfPower = 30
    var epcTable: MutableMap<String, Int> = HashMap()
    var epcTablePreviousSize = 0
    var barcodeTable = ArrayList<String>()
    private val barcode2D = Barcode2D(this)
    private var barcodeIsEnabled = false
    var uiParameters = UIParameters()

    private val apiTimeout = 20000
    private var fileJsonArray = JSONArray()

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
        clear()
        isScanning = false
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
                    syncToServer()
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

    private fun syncToServer() {
        val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            var template: JSONObject
            val response = it.getJSONArray("epcs")
            for (i in 0 until it.getJSONArray("KBarCodes").length()) {
                response.put(it.getJSONArray("KBarCodes")[i])
            }

            uiParameters = UIParameters()

            uiParameters.sum = 0
            for (i in 0 until response.length()) {
                uiParameters.sum += response.getJSONObject(i).getInt("handheldCount")
            }

            var difference = 0
            val fileJSONArrayCopy = fileJsonArray

            for (i in 0 until response.length()) {
                template = response.getJSONObject(i)
                for (j in 0 until fileJSONArrayCopy.length()) {
                    if (template.getString("KBarCode") == fileJSONArrayCopy.getJSONObject(j).getString("KBarCode")) {
                        difference = template.getInt("handheldCount") - fileJSONArrayCopy.getJSONObject(j).getInt("handheldCount")
                        if (difference > 0) {
                            uiParameters.titles[i] = (template.getString("productName"))
                            uiParameters.size[i] = ("اندازه: " + template.getString("Size"))
                            uiParameters.color[i] = ("رنگ: " + template.getString("Color"))
                            uiParameters.scannedNumber[i] = ("تعداد: " + difference)
                            uiParameters.pictureURL[i] = (template.getString("ImgUrl"))
                            uiParameters.specs[i] = ("کد: " + template.getString("KBarCode") + "\n" + "اضافی")
                        } else if (difference < 0) {
                            uiParameters.titles[i] = (template.getString("productName"))
                            uiParameters.size[i] = ("اندازه: " + template.getString("Size"))
                            uiParameters.color[i] = ("رنگ: " + template.getString("Color"))
                            uiParameters.scannedNumber[i] = ("تعداد: " + -difference)
                            uiParameters.pictureURL[i] = (template.getString("ImgUrl"))
                            uiParameters.specs[i] = ("کد: " + template.getString("KBarCode") + "\n" + "کسری")
                        }
                        response.remove(i)
                        fileJSONArrayCopy.remove(i)
                    }
                }
            }

            for (i in 0 until fileJSONArrayCopy.length()) {
                template = fileJSONArrayCopy.getJSONObject(i)
                uiParameters.titles.add(template.getString("productName"))
                uiParameters.size.add("اندازه: " + template.getString("Size"))
                uiParameters.color.add("رنگ: " + template.getString("Color"))
                uiParameters.scannedNumber.add("تعداد: " + template.getString("handheldCount"))
                uiParameters.pictureURL.add(template.getString("ImgUrl"))
                uiParameters.specs.add("کد: " + template.getString("KBarCode") + "\n" + "کسری")
            }

            for (i in 0 until response.length()) {
                template = response.getJSONObject(i)
                uiParameters.titles.add(template.getString("productName"))
                uiParameters.size.add("اندازه: " + template.getString("Size"))
                uiParameters.color.add("رنگ: " + template.getString("Color"))
                uiParameters.scannedNumber.add("تعداد: " + template.getString("handheldCount"))
                uiParameters.pictureURL.add(template.getString("ImgUrl"))
                uiParameters.specs.add("کد: " + template.getString("KBarCode") + "\n" + "اضافی")
            }

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
            if (sheet.getRow(i).getCell(1) == null) {
                break
            } else {
                excelBarcodes.add(sheet.getRow(i).getCell(1).stringCellValue)
            }
        }
        Toast.makeText(this, excelBarcodes.toString(), Toast.LENGTH_LONG).show()

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

        uiParameters.text = "سرعت اسکن (تگ بر ثانیه): " + speed + "\n"
        uiParameters.text += "تعداد کالا های پیدا شده: " + (epcTable.size + barcodeTable.size) + "\n"

        if (!isScanning) {
            uiParameters.text += "مجموع اجناس: " + uiParameters.sum + "\n"
            uiParameters.text += "تعداد تگ های خراب: " + (epcTable.size + barcodeTable.size - uiParameters.sum)
        } else {

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
            syncToServer()
        }
    }

    private fun clear() {
        barcodeTable.clear()
        epcTable.clear()
        epcTablePreviousSize = 0
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
    }

    //for decoding items JSON
    data class UIParameters(
            val titles: ArrayList<String> = ArrayList(),
            val specs: ArrayList<String> = ArrayList(),
            val size: ArrayList<String> = ArrayList(),
            val color: ArrayList<String> = ArrayList(),
            val scannedNumber: ArrayList<String> = ArrayList(),
            val pictureURL: ArrayList<String> = ArrayList(),
            var sum: Int = 0,
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
                    IconButton(onClick = {  }) {
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

                Row() {

                    Text(
                            text = "بارکد",
                            modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .align(Alignment.CenterVertically),
                    )

                    Switch(
                            checked = switchValue.value,
                            onCheckedChange = {
                                barcodeIsEnabled = it
                                switchValue.value = it
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                Text(
                        text = uiParameters.text,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                                .padding(top = 10.dp, start = 8.dp)
                                .fillMaxWidth(),
                )

                LazyColumn(modifier = Modifier.padding(top = 10.dp)) {

                    items(uiParameters.titles.size) { i ->
                        Row(horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                        .padding(2.dp)
                                        .background(MaterialTheme.colors.onPrimary)
                                        .fillMaxWidth()
                        ) {
                            Column {
                                Text(text = uiParameters.titles[i],
                                        fontSize = 20.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier)
                                Text(text = uiParameters.specs[i],
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier)
                                Text(text = uiParameters.size[i],
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier,
                                        color = colorResource(id = R.color.DarkGreen)
                                )
                                Text(text = uiParameters.color[i],
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier,
                                        color = colorResource(id = R.color.Brown)
                                )
                                Text(text = uiParameters.scannedNumber[i],
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = modifier,
                                        color = colorResource(id = R.color.Goldenrod)
                                )
                            }

                            Image(
                                    painter = rememberImagePainter(uiParameters.pictureURL[i]),
                                    contentDescription = "",
                                    modifier = Modifier
                                            .height(200.dp)
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