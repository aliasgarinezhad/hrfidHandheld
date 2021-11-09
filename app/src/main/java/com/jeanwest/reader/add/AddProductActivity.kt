package com.jeanwest.reader.add

import com.jeanwest.reader.hardware.Barcode2D
import com.rscja.deviceapi.RFIDWithUHFUART
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.preference.PreferenceManager
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.IUHF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import java.util.*

class AddProductActivity : ComponentActivity(), IBarcodeResult {

    private var barcode2D = Barcode2D(this)
    private lateinit var rf: RFIDWithUHFUART
    private var epc = ""
    private var tid = ""
    private var barcodeID = ""

    private var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var counter by mutableStateOf(0L)
    private var numberOfWrittenRfTags by mutableStateOf(0L)
    private var result by mutableStateOf("")
    private var openDialog by mutableStateOf(false)
    private var barcodeIsScanning by mutableStateOf(false)
    private var rfIsScanning by mutableStateOf(false)
    private var resultColor by mutableStateOf(R.color.white)

    private var step2 = false
    private var rfPower = 5
    private var edit = false

    private var counterMaxValue = 0L
    private var counterMinValue = 0L
    private var tagPassword = "00000000"
    private var counterValue = 0L
    private var filterNumber = 0 // 3bit
    private var partitionNumber = 0 // 3bit
    private var headerNumber = 48 // 8bit
    private var companyNumber = 101 // 12bit

    override fun onResume() {

        super.onResume()

        barcodeInit()
        rfInit()

        setContent {
            Page()
        }

        loadMemory()
    }

    private fun rfInit() {

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcAndTidMode()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        if (memory.getLong("value", -1L) != -1L) {

            counterValue = memory.getLong("value", -1L)
            counterMaxValue = memory.getLong("max", -1L)
            counterMinValue = memory.getLong("min", -1L)
            headerNumber = memory.getInt("header", -1)
            filterNumber = memory.getInt("filter", -1)
            partitionNumber = memory.getInt("partition", -1)
            companyNumber = memory.getInt("company", -1)
            tagPassword = memory.getString("password", "") ?: "00000000"
            counter = memory.getLong("counterModified", -1L)
        }

        numberOfWrittenRfTags = counterValue - counterMinValue
    }

    private fun saveMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()
        memoryEditor.putLong("value", counterValue)
        memoryEditor.putLong("counterModified", counter)
        memoryEditor.apply()
    }

    private fun back() {
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
                    this@AddProductActivity,
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
                this@AddProductActivity,
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {

            if (event.repeatCount != 0) {
                return true
            }
            if (counterValue >= counterMaxValue) {
                Toast.makeText(
                    this@AddProductActivity,
                    "تنظیمات نامعتبر است",
                    Toast.LENGTH_LONG
                ).show()
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

    @Throws(InterruptedException::class)
    private fun addNewTag() {

        val tidMap = mutableMapOf<String, String>()
        var epcs = mutableListOf<String>()

        for (i in 0..1000) {
            rf.readTagFromBuffer()?.also {
                epcs.add(it.epc)
                tidMap[it.epc] = it.tid
            } ?: break
        }

        if (epcs.size > 990) {
            Thread.sleep(100)
            rf.startInventoryTag(0, 0, 0)
            startBarcodeScan()
            barcodeIsScanning = true
            rfIsScanning = true
            return
        } else if (epcs.size > 2) {

            epcs = epcs.distinct().toMutableList()

            if (!edit) {
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
            Thread.sleep(100)
            rf.startInventoryTag(0, 0, 0)
            Thread.sleep(900)

            for (i in 0..15) {

                rf.readTagFromBuffer()?.also {
                    epcs.add(it.epc)
                    tidMap[it.epc] = it.tid
                } ?: break
            }

            rf.stopInventory()

            if (epcs.size < 10) {
                result += "هیچ تگی یافت نشد"
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = R.color.Brown
                return
            } else {

                epcs = epcs.distinct().toMutableList()

                if (!edit) {
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

        val url = "http://rfid-api-0-1.avakatan.ir/products/v2?KBarCode=$barcodeID"
        val request = object : JsonObjectRequest(Method.GET, url, null, fun(it) {

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
            //result += "\nHeader: $headerNumber"
            //result += "\nFilter: $filterNumber"
            //result += "\nPartition: $partitionNumber"
            //result += "\nCompany number: $companyNumber"
            //result += "\nItem number: $itemNumber"
            //result += "\nSerial number: $serialNumber"
            result += "سریال جدید: $productEPC"

            counterValue++
            counter++
            numberOfWrittenRfTags = counterValue - counterMinValue
            saveMemory()
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

                IconButton(onClick = {
                    counter = 0
                    saveMemory()
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }

                IconButton(onClick = {
                    openDialog = true
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_settings_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = "اضافه کردن",
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
        var slideValue by rememberSaveable { mutableStateOf(rfPower.toFloat()) }
        var switchValue by rememberSaveable { mutableStateOf(false) }

        Column {

            Column(
                modifier = Modifier
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .fillMaxWidth()
            ) {

                if (openDialog) {
                    PasswordAlertDialog()
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
                        text = "تعداد اضافه شده: $numberOfWrittenRfTags",
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

                    Row {
                        Text(
                            text = "اصلاح",
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 10.dp)
                                .align(Alignment.CenterVertically),
                        )

                        Switch(
                            checked = switchValue,
                            onCheckedChange = {
                                edit = it
                                switchValue = it
                            },
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 10.dp)
                                .testTag("switch"),
                        )
                    }
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
                        shape = RoundedCornerShape(10.dp)
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
    fun PasswordAlertDialog() {

        var password by remember { mutableStateOf("") }

        AlertDialog(

            buttons = {

                Column {

                    Text(
                        text = "رمز عبور را وارد کنید", modifier = Modifier
                            .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                    )

                    OutlinedTextField(
                        value = password, onValueChange = {
                            password = it
                        },
                        modifier = Modifier
                            .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                            .align(Alignment.CenterHorizontally)
                    )

                    Button(modifier = Modifier
                        .padding(bottom = 10.dp, top = 10.dp, start = 10.dp, end = 10.dp)
                        .align(Alignment.CenterHorizontally),
                        onClick = {
                            if (password == "123456") {
                                openDialog = false
                                val nextActivityIntent = Intent(
                                    this@AddProductActivity,
                                    AddProductSettingActivity::class.java
                                )
                                startActivity(nextActivityIntent)
                            } else {
                                Toast.makeText(
                                    this@AddProductActivity,
                                    "رمز عبور اشتباه است",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                        Text(text = "ورود")
                    }
                }
            },

            onDismissRequest = {
                openDialog = false
            }
        )
    }
}
