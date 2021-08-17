package com.jeanwest.reader.warehouseScanning

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
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.R
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class WareHouseScanningFindingProduct : AppCompatActivity() {
    var api = GetProductEPCAPI()
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
    var subStuffs = JSONArray()
    var stuff = JSONObject()
    var epcTableFindingMatched: MutableMap<String, Int> = HashMap()
    var temp = ""
    var temp2 = JSONObject()
    lateinit var setting: WebSettings
    lateinit  var update: Button
    lateinit var table: SharedPreferences
    private lateinit var tableEditor: SharedPreferences.Editor
    var isChecked = true
    var findingInProgress = false
    var updateDatabaseInProgress = false
    var readEnable = false
    var apiReadingEPC = WarehouseScanningSendingEPCsAPI()
    var apiReadingConflicts = WarehouseScanningReadingConflictsAPI()
    var databaseInProgress = false
    var receivingData = false

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
                var uhftagInfo: UHFTAGInfo?
                while (true) {
                    uhftagInfo = rf.readTagFromBuffer()
                    if (uhftagInfo != null) {
                        epcTableFinding[uhftagInfo.epc] = 1
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
                    status.text = ""
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
            if (databaseInProgress) {
                if (!receivingData) {
                    if (apiReadingEPC.run) {
                        status.text = "در حال ارسال به سرور "
                        databaseBackgroundTaskHandler.postDelayed(this, 1000)
                    } else {
                        if (apiReadingEPC.status) {
                            apiReadingConflicts.start()
                            receivingData = true
                            databaseBackgroundTaskHandler.postDelayed(this, 1000)
                        } else {
                            databaseInProgress = false
                            status.text = "خطا در دیتابیس " + apiReadingEPC.response
                        }
                    }
                } else {
                    if (apiReadingConflicts.run) {
                        status.text = "در حال دریافت اطلاعات از سرور "
                        databaseBackgroundTaskHandler.postDelayed(this, 1000)
                    } else {
                        receivingData = false
                        databaseInProgress = false
                        if (apiReadingConflicts.status) {
                            try {
                                WarehouseScanningActivity.conflicts = apiReadingConflicts.conflicts
                                val stuffs = WarehouseScanningActivity.conflicts.names()
                                var i = 0
                                while (i < stuffs.length()) {
                                    temp = stuffs.getString(i)
                                    subStuffs =
                                        WarehouseScanningActivity.conflicts.getJSONArray(temp)
                                    var j = 0
                                    while (j < subStuffs.length()) {
                                        temp2 = subStuffs.getJSONObject(j)
                                        if (temp2.getString("BarcodeMain_ID") == stuffPrimaryCode) {
                                            WarehouseScanningResultActivity.index =
                                                stuffs.getString(i)
                                            WarehouseScanningSubResultActivity.subIndex = j
                                            i = stuffs.length() + 10
                                            j = subStuffs.length() + 10
                                        }
                                        j++
                                    }
                                    i++
                                }
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                            epcTableFinding.clear()
                            epcTableFindingMatched.clear()
                            status.text = ""
                            onResume()
                        } else {
                            status.text = "خطا در دیتابیس " + apiReadingEPC.response
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading_result_sub_sub)
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
                    powerText.setText("قدرت سیگنال($findingPower)")
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
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        updateDatabaseInProgress = false
        while (!rf.setPower(findingPower)) {
        }
        try {
            subStuffs =
                WarehouseScanningActivity.conflicts.getJSONArray(WarehouseScanningResultActivity.index)
            stuff = subStuffs.getJSONObject(WarehouseScanningSubResultActivity.subIndex)
            if (stuff.getBoolean("status")) {
                stuffSpec.text = """
                ${stuff.getString("productName")}
                کد محصول: ${stuff.getString("K_Bar_Code")}
                بارکد: ${stuff.getString("KBarCode")}
                تعداد اضافی: ${stuff.getString("diffCount")}
                تعداد اسکن شده: ${stuff.getString("handheldCount")}
                تعداد کل: ${stuff.getString("dbCount")}
                """.trimIndent()
            } else {
                stuffSpec.text = """
                ${stuff.getString("productName")}
                کد محصول: ${stuff.getString("K_Bar_Code")}
                بارکد: ${stuff.getString("KBarCode")}
                تعداد اسکن نشده: ${stuff.getString("diffCount")}
                تعداد اسکن شده: ${stuff.getString("handheldCount")}
                تعداد کل: ${stuff.getString("dbCount")}
                """.trimIndent()
            }
            picture.loadUrl(stuff.getString("ImgUrl"))
            stuffPrimaryCode = stuff.getString("BarcodeMain_ID")
            stuffRFIDCode = stuff.getString("RFID")
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        setting = picture.settings
        setting.useWideViewPort = true
        setting.loadWithOverviewMode = true
        picture.isFocusable = false
        api = GetProductEPCAPI()
        api.id = WarehouseScanningActivity.ID.toString()
        api.primaryCode = stuffPrimaryCode
        api.rfidCode = stuffRFIDCode
        api.start()
        while (api.run) {
        }

        status.text = "${status.text}" + "\n${api.response}"

        databaseInProgress = false
        when (findingPower) {
            5 -> powerSeekBar.progress = 0
            10 -> powerSeekBar.progress = 1
            15 -> powerSeekBar.progress = 2
            20 -> powerSeekBar.progress = 3
            30 -> powerSeekBar.progress = 4
        }
        powerText.text = "قدرت سیگنال($findingPower)"
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
            if (readEnable) {
                rf.stopInventory()
                readEnable = false
            }
            findingInProgress = false
            databaseBackgroundTaskHandler.removeCallbacks(databaseBackgroundTask)
            finish()
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
        WarehouseScanningActivity.EPCTableValid.putAll(epcTableFindingMatched)
        val tableJson = JSONObject(WarehouseScanningActivity.EPCTableValid as Map<*, *>)
        tableEditor.putString(
            java.lang.String.valueOf(WarehouseScanningActivity.warehouseID),
            tableJson.toString()
        )
        tableEditor.commit()
        apiReadingEPC = WarehouseScanningSendingEPCsAPI()
        apiReadingEPC.id = WarehouseScanningActivity.ID
        apiReadingEPC.data.putAll(WarehouseScanningActivity.EPCTableValid)
        apiReadingConflicts = WarehouseScanningReadingConflictsAPI()
        apiReadingConflicts.id = WarehouseScanningActivity.ID
        apiReadingEPC.start()
        databaseInProgress = true
        databaseBackgroundTaskHandler.post(databaseBackgroundTask)
    }

    fun optionChange(view: View?) {
        isChecked = option.isChecked
    }
}