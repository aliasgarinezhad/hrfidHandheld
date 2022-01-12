package com.jeanwest.reader.search

import com.rscja.deviceapi.RFIDWithUHFUART
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.jeanwest.reader.R
//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.json.JSONObject
import java.util.*

class SearchSubActivity : ComponentActivity() {

    private var scannedNumber by mutableStateOf(0)
    private var rfPower by mutableStateOf(30)
    private var continious by mutableStateOf(false)
    private var isScanning by mutableStateOf(false)
    private lateinit var product: SearchResultProducts
    private var scanningJob: Job? = null
    private var matchedEpcTable = mutableListOf<String>()
    private var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private lateinit var rf: RFIDWithUHFUART

    @ExperimentalCoilApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }

        rfInit()

        val stuff = JSONObject(
            intent.getStringExtra("product") ?: """{
                "RFID": 130290,
                "BarcodeMain_ID": 9514289,
                "K_Bar_Code": "64822109",
                "kbarcode": "64822109J-8010-F",
                "productName": "ساپورت",
                "Title2": "ساپورت",
                "Color": "8010",
                "Size": "F",
                "ImgUrl": "https://www.banimode.com/jeanswest/image.php?token=tmv43w4as&code=64822109J-8010-F",
                "dbCountStore": 0,
                "dbCountDepo": -5,
                "OrigPrice": 1490000,
                "SalePrice": 1490000,
                "SalePercent": 0
            }"""
        )
        product = SearchResultProducts(
            name = stuff.getString("productName"),
            productCode = stuff.getString("K_Bar_Code"),
            kbarcode = stuff.getString("kbarcode"),
            originalPrice = stuff.getString("OrigPrice"),
            salePrice = stuff.getString("SalePrice"),
            shoppingNumber = stuff.getInt("dbCountStore"),
            warehouseNumber = stuff.getInt("dbCountDepo"),
            imageUrl = stuff.getString("ImgUrl"),
            primaryKey = stuff.getLong("BarcodeMain_ID"),
            rfidKey = stuff.getLong("RFID"),
            color = stuff.getString("Color"),
            size = stuff.getString("Size")
        )
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

        if (!continious) {
            matchedEpcTable.clear()
        }

        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            var epcTable = mutableListOf<String>()
            var found = false

            while (true) {
                rf.readTagFromBuffer()?.let {
                    epcTable.add(it.epc)
                } ?: break
            }
            epcTable = epcTable.distinct().toMutableList()

            epcTable = epcTable.filter {
                it.startsWith("30")
            }.toMutableList()

            epcTable.forEach {
                val decodedEpc = epcDecoder(it)
                if ((decodedEpc.company == 101 && decodedEpc.item == product.rfidKey) ||
                    (decodedEpc.company == 100 && decodedEpc.item == product.primaryKey)
                ) {
                    matchedEpcTable.add(it)
                    matchedEpcTable = matchedEpcTable.distinct().toMutableList()
                    found = true
                }
            }

            if (found) {
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 700)
            }

            scannedNumber = matchedEpcTable.size

            delay(1000)
        }

        rf.stopInventory()
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

    @ExperimentalCoilApi
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

    @ExperimentalCoilApi
    @Composable
    fun Content() {

        val modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .wrapContentWidth()

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
                        text = "قدرت آنتن($rfPower)",
                        modifier = Modifier
                            .padding(end = 8.dp, start = 8.dp)
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center
                    )
                    Slider(
                        modifier = Modifier.padding(end = 8.dp, start = 8.dp),
                        value = rfPower.toFloat(),
                        valueRange = 5f..30f,
                        onValueChange = {
                            rfPower = it.toInt()
                        })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text(text = "پیدا شده: $scannedNumber")
                    ContiniousSwitch()
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
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colors.onPrimary,
                            shape = MaterialTheme.shapes.small
                        )
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.h1,
                            textAlign = TextAlign.Right,
                            modifier = modifier,
                            color = colorResource(id = R.color.DarkSlateGray)
                        )
                        Text(
                            text = product.kbarcode,
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Right,
                            modifier = modifier,
                            color = colorResource(id = R.color.Goldenrod)
                        )
                        Text(
                            text = product.originalPrice,
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Right,
                            modifier = modifier,
                            color = colorResource(id = R.color.Brown)
                        )
                        Text(
                            text = product.salePrice,
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Right,
                            modifier = modifier,
                            color = colorResource(id = R.color.DarkGreen)
                        )
                        Text(
                            text = "موجودی فروشگاه: " + product.shoppingNumber.toString(),
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Right,
                            modifier = modifier,
                            //color = colorResource(id = R.color.Brown)
                        )
                        Text(
                            text = "موجودی انبار: " + product.warehouseNumber.toString(),
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Right,
                            modifier = modifier,
                            //color = colorResource(id = R.color.Brown)
                        )
                    }

                    Image(
                        painter = rememberImagePainter(data = product.imageUrl),
                        contentDescription = "",
                        modifier = Modifier
                            .height(200.dp)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun ContiniousSwitch() {
        Row {
            Text(
                text = "ذخیره تگ های پیدا شده",
                modifier = Modifier.padding(end = 4.dp, bottom = 10.dp)
            )
            Switch(checked = continious, modifier = Modifier.testTag("SearchSubSaveSwitch"), onCheckedChange = {
                continious = it
            })
        }
    }

    @Composable
    @Preview
    fun PreviewFun() {
        AppBar()
    }
}