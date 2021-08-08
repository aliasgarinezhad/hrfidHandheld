package com.jeanwest.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
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
import com.google.gson.Gson
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.mikhaellopez.circularprogressbar.CircularProgressBar.ProgressDirection
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.collections.HashMap

class WarehouseScanningActivity : AppCompatActivity() {
    lateinit var rf: RFIDWithUHFUART
    var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    lateinit var status: TextView
    lateinit var percentage: TextView
    lateinit var powerText: TextView
    lateinit var powerSeekBar: SeekBar
    lateinit var response: Toast
    private var readingInProgress = false
    private var processingInProgress = false
    lateinit var button: Button
    lateinit var circularProgressBar: CircularProgressBar
    lateinit var nextActivityIntent: Intent
    var epcLastLength = 0
    var readingPower = 30
    lateinit var table: SharedPreferences
    lateinit var tableEditor: SharedPreferences.Editor
    var temp = ""
    var subStuffs = JSONArray()
    var temp2 = JSONObject()
    var header = ""
    var departmentInfoID = 0
    var allStuffs = 0
    var fromLogin = false
    var timerHandler = Handler()

    var timerRunnable: Runnable = object : Runnable {
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
                status.text = " کد شعبه:" + departmentInfoID + "\n"

                if (warehouseID == 1) {
                    status.text = status.text.toString() + "در سطح فروش" + "\n"
                } else {
                    status.text = status.text.toString() + "در سطح انبار" + "\n"
                }

                status.text = status.text.toString() + " سرعت اسکن (تگ بر ثانیه):" + (EPCTable.size - epcLastLength) + "\n"
                    
                if (EPCTable.size > epcLastLength + 100) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 700)
                    epcLastLength = EPCTable.size
                }
                if (EPCTable.size > epcLastLength + 30) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                    epcLastLength = EPCTable.size
                }
                if (EPCTable.size > epcLastLength + 10) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 300)
                    epcLastLength = EPCTable.size
                }
                if (EPCTable.size > epcLastLength) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                    epcLastLength = EPCTable.size
                }
                timerHandler.postDelayed(this, 1000)
            } else if (processingInProgress) {
                EPCTableValid.clear()
                for ((key) in EPCTable) {

                    if (key.length > 0) {
                        header = key.substring(0, 2)
                        if (header == "30") {
                            EPCTableValid[key] = 1
                        }
                    } else {
                        Log.e("errorx", key)
                    }
                }
                status.text = " کد شعبه:" + departmentInfoID + "\n"

                if (warehouseID == 1) {
                    status.text = status.text.toString() + "در سطح فروش" + "\n"
                } else {
                    status.text = status.text.toString() + "در سطح انبار" + "\n"
                }
                status.text = status.text.toString() + "سرعت اسکن (تگ بر ثانیه): 0" + "\n"

                status.text = status.text.toString() + "تعداد کالا های پیدا شده: " + EPCTableValid.size + "/" + allStuffs

                circularProgressBar.progress = (EPCTableValid.size * 100 / allStuffs).toFloat()
                percentage.text = circularProgressBar.progress.toString() + '%'

                readingInProgress = false
                databaseInProgress = false
                processingInProgress = false
                button.setBackgroundColor(getColor(R.color.Primary))
            } else if (databaseInProgress) {
                if (!API.status) {
                    status.text = "در حال ارسال به سرور "
                    timerHandler.postDelayed(this, 1000)
                } else if (!API2.status) {
                    if (!API2.isAlive) {
                        API2.start()
                    }
                    status.text = "در حال دریافت اطلاعات از سرور "
                    timerHandler.postDelayed(this, 1000)
                } else {
                    startActivity(nextActivityIntent)
                }
            } else {
                response.setText("خطا در دیتابیس " + API.response)

                response.show()
                status.text = " کد شعبه:" + departmentInfoID + "\n"

                if (warehouseID == 1) {
                    status.text = status.text.toString() + "در سطح فروش" + "\n"
                } else {
                    status.text = status.text.toString() + "در سطح انبار" + "\n"
                }
                status.text = status.text.toString() + "سرعت اسکن (تگ بر ثانیه): 0" + "\n"

                status.text = status.text.toString() + "تعداد کالا های پیدا شده: " + EPCTableValid.size + "/" + allStuffs
            }
        }
    }

    @SuppressLint("SetTextI18n", "CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading)
        status = findViewById(R.id.section_label)
        response = Toast.makeText(this, "", Toast.LENGTH_LONG)
        button = findViewById(R.id.buttonReading)
        nextActivityIntent = Intent(this, ReadingResultActivity::class.java)
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
        table = PreferenceManager.getDefaultSharedPreferences(this)
        tableEditor = table.edit()
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        EPCTable.clear()
        EPCTableValid.clear()

        EPCTableValid =
            if (Gson().fromJson(
                    table.getString(warehouseID.toString(), ""),
                    EPCTableValid.javaClass
                ) != null
            ) {
                Gson().fromJson(
                    table.getString(warehouseID.toString(), ""),
                    EPCTableValid.javaClass
                )
            } else {
                HashMap<String, Int>()
            }

        EPCTable.putAll(EPCTableValid)

        epcLastLength = EPCTable.size
        while (!rf.setEPCMode()) {
        }
        if (rf.power != readingPower) {
            while (!rf.setPower(readingPower)) {
            }
        }
        databaseInProgress = false
        readingInProgress = false
        processingInProgress = false
        fromLogin = intent.getBooleanExtra("fromLogin", false)

        if (fromLogin) {
            ID = intent.getIntExtra("ID", 0)
            warehouseID = intent.getIntExtra("warehouseID", 0)
            departmentInfoID = intent.getIntExtra("departmentInfoID", 0)
            API2 = APIReadingConflicts()
            API2.start()
            while (API2.run) {
            }
            allStuffs = 0
            for (i in 0 until API2.stuffs.length()) {
                try {
                    temp = API2.stuffs.getString(i)
                    subStuffs = API2.conflicts.getJSONArray(temp)
                    for (j in 0 until subStuffs.length()) {
                        temp2 = subStuffs.getJSONObject(j)
                        allStuffs += temp2.getInt("dbCount")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            fromLogin = false
        }
        status.text = " کد شعبه:" + departmentInfoID + "\n"

        if (warehouseID == 1) {
            status.text = status.text.toString() + "در سطح فروش" + "\n"
        } else {
            status.text = status.text.toString() + "در سطح انبار" + "\n"
        }
        status.text = status.text.toString() + "سرعت اسکن (تگ بر ثانیه): 0" + "\n"

        status.text = status.text.toString() + "تعداد کالا های پیدا شده: " + EPCTableValid.size + "/" + allStuffs

        circularProgressBar.progress = (EPCTableValid.size * 100 / allStuffs).toFloat()
        percentage.text = (EPCTableValid.size * 100 / allStuffs).toFloat().toString() + '%'
        powerText.text = "اندازه توان($readingPower)"
        powerSeekBar.progress = readingPower - 5
    }

    @SuppressLint("SetTextI18n")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            if (event.repeatCount == 0) {
                if (!readingInProgress) {
                    while (!rf.setPower(readingPower)) {
                    }
                    databaseInProgress = false
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
                    databaseInProgress = false
                    readingInProgress = false
                    processingInProgress = true
                    status.text = "در حال پردازش ..."
                    val tableJson = JSONObject(EPCTableValid as Map<*, *>)
                    tableEditor.putString(warehouseID.toString(), tableJson.toString())
                    tableEditor.putInt(departmentInfoID.toString() + warehouseID, ID)
                    tableEditor.commit()
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
        val tableJson = JSONObject(EPCTableValid as Map<*, *>)
        tableEditor.putString(warehouseID.toString(), tableJson.toString())
        tableEditor.putInt(departmentInfoID.toString() + warehouseID, ID)
        tableEditor.commit()
    }

    @SuppressLint("SetTextI18n")
    fun sendFile(view: View?) {
        if (readingInProgress || processingInProgress) {
            return
        }
        ReadingResultActivity.indexNumber = 0
        API = APIReadingEPC()
        API.start()
        API2 = APIReadingConflicts()
        databaseInProgress = true
        timerHandler.post(timerRunnable)
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
            tableEditor.putString(warehouseID.toString(), "")
            tableEditor.putInt(departmentInfoID.toString() + departmentInfoID, ID)
            tableEditor.commit()
            epcLastLength = 0

            status.text = " کد شعبه:" + departmentInfoID + "\n"

            if (warehouseID == 1) {
                status.text = status.text.toString() + "در سطح فروش" + "\n"
            } else {
                status.text = status.text.toString() + "در سطح انبار" + "\n"
            }
            status.text = status.text.toString() + "سرعت اسکن (تگ بر ثانیه): 0" + "\n"

            status.text = status.text.toString() + "تعداد کالا های پیدا شده: " + EPCTableValid.size + "/" + allStuffs

            circularProgressBar.progress = (EPCTableValid.size * 100 / allStuffs).toFloat()
            percentage.text = (EPCTableValid.size * 100 / allStuffs).toFloat().toString() + '%'
        }
        alertBuilder.setNegativeButton("خیر") { dialog, which -> }
        alertDialog = alertBuilder.create()
        alertDialog.setOnShowListener {
            alertDialog.window!!.decorView.layoutDirection =
                View.LAYOUT_DIRECTION_RTL // set title and message direction to RTL
        }
        alertDialog.show()
    }

    companion object {
        @JvmField
        var EPCTable: MutableMap<String, Int> = HashMap()

        @JvmField
        var EPCTableValid: MutableMap<String, Int> = HashMap()

        @JvmField
        var ID: Int = 0

        @JvmField
        var API = APIReadingEPC()

        @JvmField
        var API2 = APIReadingConflicts()

        @JvmField
        var databaseInProgress = false

        @JvmField
        var warehouseID = 2
    }
}