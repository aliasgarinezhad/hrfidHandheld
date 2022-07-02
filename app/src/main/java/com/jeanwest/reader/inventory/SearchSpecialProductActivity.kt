package com.jeanwest.reader.inventory

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.sharedClassesAndFiles.ExceptionHandler
import com.jeanwest.reader.R
import com.jeanwest.reader.sharedClassesAndFiles.setRFEpcMode
import com.jeanwest.reader.sharedClassesAndFiles.setRFPower
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.rscja.deviceapi.RFIDWithUHFUART
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.LoadingCircularProgressIndicator
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.theme.borderColor
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO

class SearchSpecialProductActivity : ComponentActivity() {

    var distance by mutableStateOf(1f)
    private var scannedNumber by mutableStateOf(0)
    private var rfPower by mutableStateOf(30)
    private var isScanning by mutableStateOf(false)
    private lateinit var product: Product
    private var scanningJob: Job? = null
    private var beepJob: Job? = null
    private var matchedEpcTable = mutableListOf<String>()
    private lateinit var rf: RFIDWithUHFUART
    private var state = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }

        intent.getStringExtra("product").let {
            if (it.isNullOrEmpty()) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "کالایی جهت بررسی وجود ندارد",
                        null,
                        SnackbarDuration.Long
                    )
                }
                //finish()

                product = Product(
                    name = "ساپورت",
                    KBarCode = "64822109J-8010-F",
                    imageUrl = "https://www.banimode.com/jeanswest/image.php?token=tmv43w4as&code=64822109J-8010-F",
                    storeNumber = 1,
                    wareHouseNumber = 0,
                    productCode = "64822109",
                    size = "F",
                    color = "8010",
                    originalPrice = "1490000",
                    salePrice = "1490000",
                    primaryKey = 9514289L,
                    rfidKey = 130290L,
                )

            } else {
                val type = object : TypeToken<Product>() {}.type
                product = Gson().fromJson(
                    it,
                    type
                )
            }
        }

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode(rf, state)

        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()!!))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293 || keyCode == 139) {
                if (!isScanning) {
                    scanningJob = CoroutineScope(IO).launch {
                        startFinding()
                    }
                } else {
                    stopFinding()
                }
            } else if (keyCode == 4) {
                back()
            }
        }
        return true
    }

    private suspend fun beep() {

        val beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        while (isScanning) {
            when (distance) {

                0.7f -> {
                    beep.startTone(ToneGenerator.TONE_PROP_BEEP)
                    delay(
                        300
                    )
                    beep.stopTone()
                }
                0.5f -> {
                    beep.startTone(ToneGenerator.TONE_PROP_BEEP)
                    delay(150)
                    beep.stopTone()
                }
                0.2f -> {
                    beep.startTone(ToneGenerator.TONE_PROP_BEEP)
                    delay(100)
                    beep.stopTone()
                }
                0.05f -> {
                    beep.startTone(ToneGenerator.TONE_PROP_BEEP)
                    delay(50)
                    beep.stopTone()
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private suspend fun startFinding() {

        isScanning = true
        if (!setRFPower(state, rf, rfPower)) {
            isScanning = false
            return
        }

        beepJob = CoroutineScope(IO).launch {
            beep()
        }

        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            val epcTable = mutableListOf<String>()
            var isFound = false
            var uhfTagInfo: UHFTAGInfo?

            while (true) {

                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        epcTable.add(uhfTagInfo.epc)
                        InventoryActivity.scannedEpcTable.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            InventoryActivity.scannedEpcTable = InventoryActivity.scannedEpcTable.distinct().toMutableList()

            epcTable.forEach {

                val decodedEpc = epcDecoder(it)
                if ((decodedEpc.company == 101 && decodedEpc.item == product.rfidKey) ||
                    (decodedEpc.company == 100 && decodedEpc.item == product.primaryKey)
                ) {
                    matchedEpcTable.add(it)
                    matchedEpcTable = matchedEpcTable.distinct().toMutableList()
                    isFound = true
                }
            }

            when (rfPower) {
                30 -> {
                    if (isFound) {
                        distance = 0.7f
                        changePowerWhileScanning(20)
                    } else {
                        distance = 1f
                        delay(500)
                    }
                }

                20 -> {
                    if (isFound) {
                        distance = 0.5f
                        changePowerWhileScanning(10)
                    } else {
                        distance = 0.7f
                        changePowerWhileScanning(30)
                    }
                }

                10 -> {
                    if (isFound) {
                        distance = 0.2f
                        changePowerWhileScanning(5)
                    } else {
                        distance = 0.5f
                        changePowerWhileScanning(20)
                    }
                }
                5 -> {
                    if (isFound) {
                        distance = 0.05f
                        delay(500)
                    } else {
                        distance = 0.2f
                        changePowerWhileScanning(10)
                    }
                }
            }
            delay(500)
            scannedNumber = matchedEpcTable.size
        }

        rf.stopInventory()
    }

    private fun changePowerWhileScanning(power: Int) {
        rfPower = power
        rf.stopInventory()
        if (!setRFPower(state, rf, rfPower)) {
            return
        }
        rf.startInventoryTag(0, 0, 0)
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

    data class EPC(
        var header: Int,
        var filter: Int,
        var partition: Int,
        var company: Int,
        var item: Long,
        var serial: Long
    )

    override fun onPause() {
        super.onPause()
        if (isScanning) {
            rf.stopInventory()
            isScanning = false
        }
    }

    private fun clearEPCs() {
        matchedEpcTable.clear()
        scannedNumber = 0
    }

    private fun stopFinding() {

        scanningJob?.let {
            if (it.isActive) {
                isScanning = false // cause scanning routine loop to stop
                runBlocking { it.join() }
            }
        }
        beepJob?.let {
            if (it.isActive) {
                isScanning = false // cause beep to stop
                runBlocking { it.join() }
            }
        }
    }

    private fun back() {
        stopFinding()
        finish()
    }

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
            title = {
                Text(
                    text = "جست و جو",
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Right
                )
            },
            actions = {
                IconButton(
                    onClick = { clearEPCs() },
                    modifier = Modifier.testTag("SearchSubClearButton")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            }
        )
    }

    @Composable
    fun Content() {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .border(
                        BorderStroke(1.dp, borderColor),
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    ),
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "فاصله",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center
                    )
                    LinearProgressIndicator(
                        progress = distance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 8.dp),
                        backgroundColor = MaterialTheme.colors.background
                    )
                }
                LoadingCircularProgressIndicator(isScanning)
            }

            Column(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .fillMaxWidth()
                    .border(
                        BorderStroke(1.dp, borderColor),
                        shape = MaterialTheme.shapes.small
                    )
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    ),
            ) {
                LazyColumnItem()
            }
        }
    }

    @Composable
    fun LazyColumnItem() {

        val modifier = Modifier
            .padding(top = 2.dp, bottom = 2.dp)
            .wrapContentWidth()

        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colors.onPrimary,
                    shape = MaterialTheme.shapes.small
                )
                .fillMaxWidth()
                .height(200.dp)
                .padding(5.dp),
            //horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Image(
                painter = rememberImagePainter(data = product.imageUrl),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(180.dp)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )

            Column {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.h1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = product.KBarCode,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "قیمت: " + product.originalPrice,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "فروش: " + product.salePrice,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "موجودی فروشگاه: " + product.storeNumber.toString(),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "موجودی انبار: " + product.wareHouseNumber.toString(),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "پیدا شده: $scannedNumber",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
            }
        }
    }

    @Composable
    @Preview
    fun PreviewFun() {
        AppBar()
    }
}