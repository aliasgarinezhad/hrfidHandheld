package com.jeanwest.reader

import android.annotation.SuppressLint
import android.app.VoiceInteractor
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import java.lang.reflect.Method
import java.util.*
import kotlin.collections.HashMap

class TransferenceActivity : AppCompatActivity() {

    var power = 30
    private var isScanning = false
    var beepMain = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    var timerHandler = Handler(Looper.getMainLooper())
    lateinit var rf: RFIDWithUHFUART
    var epcTable: MutableMap<String, Int> = HashMap()
    var epcTableValid: MutableMap<String, Int> = HashMap()
    var epcLastLength = 0
    private var processingInProgress = false
    lateinit var button: Button
    lateinit var status : TextView
    var source = 0;
    var des = 0;
    var explanation = ""

    private var timerRunnable: Runnable = object : Runnable {

        @SuppressLint("SetTextI18n", "ResourceAsColor")
        override fun run() {

            if (isScanning) {

                var uhfTagInfo: UHFTAGInfo?
                while (true) {
                    uhfTagInfo = rf.readTagFromBuffer()
                    if (uhfTagInfo != null) {
                        epcTable[uhfTagInfo.epc] = 1
                    } else {
                        break
                    }
                }

                showPropertiesToUser(epcTable.size - epcLastLength, beepMain)
                epcLastLength = epcTable.size

                timerHandler.postDelayed(this, 1000)

            } else if (processingInProgress) {

                var header = ""
                epcTableValid.clear()
                for ((key) in epcTable) {

                    if (key.isNotEmpty()) {
                        header = key.substring(0, 2)
                        if (header == "30") {
                            epcTableValid[key] = 1
                        }
                    } else {
                        Log.e("errorx", key)
                    }
                }
                showPropertiesToUser(0, beepMain)

                var url = "http://rfid-api-0-1.avakatan.ir/products/v2?"
                for (key in epcTableValid) {
                    url += "epc=" + key.key + "&"
                }

                var request = JsonObjectRequest(Request.Method.GET, url, null,  {
                    response ->
                    Toast.makeText(this@TransferenceActivity, response.toString(), Toast.LENGTH_LONG).show()
                }, { response ->
                    Toast.makeText(this@TransferenceActivity, response.toString(), Toast.LENGTH_LONG).show()
                })

                val queue = Volley.newRequestQueue(this@TransferenceActivity)
                queue.add(request)

                isScanning = false
                processingInProgress = false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transference)

        val powerText = findViewById<TextView>(R.id.readingPowerTextViewT)
        val powerSeekBar = findViewById<SeekBar>(R.id.readingPowerSeekBarT)
        button = findViewById(R.id.buttonReadingT)
        status = findViewById(R.id.section_labelT)

        powerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!isScanning) {
                    power = progress + 5
                    powerText.text = "اندازه توان($power)"
                } else {
                    powerSeekBar.progress = power - 5
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        epcTable.clear()
        epcTableValid.clear()

        epcLastLength = WarehouseScanningActivity.EPCTable.size
        while (!rf.setEPCMode()) {
        }
        if (rf.power != power) {
            while (!rf.setPower(power)) {
            }
        }
        isScanning = false
        processingInProgress = false

        showPropertiesToUser(0, beepMain)

        powerText.text = "اندازه توان($power)"
        powerSeekBar.progress = power - 5

        source = intent.getIntExtra("source", 0)
        des = intent.getIntExtra("des", 0)
        explanation = intent.getStringExtra("explanation")!!
        
    }

    @SuppressLint("SetTextI18n")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            if (event.repeatCount == 0) {
                if (!isScanning) {
                    while (!rf.setPower(power)) {
                    }
                    processingInProgress = false
                    isScanning = true
                    button.setBackgroundColor(Color.GRAY)
                    rf.startInventoryTag(0, 0, 0)
                    timerHandler.post(timerRunnable)
                } else {
                    timerHandler.removeCallbacks(timerRunnable)
                    rf.stopInventory()

                    isScanning = false
                    processingInProgress = true

                    status.text = "در حال پردازش ..."

                    timerHandler.postDelayed(timerRunnable, 500)
                }
            }
        } else if (keyCode == 4) {
            if (isScanning) {
                rf.stopInventory()
                isScanning = false
            }
            finish()
        }
        return true
    }


    fun sendFile(view: View) {}
    fun clearAll(view: View) {
        epcLastLength = 0
    }

    @SuppressLint("SetTextI18n")
    fun showPropertiesToUser(speed: Int, beep: ToneGenerator) {

        status.text = "حواله از " + source + " به " + des + "\n"

        status.text = status.text.toString() + "توضیحات: " + explanation + "\n"

        status.text = status.text.toString() + "سرعت اسکن (تگ بر ثانیه): " + speed + "\n"

        if (speed == 0) {
            status.text =
                status.text.toString() + "تعداد کالا های پیدا شده: " + epcTableValid.size
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
    }
}