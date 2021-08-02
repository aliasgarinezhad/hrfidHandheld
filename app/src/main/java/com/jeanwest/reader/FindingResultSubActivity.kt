package com.jeanwest.reader

import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class FindingResultSubActivity : AppCompatActivity() {

    lateinit var rf: RFIDWithUHFUART
    var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var findingPower = 30
    lateinit var status: TextView
    lateinit var powerText: TextView
    lateinit var powerSeekBar: SeekBar
    lateinit var numberOfFoundText: TextView
    lateinit var stuffSpec: TextView
    private var stuffCode: Long = 0
    lateinit var picture: WebView
    lateinit var option: CheckBox
    lateinit var stuff: JSONObject
    var epcTableFindingMatched: MutableMap<String?, Int> = HashMap()
    lateinit var setting: WebSettings
    var isChecked = false
    var findingInProgress = false
    var readEnable = false

    var header = 0
    var companyNumber = 0
    var itemNumber: Long = 0
    var flag = false
    var EPCInt1: Long = 0
    var EPCInt2: Long = 0
    var EPCInt3: Long = 0
    lateinit var EPCHexString: String
    lateinit var EPCBinaryString: String
    lateinit var EPCBinaryString1: String
    lateinit var EPCBinaryString2: String
    lateinit var EPCBinaryString3: String
    
    var databaseBackgroundTaskHandler = Handler()
    
    var databaseBackgroundTask: Runnable = object : Runnable {
        
        @SuppressLint("SetTextI18n")
        override fun run() {
            
            if (findingInProgress) {
                
                var uhfTagInfo: UHFTAGInfo?
                
                while (true) {
                    
                    uhfTagInfo = rf.readTagFromBuffer()
                    if (uhfTagInfo != null) {
                        EPCTableFinding[uhfTagInfo.epc] = 1
                    } else {
                        break
                    }
                }
                if (EPCTableFinding.isNotEmpty()) {
                    flag = false
                    for ((key) in EPCTableFinding) {
                        EPCHexString = key
                        EPCInt1 = EPCHexString.substring(0, 8).toLong(16)
                        EPCInt2 = EPCHexString.substring(8, 16).toLong(16)
                        EPCInt3 = EPCHexString.substring(16, 24).toLong(16)
                        EPCBinaryString1 = java.lang.Long.toBinaryString(EPCInt1)
                        EPCBinaryString1 = String.format("%32s", EPCBinaryString1).replace(" ".toRegex(), "0")
                        EPCBinaryString2 = java.lang.Long.toBinaryString(EPCInt2)
                        EPCBinaryString2 = String.format("%32s", EPCBinaryString2).replace(" ".toRegex(), "0")
                        EPCBinaryString3 = java.lang.Long.toBinaryString(EPCInt3)
                        EPCBinaryString3 = String.format("%32s", EPCBinaryString3).replace(" ".toRegex(), "0")
                        EPCBinaryString = EPCBinaryString1 + EPCBinaryString2 + EPCBinaryString3
                        header = EPCBinaryString.substring(0, 8).toInt(2)
                        companyNumber = EPCBinaryString.substring(14, 26).toInt(2)
                        itemNumber = EPCBinaryString.substring(26, 58).toLong(2)
                        if (companyNumber == 100) {
                            stuffCode = stuffPrimaryCode.toLong()
                        } else if (companyNumber == 101) {
                            stuffCode = stuffRFIDCode.toLong()
                        }
                        if (header == 48 && itemNumber == stuffCode) {
                            epcTableFindingMatched[EPCHexString] = 1
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
                    EPCTableFinding.clear()
                    databaseBackgroundTaskHandler.postDelayed(this, 1000)
                } else {
                    databaseBackgroundTaskHandler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finding_result_sub)
        status = findViewById(R.id.section_label0)
        stuffSpec = findViewById(R.id.result0)
        picture = findViewById(R.id.pictureView0)
        powerText = findViewById(R.id.findingPowerTextView0)
        powerSeekBar = findViewById(R.id.findingPowerSeekBar0)
        numberOfFoundText = findViewById(R.id.numberOfFoundTextView0)
        option = findViewById(R.id.checkBox0)
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
                    powerText.setText("قدرت سیگنال($findingPower)")
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
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        while (!rf.setPower(findingPower)) {
        }
        try {
            stuff = FindingProductActivity.API.similar.getJSONObject(FindingProductActivity.index)
            stuffSpec.text = """
                ${stuff.getString("productName")}
                کد محصول: ${stuff.getString("K_Bar_Code")}
                بارکد: ${stuff.getString("KBarCode")}
                قیمت مصرف کننده: ${stuff.getString("OrigPrice")}
                قیمت فروش: ${stuff.getString("SalePrice")}
                موجودی فروشگاه: ${stuff.getString("dbCountStore")}
                موجودی انبار: ${stuff.getString("dbCountDepo")}
                """.trimIndent()
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
        reading.databaseInProgress = false
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
        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            if (event.repeatCount == 0) {
                status.text = ""
                if (!readEnable) {
                    EPCTableFinding.clear()
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
                    databaseBackgroundTaskHandler.removeCallbacks(databaseBackgroundTask)
                    readEnable = false
                    rf.stopInventory()
                    findingInProgress = false
                    flag = false
                    for ((key) in EPCTableFinding) {
                        EPCHexString = key
                        EPCInt1 = EPCHexString.substring(0, 8).toLong(16)
                        EPCInt2 = EPCHexString.substring(8, 16).toLong(16)
                        EPCInt3 = EPCHexString.substring(16, 24).toLong(16)
                        EPCBinaryString1 = java.lang.Long.toBinaryString(EPCInt1)
                        EPCBinaryString1 = String.format("%32s", EPCBinaryString1).replace(" ".toRegex(), "0")
                        EPCBinaryString2 = java.lang.Long.toBinaryString(EPCInt2)
                        EPCBinaryString2 = String.format("%32s", EPCBinaryString2).replace(" ".toRegex(), "0")
                        EPCBinaryString3 = java.lang.Long.toBinaryString(EPCInt3)
                        EPCBinaryString3 = String.format("%32s", EPCBinaryString3).replace(" ".toRegex(), "0")
                        EPCBinaryString = EPCBinaryString1 + EPCBinaryString2 + EPCBinaryString3
                        header = EPCBinaryString.substring(0, 8).toInt(2)
                        companyNumber = EPCBinaryString.substring(14, 26).toInt(2)
                        itemNumber = EPCBinaryString.substring(26, 58).toLong(2)
                        if (companyNumber == 100) {
                            stuffCode = stuffPrimaryCode.toLong()
                        } else if (companyNumber == 101) {
                            stuffCode = stuffRFIDCode.toLong()
                        }
                        if (header == 48 && itemNumber == stuffCode) {
                            epcTableFindingMatched[EPCHexString] = 1
                            flag = true
                        }
                    }
                    numberOfFoundText.text = epcTableFindingMatched.size.toString()
                    status.text = ""
                    for ((key) in epcTableFindingMatched) {
                        status.text = "${status.text}" + "\n$key"
                    }
                    if (flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    }
                    EPCTableFinding.clear()
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
        EPCTableFinding.clear()
        epcTableFindingMatched.clear()
        status.text = ""
        numberOfFoundText.text = "0"
    }

    fun optionChange(view: View?) {
        isChecked = option.isChecked
    }

    companion object {
        var EPCTableFinding: MutableMap<String, Int> = HashMap()
        lateinit var stuffPrimaryCode: String
        lateinit var stuffRFIDCode: String
    }
}