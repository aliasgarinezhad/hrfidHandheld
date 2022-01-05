package com.jeanwest.reader.write

//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
//import com.jeanwest.reader.testClasses.Barcode2D
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.JalaliDate.JalaliDate
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.iotHub.IotHub
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.IUHF
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.android.volley.toolbox.JsonObjectRequest as JsonObjectRequest1

class WriteActivity : ComponentActivity(), IBarcodeResult {

    private var deviceSerialNumber = ""
    private var barcode2D = Barcode2D(this)
    private lateinit var rf: RFIDWithUHFUART
    private var epc = ""
    private var tid = ""
    private var barcodeID = ""
    private var barcodeTable = mutableListOf<String>()
    private lateinit var iotHubService: IotHub

    private var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var counter by mutableStateOf(0)
    private var numberOfWrittenRfTags by mutableStateOf(0L)
    private var result by mutableStateOf("")
    private var openClearDialog by mutableStateOf(false)
    private var openFileDialog by mutableStateOf(false)
    private var barcodeIsScanning by mutableStateOf(false)
    private var rfIsScanning by mutableStateOf(false)
    private var resultColor by mutableStateOf(R.color.white)
    private var fileName by mutableStateOf("خروجی")
    private var writeRecords = mutableListOf<WriteRecord>()
    private var userWriteRecords = mutableListOf<WriteRecord>()

    private var step2 = false
    private var rfPower = 5
    private var switchValue by mutableStateOf(false)

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
            Log.e("service connection", "onServiceConnected")
            val binder = service as IotHub.LocalBinder
            iotHubService = binder.service
            iotHubConnected = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e("service connection", "onServiceDisconnected")
            iotHubConnected = false
        }
    }

    override fun onResume() {

        super.onResume()

        val util = JalaliDate()
        fileName = util.currentShamsidate

        barcodeInit()
        rfInit()

        setContent {
            Page()
        }

        loadMemory()

        val intent = Intent(this, IotHub::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun rfInit() {

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcAndTidMode()
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

        Log.e("json", userWriteRecords.toString())

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
        step2 = false
        if (barcodeIsScanning || rfIsScanning) {
            barcodeIsScanning = false
            rf.stopInventory()
        }
        finish()
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
                    this@WriteActivity,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        } else {
            return true
        }
    }

    private fun setRFEpcAndTidMode(): Boolean {

        for (i in 0..11) {
            if (rf.setEPCAndTIDMode()) {
                return true
            }
        }
        CoroutineScope(Main).launch {
            Toast.makeText(
                this@WriteActivity,
                "مشکلی در سخت افزار پیش آمده است",
                Toast.LENGTH_LONG
            ).show()
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        stopBarcodeScan()
        step2 = false
        if (barcodeIsScanning || rfIsScanning) {
            barcodeIsScanning = false
            rf.stopInventory()
        }
    }

    @Throws(InterruptedException::class)
    override fun getBarcode(barcode: String?) {

        if (!barcode.isNullOrEmpty()) {
            barcodeIsScanning = false
            barcodeID = barcode
            result = "اسکن بارکد با موفقیت انجام شد\nID: $barcodeID\n"
            resultColor = R.color.DarkGreen
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            step2 = true

        } else {
            barcodeIsScanning = false
            rf.stopInventory()
            rfIsScanning = false
            result = "بارکدی پیدا نشد"
            resultColor = R.color.Brown
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
                CoroutineScope(Main).launch {
                    Toast.makeText(
                        this@WriteActivity,
                        "تنظیمات نامعتبر است",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return true
            }

            if (barcodeIsScanning) {
                return true
            }
            if (step2) {
                rf.stopInventory()
                rfIsScanning = false
                addNewTag()
                step2 = false
            } else {
                barcodeIsScanning = true
                startBarcodeScan()
                rfIsScanning = true
                if (!setRFPower(rfPower)) {
                    return true
                }
                rf.startInventoryTag(0, 0, 0)
            }
        } else if (keyCode == 4) {
            back()
        }
        return true
    }

    private fun rfWrite(productEPC: String): Boolean {

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

    private fun rfWriteVerify(productEPC: String): Boolean {

        for (k in 0..15) {

            rf.readData("00000000", IUHF.Bank_TID, 0, 96, tid, IUHF.Bank_EPC, 2, 6)?.let {
                if (productEPC == it.lowercase()) {
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(InterruptedException::class)
    private fun addNewTag() {

        val tidMap = mutableMapOf<String, String>()
        var epcs = mutableListOf<String>()

        for (i in 0..600) {
            rf.readTagFromBuffer()?.also {
                epcs.add(it.epc)
                tidMap[it.epc] = it.tid
            } ?: break
        }

        if (epcs.size > 590) {
            Thread.sleep(10)
            rf.startInventoryTag(0, 0, 0)
            startBarcodeScan()
            barcodeIsScanning = true
            rfIsScanning = true
            return
        } else if (epcs.size > 2) {

            epcs = epcs.distinct().toMutableList()

            if (!switchValue) {
                epcs = epcs.filter {
                    !it.startsWith("30")
                }.toMutableList()
            }

            if (epcs.size == 1) {
                epc = epcs[0]
                tid = tidMap[epc] ?: "null"
            }
        }

        if (epcs.size != 1) {

            epcs.clear()
            tidMap.clear()
            Thread.sleep(10)
            rf.startInventoryTag(0, 0, 0)

            val timeout = System.currentTimeMillis() + 500
            while (epcs.size < 5 && System.currentTimeMillis() < timeout) {
                rf.readTagFromBuffer()?.also {
                    epcs.add(it.epc)
                    tidMap[it.epc] = it.tid
                }
            }

            rf.stopInventory()

            if (epcs.size < 3) {
                result += "هیچ تگی یافت نشد"
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = R.color.Brown
                return
            } else {

                epcs = epcs.distinct().toMutableList()

                if (!switchValue) {
                    epcs = epcs.filter {
                        !it.startsWith("30")
                    }.toMutableList()
                }

                when {
                    epcs.isEmpty() -> {
                        result += "هیچ تگ جدیدی یافت نشد"
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                        resultColor = R.color.Brown
                        return
                    }
                    epcs.size > 1 -> {
                        result += "تعداد تگ های یافت شده بیشتر از یک عدد است"
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                        resultColor = R.color.Brown
                        return
                    }
                    else -> {
                        epc = epcs[0]
                        tid = tidMap[epc] ?: "null"
                    }
                }
            }
        }

        val url = "http://rfid-api-0-1.avakatan.ir/products/v2?kbarcode=$barcodeID"
        val request = object : JsonObjectRequest1(Method.GET, url, null,
            fun(it) {

                if (switchValue) {

                    val decodedTagEpc = epcDecoder(epc)

                    if ((decodedTagEpc.company == 100 && decodedTagEpc.item == it.getLong("BarcodeMain_ID")) ||
                        (decodedTagEpc.company == 101 && decodedTagEpc.item == it.getString("RFID")
                            .toLong())
                    ) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                        resultColor = R.color.DarkGreen
                        result += "با موفقیت اضافه شد" + "\n"
                        return
                    }
                }

                if (!setRFPower(30)) {
                    return
                }

                val itemNumber = it.getString("RFID").toLong()// 32 bit
                val serialNumber = counterValue // 38 bit

                val productEPC = epcGenerator(
                    headerNumber,
                    filterNumber,
                    partitionNumber,
                    companyNumber,
                    itemNumber,
                    serialNumber
                )

                if (!rfWrite(productEPC)) {
                    result += "خطا در نوشتن"
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    resultColor = R.color.Brown
                    return
                }

                if (!rfWriteVerify(productEPC)) {
                    result += "خطا در تطبیق"
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    resultColor = R.color.Brown
                    return
                }

                if (!setRFPower(rfPower)) {
                    return
                }

                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                resultColor = R.color.DarkGreen

                result += "با موفقیت اضافه شد" + "\n"
                result += "سریال جدید: $productEPC"

                counterValue++
                barcodeTable.add(it.getString("KBarCode"))

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                val writeRecord = WriteRecord(
                    barcode = it.getString("KBarCode"),
                    epc = productEPC,
                    dateAndTime = sdf.format(Date()),
                    username = MainActivity.username,
                    deviceSerialNumber = deviceSerialNumber,
                    wroteOnRawTag = switchValue
                )
                writeRecords.add(writeRecord)
                userWriteRecords.add(writeRecord)

                counter = userWriteRecords.size
                numberOfWrittenRfTags = counterValue - counterMinValue
                saveMemory()
                if (writeRecords.size > 100) {
                    if (iotHubConnected) {
                        if (iotHubService.sendLogFile(writeRecords)) {
                            writeRecords.clear()
                            saveMemory()
                        }
                    }
                }

            }, {
                result += "خطا در دیتابیس" + it.message
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = R.color.Brown
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Authorization" to "Bearer ${MainActivity.token}")
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
        return result
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
                        .wrapContentSize(),
                    textAlign = TextAlign.Right,
                )
            }
        )
    }

    @Composable
    fun Content() {
        var slideValue by rememberSaveable { mutableStateOf(rfPower.toFloat()) }

        Column {

            Column(
                modifier = Modifier
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp, top = 5.dp)
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
                        text = "کل تگ های رایت شده: $numberOfWrittenRfTags",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(horizontal = 8.dp),
                    )

                    Text(
                        text = "شمارنده: $counter",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(horizontal = 8.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                ) {
                    Text(
                        text = "رایت مجدد تگ های استفاده شده",
                        modifier = Modifier
                            .padding(end = 4.dp, bottom = 10.dp)
                            .align(Alignment.CenterVertically),
                    )

                    Switch(
                        checked = switchValue,
                        onCheckedChange = {
                            switchValue = it
                        },
                        modifier = Modifier
                            .padding(end = 4.dp, bottom = 10.dp)
                            .testTag("switch"),
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
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                    .background(
                        color = colorResource(id = resultColor),
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
}