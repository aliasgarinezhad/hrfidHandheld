package com.jeanwest.reader.add

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.KeyEvent
import android.view.View
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.R
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.IUHF
import kotlinx.android.synthetic.main.activity_add_product.*
import java.util.*

class AddProductActivity : AppCompatActivity(),
    IBarcodeResult {
    
    private lateinit var barcode2D: Barcode2D
    private lateinit var barcodeID: String
    private lateinit var rf: RFIDWithUHFUART
    private lateinit var api: AddProductAPI
    private var beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private lateinit var epc: String
    private lateinit var tid: String
    private var counterValueModified: Long = 0
    private var isAddNewOK = true
    lateinit var status: TextView
    private lateinit var numberOfWritten: TextView
    private lateinit var numberOfWrittenModified: TextView
    private lateinit var powerText: TextView
    private lateinit var powerSet: SeekBar
    private lateinit var editOption: CheckBox
    private var edit = false
    private var barcodeIsScanning = false
    private var rfIsScanning = false
    private lateinit var memory: SharedPreferences
    private lateinit var memoryEditor: SharedPreferences.Editor

    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)
        status = findViewById(R.id.section_label)
        numberOfWritten = findViewById(R.id.numberOfWrittenView)
        numberOfWrittenModified = findViewById(R.id.numberOfWrittenModifiedView)
        barcode2D = Barcode2D(this)
        editOption = findViewById(R.id.checkBox2)
        powerText = findViewById(R.id.powerIndicatorText)
        powerSet = findViewById(R.id.poweSeekBar)
        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        memory = PreferenceManager.getDefaultSharedPreferences(this)
        memoryEditor = memory.edit()

        add_product_toolbar.setNavigationOnClickListener {
            back()
        }
    }

    private fun back() {
        close()
        step2 = false
        if (barcodeIsScanning || rfIsScanning) {
            barcodeIsScanning = false
            rf.stopInventory()
        }
        finish()
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {

        super.onResume()
        open()
        if (rf.power != RFPower) {
            while (!rf.setPower(RFPower)) {
            }
        }
        while (!rf.setEPCAndTIDMode()) {
        }

        if (memory.getLong("value", -1L) == -1L) {
            Toast.makeText(this, "دیتای برنامه پاک شده است. جهت کسب اطلاعات بیشتر با توسعه دهنده تماس بگیرید", Toast.LENGTH_LONG).show()
            isAddNewOK = false
        } else {
            counterValue = memory.getLong("value", -1L)
            counterMaxValue = memory.getLong("max", -1L)
            counterMinValue = memory.getLong("min", -1L)
            headerNumber = memory.getInt("header", -1)
            filterNumber = memory.getInt("filter", -1)
            partitionNumber = memory.getInt("partition", -1)
            companyNumber = memory.getInt("company", -1)
            tagPassword = memory.getString("password", "")
            oneStepActive = memory.getInt("step", -1) == 1
            counterValueModified = memory.getLong("counterModified", -1L)
            isAddNewOK = true
        }

        numberOfWritten.text = "تعداد تگ های برنامه ریزی شده: " + (counterValue - counterMinValue)
        numberOfWrittenModified.text = counterValueModified.toString()
        numberOfWrittenModified.text = "مقدار شمارنده: $counterValueModified"

        powerSet.progress = RFPower - 5
        powerText.text = "اندازه توان " + RFPower + "dB"
        powerSet.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                RFPower = powerSet.progress + 5
                powerText.text = "اندازه توان " + RFPower + "dB"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    override fun onPause() {
        super.onPause()
        back()
    }

    @SuppressLint("SetTextI18n")
    @Throws(InterruptedException::class)
    override fun getBarcode(barcode: String) {

        if (barcode.length > 2) {
            barcodeIsScanning = false
            barcodeID = barcode
            status.text = "اسکن بارکد با موفقیت انجام شد\nID: $barcodeID"
            status.setBackgroundColor(getColor(R.color.DarkGreen))
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            if (oneStepActive) {
                addNewTag()
            } else {
                step2 = true
            }
        } else {
            barcodeIsScanning = false
            rf.stopInventory()
            rfIsScanning = false
            status.text = "بارکدی پیدا نشد"
            status.setBackgroundColor(getColor(R.color.Brown))
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
        }
    }

    fun start() {
        if (!isAddNewOK) {
            Toast.makeText(this, "دیتای برنامه پاک شده است. جهت کسب اطلاعات بیشتر با توسعه دهنده تماس بگیرید", Toast.LENGTH_LONG).show()
            barcodeIsScanning = false
            return
        }
        barcode2D.startScan(this)
    }

    fun stop() {
        barcode2D.stopScan(this)
    }

    fun open() {
        barcode2D.open(this, this)
    }

    fun close() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {

            if (event.repeatCount == 0) {

                if (barcodeIsScanning) {
                    return true
                }
                if (step2) {
                    try {
                        rf.stopInventory()
                        rfIsScanning = false
                        addNewTag()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    step2 = false
                } else {

                    barcodeIsScanning = true
                    start()
                    if (isAddNewOK) {
                        rfIsScanning = true
                        if (rf.power != RFPower) {
                            while (!rf.setPower(RFPower)) {
                            }
                        }
                        rf.startInventoryTag(0, 0, 0)
                    }
                }
            }
        } else if (keyCode == 4) {
            back()
        }
        return true
    }

    @SuppressLint("SetTextI18n")
    @Throws(InterruptedException::class)
    fun addNewTag() {

        var numberOfScanned: Int
        var loopVariable: Int
        var collision: Boolean
        val epcs: MutableMap<String, Int> = HashMap()
        var isOK = false
        var temp: UHFTAGInfo?
        loopVariable = 0

        while (loopVariable < 1000) {
            temp = rf.readTagFromBuffer()
            if (temp == null) {
                break
            }
            if (edit) {
                epcs[temp.epc] = 1
                tid = temp.tid
                epc = temp.epc
            } else if (!temp.epc.startsWith("30")) {
                epcs[temp.epc] = 1
                tid = temp.tid
                epc = temp.epc
            }
            loopVariable++
        }
        numberOfScanned = loopVariable
        if (numberOfScanned > 980) {
            Thread.sleep(100)
            rf.startInventoryTag(0, 0, 0)
            start()
            barcodeIsScanning = true
            rfIsScanning = true
            return
        } else if (numberOfScanned < 3) {
            status.text = status.text.toString() + "\n" + "هیچ تگی یافت نشد"
        } else {
            collision = epcs.size != 1
            if (collision) {
                if (edit) {
                    status.text =
                        status.text.toString() + "\n" + "تعداد تگ های یافت شده بیشتر از یک عدد است"
                } else {
                    if (epcs.isEmpty()) {
                        status.text = status.text.toString() + "\n" + "هیچ تگ جدیدی یافت نشد"
                    } else {
                        status.text =
                            status.text.toString() + "\n" + "تعداد تگ های جدید یافت شده بیشتر از یک عدد است"
                    }
                }
            } else {
                status.text = status.text.toString() + "\n" + "اسکن اول با موفقیت انجام شد" + "\n" + "TID: $tid" + "\n" + "EPC: $epc"

                isOK = true
            }
        }
        status.text = status.text.toString() + "\n" + "تعداد دفعات اسکن:$numberOfScanned"

        if (!isOK) {
            epcs.clear()

            Thread.sleep(100)
            rf.startInventoryTag(0, 0, 0)
            Thread.sleep(900)
            loopVariable = 0
            while (loopVariable < 15) {
                temp = rf.readTagFromBuffer()
                if (temp == null) {
                    break
                }
                if (edit) {
                    epcs[temp.epc] = 1
                    tid = temp.tid
                    epc = temp.epc
                } else {
                    if (!temp.epc.startsWith("30")) {
                        epcs[temp.epc] = 1
                        tid = temp.tid
                        epc = temp.epc
                    }
                }
                loopVariable++
            }
            rf.stopInventory()
            numberOfScanned = loopVariable
            if (numberOfScanned <= 10) {
                status.text = status.text.toString() + "\n" + "هیچ تگی یافت نشد"
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                status.setBackgroundColor(getColor(R.color.Brown))
                return
            }
            collision = epcs.size != 1
            if (collision) {
                if (edit) {
                    status.text =
                        status.text.toString() + "\n" + "تعداد تگ های یافت شده بیشتر از یک عدد است"
                } else {
                    if (epcs.isEmpty()) {
                        status.text = status.text.toString() + "\n" + "هیچ تگ جدیدی یافت نشد"
                    } else {
                        status.text =
                            status.text.toString() + "\n" + "تعداد تگ های جدید یافت شده بیشتر از یک عدد است"
                    }
                }
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
                status.setBackgroundColor(getColor(R.color.Brown))
                return
            }
            status.text = status.text.toString() + "\n" + "اسکن دوم با موفقیت انجام شد" + "\n" + "TID: $tid" + "\n" + "EPC: $epc"
        }

        api = AddProductAPI()
        api.barcode = barcodeID
        api.start()
        while (api.run) {}

        if (!api.status) {
            status.text = status.text.toString() + "\n" + "خطا در دیتابیس" + api.response
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            status.setBackgroundColor(getColor(R.color.Brown))
            return
        }
        while (!rf.setPower(30)) {}

        val itemNumber = api.response.toLong() // 32 bit
        val serialNumber = counterValue // 38 bit

        val productEPC = epcGenerator(headerNumber, filterNumber, partitionNumber, companyNumber, itemNumber, serialNumber)

        var k = 0
        while (k < 15) {
            if (rf.writeData("00000000", IUHF.Bank_TID, 0, 96, tid, IUHF.Bank_EPC, 2, 6, productEPC)) {
                break
            }
            k++
        }
        lateinit var EPCVerify: String
        var o = 0
        while (o < 15) {
            try {
                EPCVerify =
                    rf.readData("00000000", IUHF.Bank_TID, 0, 96, tid, IUHF.Bank_EPC, 2, 6)
                        .lowercase()
                break
            } catch (e: NullPointerException) {
            }
            o++
        }
        while (!rf.setPower(RFPower)) {
        }
        if (o >= 15) {
            status.text = status.text.toString() + "سریال نوشته شده با سریال واقعی تطابق ندارد"
            status.text = status.text.toString() + "EPCVerify"
            status.text = status.text.toString() + "New"

            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            status.setBackgroundColor(getColor(R.color.Brown))
            return
        }
        if (productEPC != EPCVerify) {
            status.text = status.text.toString() + "سریال نوشته شده با سریال واقعی تطابق ندارد"

            status.text = status.text.toString() + EPCVerify
            status.text = status.text.toString() + "New"

            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500)
            status.setBackgroundColor(getColor(R.color.Brown))
            return
        }
        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        status.setBackgroundColor(getColor(R.color.DarkGreen))

        status.text = status.text.toString() + "\n" +"با موفقیت اضافه شد"
        status.text = status.text.toString() + "\n" + "number of try in writing: $k"
        status.text = status.text.toString() + "\n" + "number of try in confirming: $o"
        status.text = status.text.toString() + "\n" + "Header: $headerNumber"
        status.text = status.text.toString() + "\n" + "Filter: $filterNumber"
        status.text = status.text.toString() + "\n" + "Partition: $partitionNumber"
        status.text = status.text.toString() + "\n" + "Company number: $companyNumber"
        status.text = status.text.toString() + "\n" + "Item number: $itemNumber"
        status.text = status.text.toString() + "\n" + "Serial number: $serialNumber"
        status.text = status.text.toString() + "\n" + "New EPC: $productEPC"

        counterValue++
        counterValueModified++
        numberOfWritten.text = "تعداد تگ های برنامه ریزی شده: " + (counterValue - counterMinValue)
        numberOfWrittenModified.text = "مقدار شمارنده: $counterValueModified"
        if (counterValue >= counterMaxValue) {
            isAddNewOK = false
        }
        memoryEditor.putLong("value", counterValue)
        memoryEditor.putLong("counterModified", counterValueModified)
        memoryEditor.commit()
    }

    @SuppressLint("SetTextI18n")
    fun CounterClearButton(view: View?) {
        counterValueModified = 0
        numberOfWrittenModified.text = "مقدار شمارنده: $counterValueModified"
        memoryEditor.putLong("counterModified", counterValueModified)
        memoryEditor.commit()
    }

    fun changeOption(view: View?) {
        edit = editOption.isChecked
    }

    private fun epcGenerator(header: Int, filter: Int, partition: Int, company: Int, item: Long, serial: Long) : String {

        var tempStr = java.lang.Long.toBinaryString(header.toLong())
        val headerStr = String.format("%8s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(filter.toLong())
        val filterStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(partition.toLong())
        val positionStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(company.toLong())
        val companynumberStr = String.format("%12s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(item)
        val itemNumberStr = String.format("%32s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(serial)
        val serialNumberStr = String.format("%38s", tempStr).replace(" ".toRegex(), "0")
        val EPCStr = headerStr + positionStr + filterStr + companynumberStr + itemNumberStr + serialNumberStr // binary string of EPC (96 bit)

        tempStr = EPCStr.substring(0, 64).toULong(2).toString(16)
        val epc0To64 = String.format("%16s", tempStr).replace(" ".toRegex(), "0")
        tempStr = EPCStr.substring(64, 96).toULong(2).toString(16)
        val epc64To96 = String.format("%8s", tempStr).replace(" ".toRegex(), "0")

        return epc0To64 + epc64To96

    }

    companion object {
        var step2 = false
        var RFPower = 5
        var oneStepActive = false
        var counterMaxValue: Long = 5
        var counterMinValue: Long = 0
        var tagPassword: String? = "00000000"
        var counterValue: Long = 0
        var filterNumber = 0 // 3bit
        var partitionNumber = 6 // 3bit
        var headerNumber = 48 // 8bit
        var companyNumber = 101 // 12bit
    }
}