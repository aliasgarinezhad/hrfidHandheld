package com.jeanwest.reader.confirm

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.android.synthetic.main.activity_confirm_find.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class ConfirmScanningFindingProduct : AppCompatActivity() {

    lateinit var rf: RFIDWithUHFUART
    var epcTableFinding: MutableMap<String, Int> = HashMap()
    var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    var findingPower = 30
    lateinit var status: TextView
    lateinit var powerText: TextView
    lateinit var powerSeekBar: SeekBar
    lateinit var numberOfFoundText: TextView
    lateinit var stuffSpec: TextView
    var stuffCode: Long = 0
    lateinit var picture: WebView
    lateinit var option: CheckBox
    var stuffPrimaryCode = ""
    var stuffRFIDCode = ""
    var stuff = JSONObject()
    var epcTableFindingMatched: MutableMap<String, Int> = HashMap()
    lateinit var setting: WebSettings
    lateinit  var update: Button
    lateinit var table: SharedPreferences
    private lateinit var tableEditor: SharedPreferences.Editor
    var isChecked = true
    var findingInProgress = false
    var updateDatabaseInProgress = false
    var readEnable = false
    var databaseInProgress = false

    var header = 0
    var companyNumber = 0
    var itemNumber = 0L
    var flag = false
    var epcInt1 = 0L
    var epcInt2 = 0L
    var epcInt3 = 0L
    var epcHexString = ""
    var epcBinaryString = ""
    var epcBinaryString1 = ""
    var epcBinaryString2 = ""
    var epcBinaryString3 = ""

    var databaseBackgroundTaskHandler = Handler()

    private var databaseBackgroundTask: Runnable = object : Runnable {

        @SuppressLint("SetTextI18n")
        override fun run() {

            if (findingInProgress) {
                var uhfTagInfo: UHFTAGInfo?
                while (true) {
                    uhfTagInfo = rf.readTagFromBuffer()
                    if (uhfTagInfo != null) {
                        epcTableFinding[uhfTagInfo.epc] = 1
                    } else {
                        break
                    }
                }
                if (epcTableFinding.isNotEmpty()) {
                    flag = false
                    for ((key) in epcTableFinding) {
                        epcHexString = key
                        epcInt1 = epcHexString.substring(0, 8).toLong(16)
                        epcInt2 = epcHexString.substring(8, 16).toLong(16)
                        epcInt3 = epcHexString.substring(16, 24).toLong(16)
                        epcBinaryString1 = java.lang.Long.toBinaryString(epcInt1)
                        epcBinaryString1 =
                            String.format("%32s", epcBinaryString1).replace(" ".toRegex(), "0")
                        epcBinaryString2 = java.lang.Long.toBinaryString(epcInt2)
                        epcBinaryString2 =
                            String.format("%32s", epcBinaryString2).replace(" ".toRegex(), "0")
                        epcBinaryString3 = java.lang.Long.toBinaryString(epcInt3)
                        epcBinaryString3 =
                            String.format("%32s", epcBinaryString3).replace(" ".toRegex(), "0")
                        epcBinaryString = epcBinaryString1 + epcBinaryString2 + epcBinaryString3
                        header = epcBinaryString.substring(0, 8).toInt(2)
                        companyNumber = epcBinaryString.substring(14, 26).toInt(2)
                        itemNumber = epcBinaryString.substring(26, 58).toLong(2)
                        if (companyNumber == 100) {
                            stuffCode = stuffPrimaryCode.toLong()
                        } else if (companyNumber == 101) {
                            stuffCode = stuffRFIDCode.toLong()
                        }
                        if (header == 48 && itemNumber == stuffCode) {
                            epcTableFindingMatched[epcHexString] = 1
                            flag = true
                        }
                    }
                    numberOfFoundText.text = epcTableFindingMatched.size.toString()
                    status.text = "در حال جست و جو ..."
                    for ((key) in epcTableFindingMatched) {
                        status.text = "${status.text}" + "\n$key"
                    }
                    if (flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    }
                    epcTableFinding.clear()
                    databaseBackgroundTaskHandler.postDelayed(this, 1000)
                } else {
                    databaseBackgroundTaskHandler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_find)
        status = findViewById(R.id.section_label)
        stuffSpec = findViewById(R.id.result)
        picture = findViewById(R.id.pictureView)
        powerText = findViewById(R.id.findingPowerTextView)
        powerSeekBar = findViewById(R.id.findingPowerSeekBar)
        numberOfFoundText = findViewById(R.id.numberOfFoundTextView)
        option = findViewById(R.id.checkBox)
        update = findViewById(R.id.updateButton)
        powerSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!readEnable) {
                    when (progress) {
                        0 -> findingPower = 5
                        1 -> findingPower = 10
                        2 -> findingPower = 15
                        3 -> findingPower = 20
                        4 -> findingPower = 30
                    }
                    powerText.text = "قدرت سیگنال($findingPower)"
                } else {
                    rf.stopInventory()
                    when (progress) {
                        0 -> findingPower = 5
                        1 -> findingPower = 10
                        2 -> findingPower = 15
                        3 -> findingPower = 20
                        4 -> findingPower = 30
                    }
                    powerText.text = "قدرت سیگنال($findingPower)"
                    while (!rf.setPower(findingPower)) {
                    }
                    rf.startInventoryTag(0, 0, 0)
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
        table = PreferenceManager.getDefaultSharedPreferences(this)
        tableEditor = table.edit()
        confirm_find_toolbar.setNavigationOnClickListener {
            back()
        }
    }

    private fun back() {
        if (readEnable) {
            rf.stopInventory()
            readEnable = false
        }
        findingInProgress = false
        databaseBackgroundTaskHandler.removeCallbacks(databaseBackgroundTask)
        finish()
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        while (!rf.setPower(findingPower)) {
        }
        stuffRFIDCode = intent.getStringExtra("productRFIDCode")!!
        stuff = getProduct(stuffRFIDCode)

        try {

            if (stuff.getBoolean("status")) {
                stuffSpec.text = stuff.getString("productName") + "\n" + """
                    کد محصول: ${stuff.getString("K_Bar_Code")}
                    بارکد: ${stuff.getString("KBarCode")}
                   تعداد اضافی:  ${(stuff.getInt("handheldCount") - stuff.getInt("dbCount"))}
                    تعداد اسکن شده: ${stuff.getString("handheldCount")}
                    تعداد کل: ${stuff.getString("dbCount")}
                    """.trimIndent()
            } else {
                stuffSpec.text = stuff.getString("productName") + "\n" +  """
                    کد محصول: ${stuff.getString("K_Bar_Code")}
                    بارکد: ${stuff.getString("KBarCode")}
                   تعداد اسکن نشده:  ${(stuff.getInt("dbCount") - stuff.getInt("handheldCount"))}
                    تعداد اسکن شده: ${stuff.getString("handheldCount")}
                    تعداد کل: ${stuff.getString("dbCount")}
                    """.trimIndent()
            }
            picture.loadUrl(stuff.getString("ImgUrl"))
            stuffPrimaryCode = stuff.getString("BarcodeMain_ID")

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        setting = picture.settings
        setting.useWideViewPort = true
        setting.loadWithOverviewMode = true
        picture.isFocusable = false

        databaseInProgress = false
        when (findingPower) {
            5 -> powerSeekBar.progress = 0
            10 -> powerSeekBar.progress = 1
            15 -> powerSeekBar.progress = 2
            20 -> powerSeekBar.progress = 3
            30 -> powerSeekBar.progress = 4
        }
        powerText.text = "قدرت سیگنال($findingPower)"
        clearEPCs(View(this))
    }

    @SuppressLint("SetTextI18n")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (updateDatabaseInProgress) {
            return true
        }
        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            if (event.repeatCount == 0) {
                status.text = ""
                if (!readEnable) {
                    update.setBackgroundColor(Color.GRAY)
                    epcTableFinding.clear()
                    if (!isChecked) {
                        epcTableFindingMatched.clear()
                    }
                    while (!rf.setPower(findingPower)) {
                    }
                    readEnable = true
                    rf.startInventoryTag(0, 0, 0)
                    status.text = "در حال جست و جو ..."
                    findingInProgress = true
                    databaseBackgroundTaskHandler.postDelayed(databaseBackgroundTask, 1000)
                } else {
                    update.setBackgroundColor(getColor(R.color.Primary))
                    databaseBackgroundTaskHandler.removeCallbacks(databaseBackgroundTask)
                    readEnable = false
                    rf.stopInventory()
                    findingInProgress = false
                    flag = false
                    for ((key) in epcTableFinding) {
                        epcHexString = key
                        epcInt1 = epcHexString.substring(0, 8).toLong(16)
                        epcInt2 = epcHexString.substring(8, 16).toLong(16)
                        epcInt3 = epcHexString.substring(16, 24).toLong(16)
                        epcBinaryString1 = java.lang.Long.toBinaryString(epcInt1)
                        epcBinaryString1 =
                            String.format("%32s", epcBinaryString1).replace(" ".toRegex(), "0")
                        epcBinaryString2 = java.lang.Long.toBinaryString(epcInt2)
                        epcBinaryString2 =
                            String.format("%32s", epcBinaryString2).replace(" ".toRegex(), "0")
                        epcBinaryString3 = java.lang.Long.toBinaryString(epcInt3)
                        epcBinaryString3 =
                            String.format("%32s", epcBinaryString3).replace(" ".toRegex(), "0")
                        epcBinaryString = epcBinaryString1 + epcBinaryString2 + epcBinaryString3
                        header = epcBinaryString.substring(0, 8).toInt(2)
                        companyNumber = epcBinaryString.substring(14, 26).toInt(2)
                        itemNumber = epcBinaryString.substring(26, 58).toLong(2)
                        if (companyNumber == 100) {
                            stuffCode = stuffPrimaryCode.toLong()
                        } else if (companyNumber == 101) {
                            stuffCode = stuffRFIDCode.toLong()
                        }
                        if (header == 48 && itemNumber == stuffCode) {
                            epcTableFindingMatched[epcHexString] = 1
                            flag = true
                        }
                    }
                    numberOfFoundText.text = epcTableFindingMatched.size.toString()
                    status.text = ""
                    for ((key) in epcTableFindingMatched) {
                        status.text = """
                            ${status.text}
                            $key
                            """.trimIndent()
                    }
                    if (flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    }
                    epcTableFinding.clear()
                }
            }
        } else if (keyCode == 4) {
            back()
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        if (readEnable) {
            rf.stopInventory()
            readEnable = false
        }
    }

    fun clearEPCs(view: View?) {
        epcTableFinding.clear()
        epcTableFindingMatched.clear()
        status.text = ""
        numberOfFoundText.text = "0"
    }

    @SuppressLint("SetTextI18n")
    fun updateDatabase(view: View?) {
        if (findingInProgress) {
            return
        }
        updateDatabaseInProgress = true
        ConfirmScanningActivity.epcTableValid.putAll(epcTableFindingMatched)

        val queue = Volley.newRequestQueue(this)

        val url = "http://rfid-api-0-1.avakatan.ir/stock-drafts/${ConfirmScanningActivity.transferID}/conflicts"

        val request = object : JsonObjectRequest(
            Method.POST, url, null,
            {
                ConfirmScanningActivity.conflicts = it
                updateDatabaseInProgress = false
                onResume()

            }, {
                Toast.makeText(this, it.toString(), Toast.LENGTH_LONG).show()
            }) {
            override fun getBody(): ByteArray {

                return JSONArray(ConfirmScanningActivity.epcTableValid.keys).toString().toByteArray()
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

    fun optionChange(view: View?) {
        isChecked = option.isChecked
    }

    private fun getProduct(productRFIDCode : String) : JSONObject {

        var products = ConfirmScanningActivity.conflicts.getJSONArray("shortage")
        var product : JSONObject
        for(i in 0 until products.length()) {

            product = products.getJSONObject(i)
            if(product.getString("RFID").equals(productRFIDCode)) {
                product.put("status", false)
                return product
            }
        }

        products = ConfirmScanningActivity.conflicts.getJSONArray("additional")
        for (i in 0 until products.length()) {

            product = products.getJSONObject(i)
            if(product.getString("RFID").equals(productRFIDCode)) {
                product.put("status", true)
                return product
            }
        }
        return JSONObject()
    }
}