package com.jeanwest.reader

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
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class WareHouseScanningFindingProduct : AppCompatActivity() {
    var api: GetProductEPCAPI? = null
    var RF: RFIDWithUHFUART? = null
    var EPCTableFinding: MutableMap<String, Int> = HashMap()
    var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    var findingPower = 30
    var status: TextView? = null
    lateinit var powerText: TextView
    lateinit var powerSeekBar: SeekBar
    var numberOfFoundText: TextView? = null
    var stuffSpec: TextView? = null
    var stuffCode: Long = 0
    var picture: WebView? = null
    var option: CheckBox? = null
    var stuffPrimaryCode: String? = null
    var stuffRFIDCode: String? = null
    var subStuffs = JSONArray()
    var stuff = JSONObject()
    var EPCTableFindingMatched: MutableMap<String, Int> = HashMap()
    var temp = ""
    var temp2 = JSONObject()
    var setting: WebSettings? = null
    var update: Button? = null
    lateinit var table: SharedPreferences
    var tableEditor: SharedPreferences.Editor? = null
    var isChecked = true
    var findingInProgress = false
    var updateDatabaseInProgress = false
    var readEnable = false
    var apiReadingEPC = WarehouseScanningSendingEPCsAPI()
    var apiReadingConflicts = WarehouseScanningReadingConflictsAPI()
    var databaseInProgress = false
    var receivingData = false
    var databaseBackgroundTaskHandler = Handler()
    var databaseBackgroundTask: Runnable = object : Runnable {
        @SuppressLint("SetTextI18n")
        override fun run() {
            if (findingInProgress) {
                var uhftagInfo: UHFTAGInfo?
                while (true) {
                    uhftagInfo = RF!!.readTagFromBuffer()
                    if (uhftagInfo != null) {
                        EPCTableFinding[uhftagInfo.epc] = 1
                    } else {
                        break
                    }
                }
                if (EPCTableFinding.size > 0) {
                    flag = false
                    for ((key) in EPCTableFinding) {
                        EPCHexString = key
                        EPCInt1 = EPCHexString!!.substring(0, 8).toLong(16)
                        EPCInt2 = EPCHexString!!.substring(8, 16).toLong(16)
                        EPCInt3 = EPCHexString!!.substring(16, 24).toLong(16)
                        EPCBinaryString1 = java.lang.Long.toBinaryString(EPCInt1)
                        EPCBinaryString1 =
                            String.format("%32s", EPCBinaryString1).replace(" ".toRegex(), "0")
                        EPCBinaryString2 = java.lang.Long.toBinaryString(EPCInt2)
                        EPCBinaryString2 =
                            String.format("%32s", EPCBinaryString2).replace(" ".toRegex(), "0")
                        EPCBinaryString3 = java.lang.Long.toBinaryString(EPCInt3)
                        EPCBinaryString3 =
                            String.format("%32s", EPCBinaryString3).replace(" ".toRegex(), "0")
                        EPCBinaryString = EPCBinaryString1 + EPCBinaryString2 + EPCBinaryString3
                        header = EPCBinaryString!!.substring(0, 8).toInt(2)
                        companyNumber = EPCBinaryString!!.substring(14, 26).toInt(2)
                        itemNumber = EPCBinaryString!!.substring(26, 58).toLong(2)
                        if (companyNumber == 100) {
                            stuffCode = stuffPrimaryCode!!.toLong()
                        } else if (companyNumber == 101) {
                            stuffCode = stuffRFIDCode!!.toLong()
                        }
                        if (header == 48 && itemNumber == stuffCode) {
                            EPCTableFindingMatched[EPCHexString!!] = 1
                            flag = true
                        }
                    }
                    numberOfFoundText!!.text = EPCTableFindingMatched.size.toString()
                    status!!.text = ""
                    status!!.text = "در حال جست و جو ..."
                    for ((key) in EPCTableFindingMatched) {
                        status!!.text = """
                            ${status!!.text}
                            $key
                            """.trimIndent()
                    }
                    if (flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    }
                    EPCTableFinding.clear()
                    databaseBackgroundTaskHandler.postDelayed(this, 1000)
                } else {
                    databaseBackgroundTaskHandler.postDelayed(this, 1000)
                }
            }
            if (databaseInProgress) {
                if (!receivingData) {
                    if (apiReadingEPC.run) {
                        status!!.text = "در حال ارسال به سرور "
                        databaseBackgroundTaskHandler.postDelayed(this, 1000)
                    } else {
                        if (apiReadingEPC.status) {
                            apiReadingConflicts.start()
                            receivingData = true
                            databaseBackgroundTaskHandler.postDelayed(this, 1000)
                        } else {
                            databaseInProgress = false
                            status!!.text = "خطا در دیتابیس " + apiReadingEPC.response
                        }
                    }
                } else {
                    if (apiReadingConflicts.run) {
                        status!!.text = "در حال دریافت اطلاعات از سرور "
                        databaseBackgroundTaskHandler.postDelayed(this, 1000)
                    } else {
                        receivingData = false
                        databaseInProgress = false
                        if (apiReadingConflicts.status) {
                            try {
                                WarehouseScanningActivity.conflicts = apiReadingConflicts.conflicts
                                val stuffs = WarehouseScanningActivity.conflicts.names()
                                var i = 0
                                while (i < stuffs!!.length()) {
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
                            EPCTableFinding.clear()
                            EPCTableFindingMatched.clear()
                            status!!.text = ""
                            onResume()
                        } else {
                            status!!.text = "خطا در دیتابیس " + apiReadingEPC.response
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
                    RF!!.stopInventory()
                    when (progress) {
                        0 -> findingPower = 5
                        1 -> findingPower = 10
                        2 -> findingPower = 15
                        3 -> findingPower = 20
                        4 -> findingPower = 30
                    }
                    powerText.setText("قدرت سیگنال($findingPower)")
                    while (!RF!!.setPower(findingPower)) {
                    }
                    RF!!.startInventoryTag(0, 0, 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        try {
            RF = RFIDWithUHFUART.getInstance()
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
        while (!RF!!.setPower(findingPower)) {
        }
        try {
            subStuffs =
                apiReadingConflicts.conflicts.getJSONArray(WarehouseScanningResultActivity.index)
            stuff = subStuffs.getJSONObject(WarehouseScanningSubResultActivity.subIndex)
            if (stuff.getBoolean("status")) {
                stuffSpec!!.text = """
                    ${stuff.getString("productName")}
                    کد محصول: ${stuff.getString("K_Bar_Code")}
                    بارکد: ${stuff.getString("KBarCode")}
                    تعداد اضافی: ${stuff.getString("diffCount")}
                    تعداد اسکن شده: ${stuff.getString("handheldCount")}
                    تعداد کل: ${stuff.getString("dbCount")}
                    """.trimIndent()
            } else {
                stuffSpec!!.text = """
                    ${stuff.getString("productName")}
                    کد محصول: ${stuff.getString("K_Bar_Code")}
                    بارکد: ${stuff.getString("KBarCode")}
                    تعداد اسکن نشده: ${stuff.getString("diffCount")}
                    تعداد اسکن شده: ${stuff.getString("handheldCount")}
                    تعداد کل: ${stuff.getString("dbCount")}
                    """.trimIndent()
            }
            picture!!.loadUrl(stuff.getString("ImgUrl"))
            stuffPrimaryCode = stuff.getString("BarcodeMain_ID")
            stuffRFIDCode = stuff.getString("RFID")
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        setting = picture!!.settings
        setting!!.useWideViewPort = true
        setting!!.loadWithOverviewMode = true
        picture!!.isFocusable = false
        api = GetProductEPCAPI()
        api!!.id = WarehouseScanningActivity.ID.toString()
        api!!.primaryCode = stuffPrimaryCode!!
        api!!.rfidCode = stuffRFIDCode!!
        api!!.start()
        while (api!!.run) {
        }
        if (!api!!.status) {
            stuffSpec!!.text = """
                ${stuffSpec!!.text}
                ${api!!.response}
                """.trimIndent()
        } else {
            stuffSpec!!.text = """
                ${stuffSpec!!.text}
                ${api!!.response}
                """.trimIndent()
        }
        databaseInProgress = false
        when (findingPower) {
            5 -> powerSeekBar!!.progress = 0
            10 -> powerSeekBar!!.progress = 1
            15 -> powerSeekBar!!.progress = 2
            20 -> powerSeekBar!!.progress = 3
            30 -> powerSeekBar!!.progress = 4
        }
        powerText!!.text = "قدرت سیگنال($findingPower)"
    }

    var header = 0
    var companyNumber = 0
    var itemNumber: Long = 0
    var flag = false
    var EPCInt1: Long = 0
    var EPCInt2: Long = 0
    var EPCInt3: Long = 0
    var EPCHexString: String? = null
    var EPCBinaryString: String? = null
    var EPCBinaryString1: String? = null
    var EPCBinaryString2: String? = null
    var EPCBinaryString3: String? = null
    @SuppressLint("SetTextI18n")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (updateDatabaseInProgress) {
            return true
        }
        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            if (event.repeatCount == 0) {
                status!!.text = ""
                if (!readEnable) {
                    update!!.setBackgroundColor(Color.GRAY)
                    EPCTableFinding.clear()
                    if (!isChecked) {
                        EPCTableFindingMatched.clear()
                    }
                    while (!RF!!.setPower(findingPower)) {
                    }
                    readEnable = true
                    RF!!.startInventoryTag(0, 0, 0)
                    status!!.text = "در حال جست و جو ..."
                    findingInProgress = true
                    databaseBackgroundTaskHandler.postDelayed(databaseBackgroundTask, 1000)
                } else {
                    update!!.setBackgroundColor(getColor(R.color.Primary))
                    databaseBackgroundTaskHandler.removeCallbacks(databaseBackgroundTask)
                    readEnable = false
                    RF!!.stopInventory()
                    findingInProgress = false
                    flag = false
                    for ((key) in EPCTableFinding) {
                        EPCHexString = key
                        EPCInt1 = EPCHexString!!.substring(0, 8).toLong(16)
                        EPCInt2 = EPCHexString!!.substring(8, 16).toLong(16)
                        EPCInt3 = EPCHexString!!.substring(16, 24).toLong(16)
                        EPCBinaryString1 = java.lang.Long.toBinaryString(EPCInt1)
                        EPCBinaryString1 =
                            String.format("%32s", EPCBinaryString1).replace(" ".toRegex(), "0")
                        EPCBinaryString2 = java.lang.Long.toBinaryString(EPCInt2)
                        EPCBinaryString2 =
                            String.format("%32s", EPCBinaryString2).replace(" ".toRegex(), "0")
                        EPCBinaryString3 = java.lang.Long.toBinaryString(EPCInt3)
                        EPCBinaryString3 =
                            String.format("%32s", EPCBinaryString3).replace(" ".toRegex(), "0")
                        EPCBinaryString = EPCBinaryString1 + EPCBinaryString2 + EPCBinaryString3
                        header = EPCBinaryString!!.substring(0, 8).toInt(2)
                        companyNumber = EPCBinaryString!!.substring(14, 26).toInt(2)
                        itemNumber = EPCBinaryString!!.substring(26, 58).toLong(2)
                        if (companyNumber == 100) {
                            stuffCode = stuffPrimaryCode!!.toLong()
                        } else if (companyNumber == 101) {
                            stuffCode = stuffRFIDCode!!.toLong()
                        }
                        if (header == 48 && itemNumber == stuffCode) {
                            EPCTableFindingMatched[EPCHexString!!] = 1
                            flag = true
                        }
                    }
                    numberOfFoundText!!.text = EPCTableFindingMatched.size.toString()
                    status!!.text = ""
                    for ((key) in EPCTableFindingMatched) {
                        status!!.text = """
                            ${status!!.text}
                            $key
                            """.trimIndent()
                    }
                    if (flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    }
                    EPCTableFinding.clear()
                }
            }
        } else if (keyCode == 4) {
            if (readEnable) {
                RF!!.stopInventory()
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
            RF!!.stopInventory()
            readEnable = false
        }
    }

    fun clearEPCs(view: View?) {
        EPCTableFinding.clear()
        EPCTableFindingMatched.clear()
        status!!.text = ""
        numberOfFoundText!!.text = "0"
    }

    @SuppressLint("SetTextI18n")
    fun updateDatabase(view: View?) {
        if (findingInProgress) {
            return
        }
        updateDatabaseInProgress = true
        val tableJson: JSONObject
        WarehouseScanningActivity.EPCTable.putAll(EPCTableFindingMatched)
        WarehouseScanningActivity.EPCTableValid.putAll(EPCTableFindingMatched)
        tableJson = JSONObject(WarehouseScanningActivity.EPCTableValid as Map<*, *>)
        tableEditor!!.putString(
            java.lang.String.valueOf(WarehouseScanningActivity.warehouseID),
            tableJson.toString()
        )
        tableEditor!!.commit()
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
        isChecked = option!!.isChecked
    }
}