package com.jeanwest.reader.search

//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main

@ExperimentalCoilApi
class SearchSubActivity : ComponentActivity() {

    private var scannedNumber by mutableStateOf(0)
    private var rfPower by mutableStateOf(30)
    private var isScanning by mutableStateOf(false)
    private lateinit var product: SearchResultProducts
    private var scanningJob: Job? = null
    private var matchedEpcTable = mutableListOf<String>()
    private lateinit var rf: RFIDWithUHFUART

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }

        intent.getStringExtra("product").let {
            if (it.isNullOrEmpty()) {
                Toast.makeText(this, "امکان جست و جو برای این کالا وجود ندارد.", Toast.LENGTH_LONG)
                    .show()
                finish()
            } else {
                val type = object : TypeToken<SearchResultProducts>() {}.type
                product = Gson().fromJson(
                    it,
                    type
                )
            }
        }

        rfInit()
    }

    private fun rfInit() {

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode()
    }

    private fun setRFEpcMode(): Boolean {
        for (i in 0..11) {
            if (rf.setEPCMode()) {
                return true
            }
        }
        CoroutineScope(Main).launch {
            Toast.makeText(
                this@SearchSubActivity,
                "مشکلی در سخت افزار پیش آمده است",
                Toast.LENGTH_LONG
            ).show()
        }
        return false
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

    private suspend fun startFinding() {

        isScanning = true
        if (!setRFPower(rfPower)) {
            isScanning = false
            return
        }

        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            val epcTableWithRssi = mutableMapOf<String, String>()
            var isFound = false
            var foundRssi = 100F
            var uhfTagInfo: UHFTAGInfo?

            while (true) {

                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        epcTableWithRssi[uhfTagInfo.epc] = uhfTagInfo.rssi
                    }
                } else {
                    break
                }
            }

            epcTableWithRssi.forEach {

                val decodedEpc = epcDecoder(it.key)
                if ((decodedEpc.company == 101 && decodedEpc.item == product.rfidKey) ||
                    (decodedEpc.company == 100 && decodedEpc.item == product.primaryKey)
                ) {
                    matchedEpcTable.add(it.key)
                    matchedEpcTable = matchedEpcTable.distinct().toMutableList()
                    isFound = true
                    val productRssi = it.value.toFloat() * -1
                    if (productRssi < foundRssi) {
                        foundRssi = productRssi
                    }
                }
            }

            when (rfPower) {
                30 -> {
                    if (isFound) {
                        val beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                        if (foundRssi < 60F) {
                            changePowerWhileScanning(20)
                        } else {
                            delay(500)
                        }
                    } else {
                        delay(500)
                    }
                }

                20 -> {
                    if (isFound) {
                        val beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 300)
                        if (foundRssi < 60F) {
                            changePowerWhileScanning(10)
                        } else {
                            delay(500)
                        }
                    } else {
                        changePowerWhileScanning(30)
                    }
                }

                10 -> {
                    if (isFound) {
                        val beep = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
                        beep.startTone(ToneGenerator.TONE_DTMF_2, 800)
                        if (foundRssi < 60F) {
                            changePowerWhileScanning(5)
                        } else {
                            delay(500)
                        }
                    } else {
                        changePowerWhileScanning(20)
                    }
                }
                5 -> {
                    if (isFound) {
                        val beep = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
                        beep.startTone(ToneGenerator.TONE_DTMF_2, 1100)
                        delay(500)
                    } else {
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
        if (!setRFPower(rfPower)) {
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

    private fun setRFPower(power: Int): Boolean {
        if (rf.power != power) {

            for (i in 0..11) {
                if (rf.setPower(power)) {
                    return true
                }
            }
            CoroutineScope(Main).launch {
                Toast.makeText(
                    this@SearchSubActivity,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        } else {
            return true
        }
    }

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
                    content = { Content() }
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
                    .padding(start = 5.dp, end = 5.dp, top = 5.dp)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    ),
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text(
                        text = "توان آنتن ($rfPower)  ",
                        modifier = Modifier
                            .padding(end = 8.dp, start = 8.dp)
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center
                    )
                    Slider(
                        modifier = Modifier.padding(end = 12.dp),
                        value = rfPower.toFloat(),
                        valueRange = 5f..30f,
                        onValueChange = {
                            rfPower = it.toInt()
                        })
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

            Column(
                Modifier
                    .padding(start = 5.dp, end = 5.dp, top = 8.dp)
                    .fillMaxWidth()
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
            .padding(top = 2.dp, start = 16.dp, bottom = 2.dp)
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
                    text = "موجودی فروشگاه: " + product.shoppingNumber.toString(),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "موجودی انبار: " + product.warehouseNumber.toString(),
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