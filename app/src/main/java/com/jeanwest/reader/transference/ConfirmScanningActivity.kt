package com.jeanwest.reader.transference

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.mikhaellopez.circularprogressbar.CircularProgressBar.ProgressDirection
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.HashMap

class ConfirmScanningActivity : AppCompatActivity() {

    private var updateDatabaseInProgress = false
    var beepMain = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    lateinit var rf: RFIDWithUHFUART
    lateinit var status: TextView
    private lateinit var percentage: TextView
    lateinit var powerText: TextView
    lateinit var powerSeekBar: SeekBar
    private var readingInProgress = false
    private var processingInProgress = false
    lateinit var button: Button
    lateinit var circularProgressBar: CircularProgressBar
    lateinit var nextActivityIntent: Intent
    var epcLastLength = 0
    var readingPower = 30
    var temp = ""
    var temp2 = JSONObject()
    var header = ""
    var allStuffs = 1
    var timerHandler = Handler()

    private var timerRunnable: Runnable = object : Runnable {

        @SuppressLint("SetTextI18n", "ResourceAsColor")
        override fun run() {

            if (readingInProgress) {

                var uhfTagInfo: UHFTAGInfo?
                while (true) {
                    uhfTagInfo = rf.readTagFromBuffer()
                    if (uhfTagInfo != null) {
                        EPCTable[uhfTagInfo.epc] = 1
                    } else {
                        break
                    }
                }

                showPropertiesToUser(EPCTable.size - epcLastLength, beepMain)
                epcLastLength = EPCTable.size

                timerHandler.postDelayed(this, 1000)

            } else if (processingInProgress) {

                showPropertiesToUser(0, beepMain)
                readingInProgress = false
                processingInProgress = false
                button.setBackgroundColor(getColor(R.color.Primary))
            }
        }
    }

    @SuppressLint("SetTextI18n", "CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm)
        status = findViewById(R.id.section_label)
        button = findViewById(R.id.buttonReading)
        nextActivityIntent = Intent(this, ConfirmScanningResultActivity::class.java)
        circularProgressBar = findViewById(R.id.circularProgressBar)
        percentage = findViewById(R.id.progressText)
        powerText = findViewById(R.id.readingPowerTextView)
        powerSeekBar = findViewById(R.id.readingPowerSeekBar)
        powerSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!readingInProgress) {
                    readingPower = progress + 5
                    powerText.text = "اندازه توان($readingPower)"
                } else {
                    powerSeekBar.progress = readingPower - 5
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        circularProgressBar.progressMax = 100f
        circularProgressBar.progressBarColor = getColor(R.color.Primary)
        circularProgressBar.backgroundProgressBarColor = Color.GRAY
        circularProgressBar.progressBarWidth = 15f // in DP
        circularProgressBar.backgroundProgressBarWidth = 7f // in DP
        circularProgressBar.roundBorder = true
        circularProgressBar.progressDirection = ProgressDirection.TO_RIGHT
        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        transferID = intent.getLongExtra("departmentInfoID", 0)

        val queue = Volley.newRequestQueue(this)

        val url = "http://rfid-api-0-1.avakatan.ir/stock-drafts/$transferID/conflicts"

        val request = object : JsonObjectRequest(Method.POST, url, null,
            {
                allStuffs = 0
                val stuffs = it.getJSONArray("shortage")
                for (i in 0 until stuffs.length()) {

                    temp2 = stuffs.getJSONObject(i)
                    allStuffs += temp2.getInt("dbCount")

                }
                showPropertiesToUser(0, beepMain)

            }, {
                Toast.makeText(this, it.toString(), Toast.LENGTH_LONG).show()
            }) {
            override fun getBody(): ByteArray {
                return JSONArray().toString().toByteArray()
            }

            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }
        }

        queue.add(request)

    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        while (!rf.setEPCMode()) {
        }
        if (rf.power != readingPower) {
            while (!rf.setPower(readingPower)) {
            }
        }
        readingInProgress = false
        processingInProgress = false

        showPropertiesToUser(0, beepMain)

        powerText.text = "اندازه توان($readingPower)"
        powerSeekBar.progress = readingPower - 5
    }

    @SuppressLint("SetTextI18n")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (updateDatabaseInProgress) {
            return true
        }

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            if (event.repeatCount == 0) {
                if (!readingInProgress) {
                    while (!rf.setPower(readingPower)) {
                    }
                    processingInProgress = false
                    readingInProgress = true
                    button.setBackgroundColor(Color.GRAY)
                    rf.startInventoryTag(0, 0, 0)
                    val params = window.attributes
                    params.screenBrightness = 0f
                    window.attributes = params
                    timerHandler.post(timerRunnable)
                } else {
                    timerHandler.removeCallbacks(timerRunnable)
                    rf.stopInventory()
                    val params = window.attributes
                    params.screenBrightness = -10f
                    window.attributes = params
                    readingInProgress = false
                    processingInProgress = true
                    status.text = "در حال پردازش ..."

                    timerHandler.postDelayed(timerRunnable, 500)
                }
            }
        } else if (keyCode == 4) {
            if (readingInProgress) {
                rf.stopInventory()
                readingInProgress = false
            }
            finish()
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        if (readingInProgress) {
            rf.stopInventory()
            readingInProgress = false
        }
    }

    @SuppressLint("SetTextI18n")
    fun sendFile(view: View?) {
        if (readingInProgress || processingInProgress) {
            return
        }

        updateDatabaseInProgress = true

        val queue = Volley.newRequestQueue(this)

        val url = "http://rfid-api-0-1.avakatan.ir/stock-drafts/$transferID/conflicts"

        val request = object : JsonObjectRequest(Method.POST, url, null,
            {
                conflicts = it
                updateDatabaseInProgress = false
                startActivity(Intent(this, ConfirmScanningResultActivity::class.java))

            }, {
                Toast.makeText(this, it.toString(), Toast.LENGTH_LONG).show()
            }) {
            override fun getBody(): ByteArray {

                return JSONArray(EPCTableValid.keys).toString().toByteArray()
            }

            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }
        }

        status.text = "در حال دریافت اطلاعات ..."
        queue.add(request)
    }

    @SuppressLint("SetTextI18n")
    fun clearAll(view: View?) {
        val alertDialog: AlertDialog
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setTitle("تمام اطلاعات قبلی پاک می شود")
        alertBuilder.setMessage("آیا ادامه می دهید؟")
        alertBuilder.setPositiveButton("بله") { dialog, which ->
            EPCTable.clear()
            EPCTableValid.clear()
            epcLastLength = 0
            showPropertiesToUser(0, beepMain)

        }
        alertBuilder.setNegativeButton("خیر") { dialog, which -> }
        alertDialog = alertBuilder.create()
        alertDialog.setOnShowListener {
            alertDialog.window!!.decorView.layoutDirection =
                View.LAYOUT_DIRECTION_RTL // set title and message direction to RTL
        }
        alertDialog.show()
    }

    @SuppressLint("SetTextI18n")
    fun showPropertiesToUser(speed: Int, beep: ToneGenerator) {

        status.text = "شماره حواله: " + " " + transferID + "\n"

        status.text = status.text.toString() + "سرعت اسکن (تگ بر ثانیه): " + speed + "\n"

        if (!readingInProgress) {
            status.text =
                status.text.toString() + "تعداد کالا های پیدا شده: " + EPCTable.size + "/" + allStuffs
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

        circularProgressBar.progress = (EPCTableValid.size * 100 / allStuffs).toFloat()
        percentage.text = (EPCTableValid.size * 100 / allStuffs).toFloat().toString() + '%'
    }

    companion object {

        internal var EPCTable: MutableMap<String, Int> = HashMap()

        internal var EPCTableValid: MutableMap<String, Int> = HashMap()

        internal var transferID = 0L

        internal var conflicts = JSONObject()
    }
}