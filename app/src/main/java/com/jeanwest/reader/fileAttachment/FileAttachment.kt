package com.jeanwest.reader.fileAttachment

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileAttachment : ComponentActivity() {

    private var isScanning = false
    lateinit var rf: RFIDWithUHFUART
    var rfPower = 30
    var epcTableValid: MutableMap<String, Int> = HashMap()
    var epcLastLength = 0
    var barcodeTable = ArrayList<String>()
    var beepMain = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rf = RFIDWithUHFUART.getInstance()

        epcTableValid.clear()

        epcLastLength = epcTableValid.size
        while (!rf.setEPCMode()) {
        }
        if (rf.power != rfPower) {
            while (!rf.setPower(rfPower)) {
            }
        }
        isScanning = false

        setContent {
            Page()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if(keyCode == 280 || keyCode == 293 || keyCode == 139) {
            if (!isScanning) {

                isScanning = true
                while (!rf.setPower(rfPower)) {
                }
                CoroutineScope(IO).launch {
                    scanningRoutine()
                }

            } else {
                isScanning = false
            }
        } else if(keyCode == 4) {
            back()
        }
        return true
    }

    private suspend fun scanningRoutine() {

        rf.startInventoryTag(0, 0, 0)


        rf.stopInventory()
        withContext(Main) {
            showPropertiesToUser(0, beepMain)
        }
    }

    private suspend fun scanningLoop() {
        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null && uhfTagInfo.epc.startsWith("30")) {
                    epcTableValid[uhfTagInfo.epc] = 1
                } else {
                    break
                }
            }

            withContext(Main) {
                showPropertiesToUser(epcTableValid.size - epcLastLength, beepMain)
            }

            epcLastLength = epcTableValid.size

            delay(1000)
        }
    }

    private fun showPropertiesToUser(speed: Int, beep: ToneGenerator) {

        var properties = ""

        properties += "سرعت اسکن (تگ بر ثانیه): " + speed + "\n"

        if (!isScanning) {
            properties +="تعداد کالا های پیدا شده: " + (epcTableValid.size + barcodeTable.size)
        } else {

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
        setContent { Page(properties) }
    }


    private fun back() {
        finish()
    }

    @Composable
    fun Page(properties : String = "تعداد تگ های اسکن شده: 0" + "\n") {
        MyApplicationTheme() {
            Scaffold(
                topBar = { AppBar() },
                content = { Content(properties) },
            )
        }
    }

    @Composable
    fun AppBar() {
        TopAppBar(

            navigationIcon = {
                Box(modifier = Modifier.width(60.dp)) {
                    IconButton(onClick = { back() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = ""
                        )
                    }
                }
            },

            title = {
                Text(
                    text = "پیوست فایل",
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize()
                        .padding(end = 60.dp),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @Composable
    fun Content(properties : String = "تعداد تگ های اسکن شده: 0" + "\n") {

        val slideValue = rememberSaveable { mutableStateOf(30F) }

        Column {

            Text(
                text = properties,
                fontSize = 18.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .fillMaxWidth()
            )

            Row() {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .weight(1F)
                        .padding(8.dp, 4.dp, 8.dp, 4.dp)
                        .fillMaxWidth()
                ) {
                    Text(text = "پاک کردن")
                }
                Button(
                    onClick = {},
                    modifier = Modifier
                        .weight(1F)
                        .padding(8.dp, 4.dp, 8.dp, 4.dp)
                        .fillMaxWidth()
                ) {
                    Text(text = "ارسال حواله")
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
            }
        }
    }

    @Preview
    @Composable
    fun Preview() {
        Page()
    }
}