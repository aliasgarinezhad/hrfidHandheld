package com.jeanwest.reader.activities


import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.management.*
import com.jeanwest.reader.data.EPC
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.data.getProductsDetails
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.hardware.setRFEpcAndTidMode
import com.jeanwest.reader.hardware.setRFPower
import com.jeanwest.reader.test.RFIDWithUHFUART
import com.jeanwest.reader.ui.*
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.IUHF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WriteTag : ComponentActivity(),
    IBarcodeResult {

    private var barcode2D = Barcode2D(this)
    private lateinit var rf: RFIDWithUHFUART
    private var oldMethodTid = ""
    private var newMethodTid = ""
    private var barcodeID = ""

    private var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var numberOfWrittenRfTags by mutableStateOf(0L)
    private var result by mutableStateOf("")
    private var openRewriteDialog by mutableStateOf(false)
    private var openHelpDialog by mutableStateOf(false)
    private var barcodeIsScanning by mutableStateOf(false)
    private var rfIsScanning by mutableStateOf(false)
    private var resultColor by mutableStateOf(Color.White)
    private lateinit var product: Product
    private val tagTypeValues = mutableListOf("???? ?????????? ???? ??????", "???? ????????")
    var tagTypeValue by mutableStateOf("???? ????????")
    private var state = SnackbarHostState()

    private var write = false
    private lateinit var queue: RequestQueue

    private var writeTagNoQRCodeRfPower = 5

    private var counterMaxValue = 0L
    private var counterMinValue = 0L
    private var tagPassword = "00000000"
    var counterValue = 0L
    private var filterNumber = 0 // 3bit
    private var partitionNumber = 0 // 3bit
    private var headerNumber = 48 // 8bit
    private var companyNumber = 101 // 12bit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queue = Volley.newRequestQueue(this)
        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onResume() {

        super.onResume()

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
        }

        numberOfWrittenRfTags = counterValue - counterMinValue
    }

    private fun saveMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()
        memoryEditor.putLong("value", counterValue)
        memoryEditor.apply()
    }

    private fun back() {
        stopBarcodeScan()
        write = false
        if (barcodeIsScanning || rfIsScanning) {
            barcodeIsScanning = false
            rf.stopInventory()
        }
        queue.stop()
        beep.release()
        finishAndRemoveTask()
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
                write = true
            } else if (tagTypeValue == "???? ?????????? ???? ??????" && write) {
                barcodeIsScanning = false
                newMethodTid = barcode
                writeTagByQRCode(barcodeID)
                write = false
            }
        } else {
            barcodeIsScanning = false
            result = "???????????? ???????? ??????. ???????? ???????? ?????????? ???? ???????????? ?????????? ???????? ????????."
            resultColor = errorColor
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

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "???????? ???????? ???????? ?????????? ???? ???? ?????????? ?????????? ??????. ???????? ???????????? ???????? ???? ???????????????? ???????? ????????????.",
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
                if (tagTypeValue == "???? ?????????? ???? ??????") {
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

    private fun write(product: Product, writeOnRawTag: Boolean, tid: String) {

        if (!setRFPower(state, rf, 30)) {
            return
        }

        val itemNumber = product.rfidKey
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
            result += "???? ???????? ???????? ??????. ???????? ???????????? ???????????? ????????"
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            resultColor = errorColor
            return
        }

        if (!rfWriteVerify(productEPC, tid)) {
            result += "???? ???????? ???????? ??????. ???????? ???????????? ???????????? ????????"
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            resultColor = errorColor
            return
        }

        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        resultColor = doneColor

        result += "???? ???? ???????????? ???????? ????" + "\n"
        result += "?????????? ????????: $productEPC"

        counterValue++

        numberOfWrittenRfTags = counterValue - counterMinValue
        saveMemory()
    }

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
                result += "?????? ?????? ???????? ??????. ???????? ???????????? ???? ?????????? ???? ???????? ???????? ?? ???????????? ???????? ????????."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }
            epcs.size == 1 -> {
                epc = epcs[0]
                oldMethodTid = tidMap[epc] ?: "null"
            }
            rawEpcs.size > 1 -> {
                result += "?????????? ???? ?????? ?????? ???????? ?????? ?????????? ???? ???? ??????. ???????? ???? ???????? ???????????? ???? ?????? ???? ???????? ???????? ???????? ?? ???????????? ???????? ????????."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }
            rawEpcs.size == 1 -> {
                epc = rawEpcs[0]
                oldMethodTid = tidMap[epc] ?: "null"
            }
            rawEpcs.isEmpty() -> {
                result += "?????? ???? ?????? ?????? ???????????? ???????? ?????? ??????????. ???????? ???? ???????? ???????????? ???? ?????? ???? ???????? ???????? ???????? ?? ???????????? ???????? ????????."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            }
        }

        getProductsDetails(
            queue,
            state,
            mutableListOf(),
            mutableListOf(barcodeID),
            { _, barcodes, _, _ ->

                val decodedTagEpc = epcDecoder(epc)

                if (barcodes.size == 0) {
                    result += "?????????? ???????? ?????? ???? ?????????? ?????????? ???????? ??????. ???????? ???? ???????????????? ???????? ????????????."
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    resultColor = errorColor
                } else if (decodedTagEpc.header == 48) {
                    if ((decodedTagEpc.company == 100 && decodedTagEpc.item == barcodes[0].primaryKey) ||
                        (decodedTagEpc.company == 101 && decodedTagEpc.item == barcodes[0].rfidKey)
                    ) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                        resultColor = doneColor
                        result += "?????? ???? ???????? ???? ???????? ?????????? ???????? ?????? ??????" + "\n"
                    } else {
                        product = barcodes[0]
                        result += "?????? ???? ???????? ???? ?????????? ?????????? ???????? ?????? ??????" + "\n"
                        openRewriteDialog = true
                    }
                } else {
                    write(barcodes[0], false, oldMethodTid)
                }

            },
            {
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
            })
    }

    private fun writeTagByQRCode(barcodeID: String) {

        var epc: String
        if (!setRFPower(state, rf, 30)) {
            return
        }
        rf.readData("00000000", IUHF.Bank_TID, 0, 96, "E28$newMethodTid", IUHF.Bank_EPC, 2, 6).let {
            if (it.isNullOrEmpty()) {
                result += "???? ???????? ??????. ???????? ???????????? ???? ?????????? ???????? ???????? ???????? ?? ???????????? ???????? ????????."
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
                return
            } else {
                epc = it
            }
        }

        getProductsDetails(
            queue,
            state,
            mutableListOf(),
            mutableListOf(barcodeID),
            { _, barcodes, _, _ ->

                val decodedTagEpc = epcDecoder(epc)

                if (barcodes.size == 0) {
                    result += "?????????? ???????? ?????? ???? ?????????? ?????????? ???????? ??????. ???????? ???? ???????????????? ???????? ????????????."
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    resultColor = errorColor
                } else if (decodedTagEpc.header == 48) {
                    if ((decodedTagEpc.company == 100 && decodedTagEpc.item == barcodes[0].primaryKey) ||
                        (decodedTagEpc.company == 101 && decodedTagEpc.item == barcodes[0].rfidKey)
                    ) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                        resultColor = doneColor
                        result += "?????? ???? ???????? ???? ???????? ?????????? ???????? ?????? ??????" + "\n"
                    } else {
                        product = barcodes[0]
                        result += "?????? ???? ???????? ???? ?????????? ?????????? ???????? ?????? ??????" + "\n"
                        openRewriteDialog = true
                    }
                } else {
                    write(barcodes[0], false, "E28$newMethodTid")
                }

            },
            {
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                resultColor = errorColor
            })
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

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
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

                IconButton(onClick = {
                    openHelpDialog = true
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_help_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = "????????",
                    modifier = Modifier
                        .padding(end = 50.dp)
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp)
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

                if (openRewriteDialog) {
                    RewriteAlertDialog()
                }

                if (openHelpDialog) {
                    HelpAlertDialog()
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
                }
                LoadingCircularProgressIndicator(barcodeIsScanning || rfIsScanning)
            }

            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .border(
                        BorderStroke(1.dp, borderColor),
                        shape = MaterialTheme.shapes.small
                    )
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
                    style = MaterialTheme.typography.h5
                )
            }
        }
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
                        text = "?????? ???? ???????? ???? ?????????? ?????????? ???????? ?????? ??????. ?????????? ???????? ?????????????? ???????? ????????",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 18.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openRewriteDialog = false
                            if (tagTypeValue == "???? ?????????? ???? ??????") {
                                write(product, true, "E28$newMethodTid")
                            } else {
                                write(product, true, oldMethodTid)
                            }
                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "??????")
                        }
                        Button(
                            onClick = { openRewriteDialog = false },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "??????")
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun HelpAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openHelpDialog = false
            },
            buttons = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = "?????????? ???? ???????? ???????? ???????? ?????? ???????? ?????????????? ?????????? ???????? ???? ???????? ????????. ?????? ???????? ???????????? ???? ???? ???? ?????? ???????? ?????????? ???????? ?? ?????????? ???????? ???? ???????? ???????? ???? ?????????????? ?????????? ???? ???? RFID ?????????? ??????.",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 18.sp
                    )

                    Button(
                        onClick = { openHelpDialog = false },
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .align(CenterHorizontally)
                    ) {
                        Text(text = "????????")
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
