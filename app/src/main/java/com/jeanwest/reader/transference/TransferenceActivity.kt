package com.jeanwest.reader.transference

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.*
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.StringRequest

import com.android.volley.toolbox.Volley
import com.jeanwest.reader.Barcode2D
import com.jeanwest.reader.IBarcodeResult
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TransferenceActivity : AppCompatActivity(), IBarcodeResult {

    private val barcode2D = Barcode2D(this)
    var power = 30
    private var isScanning = false
    var beepMain = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    var timerHandler = Handler(Looper.getMainLooper())
    lateinit var rf: RFIDWithUHFUART
    var epcTableValid: MutableMap<String, Int> = HashMap()
    var epcLastLength = 0
    private var processingInProgress = false
    lateinit var button: Button
    lateinit var status : TextView
    var source = 0;
    var des = 0;
    var explanation = ""
    var barcodeTable = ArrayList<String>()

    lateinit var result: ListView

    private var timerRunnable: Runnable = object : Runnable {

        @SuppressLint("SetTextI18n", "ResourceAsColor")
        override fun run() {

            if (isScanning) {

                var uhfTagInfo: UHFTAGInfo?
                while (true) {
                    uhfTagInfo = rf.readTagFromBuffer()
                    if (uhfTagInfo != null && uhfTagInfo.epc.startsWith("30")) {
                        epcTableValid[uhfTagInfo.epc] = 1
                    } else {
                        break
                    }
                }

                showPropertiesToUser(epcTableValid.size - epcLastLength, beepMain)
                epcLastLength = epcTableValid.size

                timerHandler.postDelayed(this, 1000)

            } else if (processingInProgress) {

                showPropertiesToUser(0, beepMain)

                var url = "http://rfid-api-0-1.avakatan.ir/products/v2?"
                for (key in epcTableValid) {
                    url += "epc=" + key.key + "&"
                }

                val request = JsonArrayRequest(Request.Method.GET, url, null,  {
                    response ->

                    var template: JSONObject
                    val titles = ArrayList<String>()
                    val specs = ArrayList<String>()
                    val size = ArrayList<String>()
                    val color = ArrayList<String>()
                    val scannedNumber = ArrayList<String>()
                    val pictureURL = ArrayList<String>()

                    for(i in 0 until response.length()) {
                        try {
                            template = response.getJSONObject(i)
                            titles.add(template.getString("productName"))
                            size.add("اندازه: " + template.getString("Size"))
                            color.add("رنگ: " + template.getString("Color"))
                            scannedNumber.add("تعداد: " + template.getString("handheldCount"))
                            pictureURL.add(template.getString("ImgUrl"))
                            specs.add("کد محصول: " + template.getString("KBarCode"))

                        } catch (ignored: JSONException) {

                        }
                    }

                    val listAdapter = MyListAdapterTransfer(this@TransferenceActivity, titles, specs, size, color, scannedNumber, pictureURL)
                    result.adapter = listAdapter

                }, { response ->
                    Toast.makeText(this@TransferenceActivity, response.toString(), Toast.LENGTH_LONG).show()
                })

                val queue = Volley.newRequestQueue(this@TransferenceActivity)
                queue.add(request)

                isScanning = false
                processingInProgress = false
                button.setBackgroundColor(getColor(R.color.Primary))
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
        result = findViewById(R.id.listViewT)

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

        epcTableValid.clear()

        epcLastLength = epcTableValid.size
        while (!rf.setEPCMode()) {
        }
        if (rf.power != power) {
            while (!rf.setPower(power)) {
            }
        }
        isScanning = false
        processingInProgress = false

        source = intent.getIntExtra("source", 0)
        des = intent.getIntExtra("des", 0)
        explanation = intent.getStringExtra("explanation")!!

        showPropertiesToUser(0, beepMain)

        powerText.text = "اندازه توان($power)"
        powerSeekBar.progress = power - 5

    }

    override fun onResume() {
        super.onResume()
        open()
    }

    override fun onPause() {
        super.onPause()
        close()
    }

    @SuppressLint("SetTextI18n")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == 280 || keyCode == 293) {

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
            close()
            if (isScanning) {
                rf.stopInventory()
                isScanning = false
            }
            finish()
        } else if (keyCode == 139) {
            start()
        }
        return true
    }

    fun sendFile(view: View) {

        val queue = Volley.newRequestQueue(this)

        val request = object : StringRequest(Request.Method.POST, "http://rfid-api-0-1.avakatan.ir/stock-drafts",
            { response ->
                Toast.makeText(this, "حواله با موفقیت ثبت شد" + "\n" + "شماره حواله: "
                        + JSONObject(response).getString("stockDraftId")
                    , Toast.LENGTH_LONG).show()
            }, {response ->
                Toast.makeText(this, response.toString(), Toast.LENGTH_LONG).show()
        }) {

            override fun getBody(): ByteArray {
                val json = JSONObject()
                json.put("SourceWareHouse_ID", source)
                json.put("DestWareHouse_ID", des)
                json.put("StockDraftDescription", explanation)
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.UK)
                val formattedDate = sdf.format(Date())
                json.put("CreateDate", formattedDate)

                val temp = JSONArray()
                for ((key) in epcTableValid) {
                    temp.put(key)
                }

                json.put("epcs", temp)

                return json.toString().toByteArray()
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
    fun clearAll(view: View) {
        epcLastLength = 0
        epcTableValid.clear()
        showPropertiesToUser(0, beepMain)
        var listAdapter = MyListAdapterTransfer(this@TransferenceActivity, ArrayList<String>(),
            ArrayList<String>(), ArrayList<String>(), ArrayList<String>(), ArrayList<String>(), ArrayList<String>())
        result.adapter = listAdapter

    }

    @SuppressLint("SetTextI18n")
    fun showPropertiesToUser(speed: Int, beep: ToneGenerator) {

        status.text = "حواله از " + source + " به " + des + "\n"

        status.text = status.text.toString() + "توضیحات: " + explanation + "\n"

        status.text = status.text.toString() + "سرعت اسکن (تگ بر ثانیه): " + speed + "\n"

        if (!isScanning) {
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

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            barcodeTable.add(barcode)
        }
    }

    private fun start() {

        barcode2D.startScan(this)
    }

    fun stop() {
        barcode2D.stopScan(this)
    }

    private fun open() {
        barcode2D.open(this, this)
    }

    fun close() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
    }
}