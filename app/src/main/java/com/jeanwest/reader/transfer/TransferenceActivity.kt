package com.jeanwest.reader.transfer

import android.annotation.SuppressLint
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
import android.widget.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest

import com.android.volley.toolbox.Volley
import com.jeanwest.reader.Barcode2D
import com.jeanwest.reader.IBarcodeResult
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.rscja.deviceapi.RFIDWithUHFUART
//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.android.synthetic.main.activity_transfer.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import com.android.volley.DefaultRetryPolicy


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

    val apiTimeout = 20000
    var sum = 0

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

                val url = "http://rfid-api-0-1.avakatan.ir/products/v3"

                val request = object : JsonObjectRequest(Method.POST, url, null,  {

                    var template: JSONObject
                    val titles = ArrayList<String>()
                    val specs = ArrayList<String>()
                    val size = ArrayList<String>()
                    val color = ArrayList<String>()
                    val scannedNumber = ArrayList<String>()
                    val pictureURL = ArrayList<String>()

                    var response = it.getJSONArray("epcs")

                    sum = 0

                    for(i in 0 until response.length()) {
                        try {
                            template = response.getJSONObject(i)
                            titles.add(template.getString("productName"))
                            size.add("اندازه: " + template.getString("Size"))
                            color.add("رنگ: " + template.getString("Color"))
                            scannedNumber.add("تعداد: " + template.getString("handheldCount"))
                            pictureURL.add(template.getString("ImgUrl"))
                            specs.add("کد محصول: " + template.getString("KBarCode") + "\n" + "اسکن شده با RF")
                            sum += template.getString("handheldCount").toInt()

                        } catch (ignored: JSONException) {

                        }
                    }

                    response = it.getJSONArray("KBarCodes")

                    for (i in 0 until response.length()) {
                        try {
                            template = response.getJSONObject(i)
                            titles.add(template.getString("productName"))
                            size.add("اندازه: " + template.getString("Size"))
                            color.add("رنگ: " + template.getString("Color"))
                            scannedNumber.add("تعداد: " + template.getString("handheldCount"))
                            pictureURL.add(template.getString("ImgUrl"))
                            specs.add("کد محصول: " + template.getString("KBarCode") + "\n" + "اسکن شده با بارکد")
                            sum += template.getString("handheldCount").toInt()

                        } catch (ignored: JSONException) {

                        }
                    }

                    titles.add(0, "نتایج")
                    size.add(0, "مجموع اجناس: $sum")
                    color.add(0, " ")
                    specs.add(0, " ")
                    pictureURL.add(0, " ")
                    scannedNumber.add(0, "مجموع تگ های خراب: " + (epcTableValid.size + barcodeTable.size - sum))

                    val listAdapter = MyListAdapterTransfer(this@TransferenceActivity, titles, specs, size, color, scannedNumber, pictureURL)
                    result.adapter = listAdapter

                }, { response ->
                    Toast.makeText(this@TransferenceActivity, response.toString(), Toast.LENGTH_LONG).show()
                }) {
                    override fun getHeaders(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params["Content-Type"] = "application/json;charset=UTF-8"
                        params["Authorization"] = "Bearer " + MainActivity.token
                        return params
                    }

                    override fun getBody(): ByteArray {
                        val json = JSONObject()

                        val epcArray = JSONArray()
                        for ((key) in epcTableValid) {
                            epcArray.put(key)
                        }

                        json.put("epcs", epcArray)

                        val barcodeArray = JSONArray()

                        barcodeTable.forEach{
                            barcodeArray.put(it)
                        }

                        json.put("KBarCodes", barcodeArray)

                        return json.toString().toByteArray()
                    }
                }

                request.retryPolicy = DefaultRetryPolicy(
                    apiTimeout,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                )

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
        setContentView(R.layout.activity_transfer)

        val powerText = findViewById<TextView>(R.id.readingPowerTextViewT)
        val powerSeekBar = findViewById<SeekBar>(R.id.readingPowerSeekBarT)
        button = findViewById(R.id.transfer_send_button)
        status = findViewById(R.id.section_labelT)
        result = findViewById(R.id.confirm_list)

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

        transfer_toolbar.setNavigationOnClickListener {
            back()
        }

    }

    private fun back() {
        close()
        if (isScanning) {
            rf.stopInventory()
            isScanning = false
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        open()
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
            back()
        } else if (keyCode == 139) {
            start()
        }
        return true
    }

    fun sendFile(view: View) {

        epcTableValid.keys.forEach {
            Log.e("epcs", it)
        }

        if((epcTableValid.size + barcodeTable.size) != sum)
        {
            Toast.makeText(this, "تعدادی تگ خراب در حواله وجود دارد", Toast.LENGTH_LONG).show()
            return
        }

        val queue = Volley.newRequestQueue(this)

        val request = object : StringRequest(
            Method.POST, "http://rfid-api-0-1.avakatan.ir/stock-drafts",
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

                val epcArray = JSONArray()
                for ((key) in epcTableValid) {
                    epcArray.put(key)
                }

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                barcodeTable.forEach{
                    barcodeArray.put(it)
                }

                json.put("KBarCodes", barcodeArray)

                return json.toString().toByteArray()
            }

            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }
        }
        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        queue.add(request)

    }
    fun clearAll(view: View) {
        epcLastLength = 0
        epcTableValid.clear()
        barcodeTable.clear()
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
                status.text.toString() + "تعداد کالا های پیدا شده: " + (epcTableValid.size + barcodeTable.size)
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
            showPropertiesToUser(0, beepMain)
            beepMain.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            processingInProgress = true
            isScanning = false
            timerHandler.post(timerRunnable)
        }
    }

    private fun start() {

        barcode2D.startScan(this)
    }

    private fun open() {
        barcode2D.open(this, this)
    }

    private fun close() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
    }
}