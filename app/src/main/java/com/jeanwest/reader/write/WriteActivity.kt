package com.jeanwest.reader.write

//import com.jeanwest.reader.testClasses.Barcode2D
//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.JalaliDate.JalaliDate
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.iotHub.IotHub
import com.jeanwest.reader.setRFEpcAndTidMode
import com.jeanwest.reader.setRFPower
import com.jeanwest.reader.theme.CustomSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.theme.doneColor
import com.jeanwest.reader.theme.errorColor
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.IUHF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.android.volley.toolbox.JsonObjectRequest as JsonObjectRequest1

class WriteActivity : ComponentActivity(), IBarcodeResult {

    private var deviceSerialNumber = ""
    private var barcode2D = Barcode2D(this)
    private lateinit var rf: RFIDWithUHFUART
    private var oldMethodTid = ""
    private var newMethodTid = ""
    private var barcodeID = ""
    private var barcodeTable = mutableListOf<String>()
    private lateinit var iotHubService: IotHub

    private var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var counter by mutableStateOf(0)
    private var numberOfWrittenRfTags by mutableStateOf(0L)
    private var result by mutableStateOf("")
    private var openClearDialog by mutableStateOf(false)
    private var openFileDialog by mutableStateOf(false)
    private var openRewriteDialog by mutableStateOf(false)
    private var barcodeIsScanning by mutableStateOf(false)
    private var rfIsScanning by mutableStateOf(false)
    private var resultColor by mutableStateOf(Color.White)
    private var fileName by mutableStateOf("خروجی")
    private var writeRecords = mutableListOf<WriteRecord>()
    private var userWriteRecords = mutableListOf<WriteRecord>()
    private var barcodeInformation = JSONObject()
    private val tagTypeValues = mutableListOf("تگ کیوآر کد دار", "تگ سفید")
    var tagTypeValue by mutableStateOf("تگ سفید")
    private var state = SnackbarHostState()

    private var write = false

    private var writeTagNoQRCodeRfPower = 5

    private var counterMaxValue = 0L
    private var counterMinValue = 0L
    private var tagPassword = "00000000"
    private var counterValue = 0L
    private var filterNumber = 0 // 3bit
    private var partitionNumber = 0 // 3bit
    private var headerNumber = 48 // 8bit
    private var companyNumber = 101 // 12bit

    private var iotHubConnected = false
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as IotHub.LocalBinder
            iotHubService = binder.service
            iotHubConnected = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            iotHubConnected = false
        }
    }

    override fun onResume() {

        super.onResume()

        val util = JalaliDate()
        fileName = util.currentShamsidate

        barcodeInit()

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcAndTidMode(rf, state)

        setContent {
            Page()
        }

        loadMemory()

        val intent = Intent(this, IotHub::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun exportFile() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("کالا های رایت شده")
        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("بارکد کالا")
        headerRow.createCell(1).setCellValue("EPC")
        headerRow.createCell(2).setCellValue("تاریخ")
        headerRow.createCell(3).setCellValue("کاربر")
        headerRow.createCell(4).setCellValue("شماره سریال دستگاه")
        headerRow.createCell(5).setCellValue("روی تگ خام رایت شده است")

        val dir = File(this.getExternalFilesDir(null), "/")
        val outFile = File(dir, "$fileName.xlsx")

        userWriteRecords.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.barcode)
            row.createCell(1).setCellValue(it.epc)
            row.createCell(2).setCellValue(it.dateAndTime)
            row.createCell(3).setCellValue(it.username)
            row.createCell(4).setCellValue(it.deviceSerialNumber)
            row.createCell(5).setCellValue(it.wroteOnRawTag)

            val outputStream = FileOutputStream(outFile.absolutePath)
            workbook.write(outputStream)
            outputStream.flush()
            outputStream.close()
        }

        val uri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName + ".provider",
            outFile
        )

        if (iotHubConnected) {
            if (iotHubService.sendLogFile(writeRecords)) {
                writeRecords.clear()
                saveMemory()
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "application/octet-stream"
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(shareIntent)
    }

    private fun loadMemory() {
        val type = object : TypeToken<List<WriteRecord>>() {}.type

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        if (memory.getLong("value", -1L) != -1L) {

            deviceSerialNumber = memory.getString("deviceSerialNumber", "") ?: ""
            counterValue = memory.getLong("value", -1L)
            counterMaxValue = memory.getLong("max", -1L)
            counterMinValue = memory.getLong("min", -1L)
            headerNumber = memory.getInt("header", -1)
            filterNumber = memory.getInt("filter", -1)
            partitionNumber = memory.getInt("partition", -1)
            companyNumber = memory.getInt("company", -1)
            tagPassword = memory.getString("password", "") ?: "00000000"

            userWriteRecords = Gson().fromJson(
                memory.getString("WriteActivityUserWriteRecords", ""),
                type
            ) ?: mutableListOf()
            writeRecords = Gson().fromJson(
                memory.getString("WriteActivityWriteRecords", ""),
                type
            ) ?: mutableListOf()

            counter = userWriteRecords.size
        }

        numberOfWrittenRfTags = counterValue - counterMinValue
    }

    private fun saveMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()
        memoryEditor.putLong("value", counterValue)

        memoryEditor.putString(
            "WriteActivityUserWriteRecords",
            Gson().toJson(userWriteRecords).toString()
        )
        memoryEditor.putString("WriteActivityWriteRecords", Gson().toJson(writeRecords).toString())

        memoryEditor.apply()
    }

    private fun back() {
        unbindService(serviceConnection)
        stopBarcodeScan()
        write = false
        if (barcodeIsScanning || rfIsScanning) {
            barcodeIsScanning = false
            rf.stopInventory()
        }
        finish()
    }

    override fun onPause() {
        super.onPause()
        stopBarcodeScan()
        write = false
        if (barcodeIsScanning || rfIsScanning) {
            barcodeIsScanning = false
            rf.stopInventory()
        }
    }

    @Throws(InterruptedException::class)
    override fun getBarcode(barcode: String?) {

        if (!barcode.isNullOrEmpty()) {
            if (!write) {
                barcodeIsScanning = false
                barcodeID = barcode
                result = "$barcodeID\n"
                resultColor = doneColor
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                write = true
            } else if (tagTypeValue == "تگ کیوآر کد دار" && write) {
                barcodeIsScanning = false
                newMethodTid = barcode
                writeTagByQRCode(barcodeID)
                write = false
            }
        } else {
            barcodeIsScanning = false
            result = "بارکدی پیدا نشد. لطفا لیزر اسکنر را روبروی بارکد قرار دهید."
            resultColor = errorColor
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
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

    private fun clear() {
        userWriteRecords.clear()
        counter = userWriteRecords.size
        saveMemory()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {

            if (event.repeatCount != 0) {
                return true
            }
            if (counterValue >= counterMaxValue) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "مجوز رایت وجود ندارد یا به پایان رسیده است. برای دریافت مجوز با پشتیبانی تماس بگیرید.",
                        null,
                        SnackbarDuration.Long
                    )
                }
                return true
            }

            if (barcodeIsScanning) {
                return true
            }
            if (write) {
                if (tagTypeValue == "تگ کیوآر کد دار") {
                    barcodeIsScanning = true
                    startBarcodeScan()
                } else {
                    writeTagNoQRCode(barcodeID)
                    write = false
                }
            } else {
                barcodeIsScanning = true
                startBarcodeScan()
            }
        } else if (keyCode == 4) {
            back()
        }
        return true
    }

    private fun rfWrite(productEPC: String, tid: String): Boolean {

        for (k in 0..15) {
            if (rf.writeData(
                    "00000000",
                    IUHF.Bank_TID,
                    0,
                    96,
                    tid,
                    IUHF.Bank_EPC,
                    2,
                    6,
                    productEPC
                )
            ) {
                return true
            }
        }
        return false
    }

    private fun rfWriteVerify(productEPC: String, tid: String): Boolean {

        for (k in 0..15) {

            rf.readData("00000000", IUHF.Bank_TID, 0, 96, tid, IUHF.Bank_EPC, 2, 6)?.let {
                if (productEPC == it.lowercase()) {
                    return true
                }
            }
        }
        return false
    }

    private fun write(barcodeInformation: JSONObject, writeOnRawTag: Boolean, tid: String) {

        if (!setRFPower(state, rf, 30)) {
            return
        }

        val itemNumber = barcodeInformation.getString("RFID").toLong()
        val serialNumber = counterValue

        val productEPC = epcGenerator(
            headerNumber,
            filterNumber,
            partitionNumber,
            companyNumber,
            itemNumber,
            serialNumber
        )

        if (!rfWrite(productEPC, tid)) {
            result += "تگ رایت نشده است. لطفا دوباره امتحان کنید"
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            resultColor = errorColor
            return
        }

        if (!rfWriteVerify(productEPC, tid)) {
            result += "تگ رایت نشده است. لطفا دوباره امتحان کنید"
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            resultColor = errorColor
            return
        }

        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        resultColor = doneColor

        result += "تگ با موفقیت رایت شد" + "\n"
        result += "سریال جدید: $productEPC"

        counterValue++
        barcodeTable.add(barcodeInformation.getString("KBarCode"))

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
        val writeRecord = WriteRecord(
            barcode = barcodeInformation.getString("KBarCode"),
            epc = productEPC,
            dateAndTime = sdf.format(Date()),
            username = MainActivity.username,
            deviceSerialNumber = deviceSerialNumber,
            wroteOnRawTag = writeOnRawTag
        )
        writeRecords.add(writeRecord)
        userWriteRecords.add(writeRecord)

        counter = userWriteRecords.size
        numberOfWrittenRfTags = counterValue - counterMinValue
        saveMemory()
        if (writeRecords.size % 100 == 0) {
            if (iotHubConnected) {
                if (iotHubService.sendLogFile(writeRecords)) {
                    writeRecords.clear()
                    saveMemory()
                }
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(InterruptedException::class)
    private fun writeTagNoQRCode(barcodeID: String) {

        var epc = ""
        val rawEpcs: MutableList<String>
        val tidMap = mutableMapOf<String, String>()
        var epcs = mutableListOf<String>()

        if (!setRFPower(state, rf, writeTagNoQRCodeRfPower)) {
            return
        }

        rf.startInventoryTag(0, 0, 0)

        val timeout = System.currentTimeMillis() + 500
        while (epcs.size < 5 && System.currentTimeMillis() < timeout) {
            rf.readTagFromBuffer()?.also {
                epcs.add(it.epc)
                tidMap[it.epc] = it.tid
            }
        }

        rf.stopInventory()

        epcs = epcs.distinct().toMutableList()
        rawEpcs = epcs.filter {
            !it.startsWith("30")
        }.toMutableList()

        when {
            epcs.isEmpty() -> {
                result += "هیج تگی پیدا نشد. لطفا دستگاه را نزدیک تگ قرار دهید و دوباره تلاش کنید."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }
            epcs.size == 1 -> {
                epc = epcs[0]
                oldMethodTid = tidMap[epc] ?: "null"
            }
            rawEpcs.size > 1 -> {
                result += "تعداد تگ های خام پیدا شده بیشتر از یک است. لطفا تگ مورد نظرتان را جدا از بقیه قرار دهید و دوباره تلاش کنید."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }
            rawEpcs.size == 1 -> {
                epc = rawEpcs[0]
                oldMethodTid = tidMap[epc] ?: "null"
            }
            rawEpcs.isEmpty() -> {
                result += "همه تگ های این محدوده رایت شده هستند. لطفا تگ مورد نظرتان را جدا از بقیه قرار دهید و دوباره تلاش کنید."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }
        }

        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest1(Method.POST, url, null, fun(it) {

            val decodedTagEpc = epcDecoder(epc)

            if (it.getJSONArray("KBarCodes").length() == 0) {
                result += "بارکد مورد نظر در سیستم تعریف نشده است. لطفا با پشتیبانی تماس بگیرید."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }

            barcodeInformation = it.getJSONArray("KBarCodes").getJSONObject(0)

            if (decodedTagEpc.header == 48) {
                if ((decodedTagEpc.company == 100 && decodedTagEpc.item == barcodeInformation.getLong(
                        "BarcodeMain_ID"
                    )) ||
                    (decodedTagEpc.company == 101 && decodedTagEpc.item == barcodeInformation.getString(
                        "RFID"
                    )
                        .toLong())
                ) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                    resultColor = doneColor
                    result += "این تگ قبلا با همین بارکد رایت شده است" + "\n"
                    return
                } else {
                    result += "این تگ قبلا با بارکد دیگری رایت شده است" + "\n"
                    openRewriteDialog = true
                    return
                }
            } else {
                write(barcodeInformation, false, oldMethodTid)
            }

        }, {

            when (it) {
                is NoConnectionError -> {
                    Toast.makeText(
                        this,
                        "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Toast.makeText(this, it.toString(), Toast.LENGTH_LONG).show()
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

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                barcodeArray.put(barcodeID)

                json.put("KBarCodes", barcodeArray)

                return json.toString().toByteArray()
            }
        }
        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun writeTagByQRCode(barcodeID: String) {

        var epc: String
        if (!setRFPower(state, rf, 30)) {
            return
        }
        rf.readData("00000000", IUHF.Bank_TID, 0, 96, "E28$newMethodTid", IUHF.Bank_EPC, 2, 6).let {
            if (it.isNullOrEmpty()) {
                result += "تگ پیدا نشد. لطفا دستگاه را نزدیک لباس قرار دهید و دوباره تلاش کنید."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            } else {
                epc = it
            }
        }

        val url = "http://rfid-api.avakatan.ir/products/v3"
        val request = object : JsonObjectRequest1(Method.POST, url, null, fun(it) {

            val decodedTagEpc = epcDecoder(epc)

            if (it.getJSONArray("KBarCodes").length() == 0) {
                result += "بارکد مورد نظر در سیستم تعریف نشده است. لطفا با پشتیبانی تماس بگیرید."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }

            barcodeInformation = it.getJSONArray("KBarCodes").getJSONObject(0)

            if (decodedTagEpc.header == 48) {
                if ((decodedTagEpc.company == 100 && decodedTagEpc.item == barcodeInformation.getLong(
                        "BarcodeMain_ID"
                    )) ||
                    (decodedTagEpc.company == 101 && decodedTagEpc.item == barcodeInformation.getString(
                        "RFID"
                    )
                        .toLong())
                ) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                    resultColor = doneColor
                    result += "این تگ قبلا با همین بارکد رایت شده است" + "\n"
                    return
                } else {
                    result += "این تگ قبلا با بارکد دیگری رایت شده است" + "\n"
                    openRewriteDialog = true
                    return
                }
            } else {
                write(barcodeInformation, false, "E28$newMethodTid")
            }

        }, {
            result += when {
                it is NoConnectionError -> {
                    "اینترنت قطع است. شبکه وای فای را بررسی کنید."
                }
                it.networkResponse.statusCode == 404 -> {
                    "بارکد مورد نظر در سیستم تعریف نشده است. لطفا با پشتیبانی تماس بگیرید."
                }
                else -> {
                    it.toString()
                }
            }
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            resultColor = errorColor
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

                barcodeArray.put(barcodeID)

                json.put("KBarCodes", barcodeArray)

                return json.toString().toByteArray()
            }
        }

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun epcGenerator(
        header: Int,
        filter: Int,
        partition: Int,
        company: Int,
        item: Long,
        serial: Long
    ): String {

        var tempStr = java.lang.Long.toBinaryString(header.toLong())
        val headerStr = String.format("%8s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(filter.toLong())
        val filterStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(partition.toLong())
        val positionStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(company.toLong())
        val companynumberStr = String.format("%12s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(item)
        val itemNumberStr = String.format("%32s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(serial)
        val serialNumberStr = String.format("%38s", tempStr).replace(" ".toRegex(), "0")
        val epcStr =
            headerStr + positionStr + filterStr + companynumberStr + itemNumberStr + serialNumberStr // binary string of EPC (96 bit)

        tempStr = epcStr.substring(0, 64).toULong(2).toString(16)
        val epc0To64 = String.format("%16s", tempStr).replace(" ".toRegex(), "0")
        tempStr = epcStr.substring(64, 96).toULong(2).toString(16)
        val epc64To96 = String.format("%8s", tempStr).replace(" ".toRegex(), "0")

        return epc0To64 + epc64To96

    }

    private fun epcDecoder(epc: String): EPC {

        val binaryEPC =
            String.format("%64s", epc.substring(0, 16).toULong(16).toString(2))
                .replace(" ".toRegex(), "0") +
                    String.format("%32s", epc.substring(16, 24).toULong(16).toString(2))
                        .replace(" ".toRegex(), "0")
        val result = EPC(0, 0, 0, 0, 0L, 0L)
        result.header = binaryEPC.substring(0, 8).toInt(2)
        result.partition = binaryEPC.substring(8, 11).toInt(2)
        result.filter = binaryEPC.substring(11, 14).toInt(2)
        result.company = binaryEPC.substring(14, 26).toInt(2)
        result.item = binaryEPC.substring(26, 58).toLong(2)
        result.serial = binaryEPC.substring(58, 96).toLong(2)
        return result
    }

    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                    snackbarHost = { CustomSnackBar(state) },
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

                IconButton(onClick = { openFileDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_share_24),
                        contentDescription = ""
                    )
                }
                IconButton(onClick = {
                    openClearDialog = true
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = "رایت",
                    modifier = Modifier
                        .padding(start = 35.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @Composable
    fun Content() {

        Column {

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 8.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
            ) {

                if (openFileDialog) {
                    FileAlertDialog()
                }

                if (openClearDialog) {
                    ClearAlertDialog()
                }

                if (openRewriteDialog) {
                    RewriteAlertDialog()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagTypeDropDownList(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 16.dp, bottom = 8.dp, top = 8.dp)
                    )

                    Text(
                        text = "تعداد رایت شده: $counter",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 16.dp, bottom = 8.dp, top = 8.dp),
                    )
                }

                if (barcodeIsScanning || rfIsScanning) {
                    Row(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.primary)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .background(
                        color = resultColor,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Text(
                    text = result,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.body2
                )
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
                            openFileDialog = false
                            exportFile()
                        }) {
                        Text(text = "ذخیره")
                    }
                }
            },

            onDismissRequest = {
                openFileDialog = false
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
                        text = "حافظه پاک شود؟",
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
    fun RewriteAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openRewriteDialog = false
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
                        text = "این تگ قبلا با بارکد دیگری رایت شده است. مقدار جدید جایگزین قبلی شود؟",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 18.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openRewriteDialog = false
                            if (tagTypeValue == "تگ کیوآر کد دار") {
                                write(barcodeInformation, true, "E28$newMethodTid")
                            } else {
                                write(barcodeInformation, true, oldMethodTid)
                            }
                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = { openRewriteDialog = false },
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
    fun TagTypeDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .testTag("WriteActivityTagTypeDropDownList")
                    .clickable { expanded = true }) {
                Text(text = tagTypeValue)
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

                tagTypeValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        tagTypeValue = it
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }
}