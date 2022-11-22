package com.jeanwest.reader.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.R
import com.jeanwest.reader.management.*
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.test.RFIDWithUHFUART
import com.jeanwest.reader.ui.JeanswestBottomBar
import com.jeanwest.reader.ui.MyApplicationTheme
import com.jeanwest.reader.ui.borderColor
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


@ExperimentalFoundationApi
class Count : ComponentActivity(),
    IBarcodeResult {

    private var searchModeEpcTable = mutableListOf<String>()
    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var scannedEpcs = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var scannedBarcodes = mutableListOf<String>()
    var inputBarcodes = mutableStateListOf<String>()
    private val barcode2D =
        Barcode2D(this)
    val inputProducts = mutableMapOf<String, Product>()
    val scannedProducts = mutableMapOf<String, Product>()
    private var scanningJob: Job? = null
    private var scannedEpcMapWithProperties = mutableMapOf<String, Product>()
    private var scannedBarcodeMapWithProperties = mutableMapOf<String, Product>()
    private var inputBarcodeMapWithProperties = mutableMapOf<String, Product>()

    //ui parameters
    var uiList = mutableStateMapOf<String, Product>()
    var loading by mutableStateOf(false)
    private var rfidScan by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var scannedNumber by mutableStateOf(0)
    private var fileName by mutableStateOf("خروجی")
    private var openDialog by mutableStateOf(false)
    private var filteredUiList = mutableStateListOf<Product>()
    private var scanFilter by mutableStateOf("اضافی")
    private var defineZoneMode by mutableStateOf(false)
    private var signedFilter by mutableStateOf("همه")
    private var searchModeProductCodes = mutableListOf<String>()
    private var signedProductCodes = mutableListOf<String>()
    private var openClearDialog by mutableStateOf(false)
    private var scanTypeValue by mutableStateOf("RFID")
    private var state = SnackbarHostState()

    private val apiTimeout = 30000
    private lateinit var queue: RequestQueue
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        setContent {
            Page()
        }
        queue = Volley.newRequestQueue(this)
        loadMemory()

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode(rf, state)

        if (intent.action == Intent.ACTION_SEND) {

            if (getString(R.string.xlsx) == intent.type || getString(R.string.xls) == intent.type) {

                if (!checkLogin()) {
                    return
                }
                rfInit(rf, this, state)

                while (!rf.setEPCMode()) {
                    rf.free()
                }

                val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri

                if (intent.type == getString(R.string.xls)) {
                    readXLSFile(uri)
                } else if (intent.type == getString(R.string.xlsx)) {
                    readXLSXFile(uri)
                }

            } else {
                while (!rf.setEPCMode()) {
                    rf.free()
                }

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "فرمت فایل باید اکسل باشد",
                        null,
                        SnackbarDuration.Long
                    )
                }
                syncInputItemsToServer()
            }
        } else {
            while (!rf.setEPCMode()) {
                rf.free()
            }
            syncInputItemsToServer()
        }
    }

    private fun calculateConflicts() {

        val conflicts = mutableMapOf<String, Product>()

        conflicts.putAll(inputProducts.filter {
            it.key !in scannedProducts.keys
        })

        conflicts.putAll(scannedProducts.filter {
            it.key !in inputProducts.keys
        })

        inputProducts.filter {
            it.key in scannedProducts.keys
        }.forEach {

            val product: Product = it.value.copy()
            product.scannedEPCs = scannedProducts[it.key]!!.scannedEPCs
            product.scannedBarcodeNumber = scannedProducts[it.key]!!.scannedBarcodeNumber
            product.scannedBarcode = scannedProducts[it.key]!!.scannedBarcode
            conflicts[it.key] = product
        }

        uiList.clear()
        uiList.putAll(conflicts)
        filterUiList()
    }

    private fun checkLogin(): Boolean {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        return if (memory.getString("username", "") != "") {

            MainActivity.username = memory.getString("username", "")!!
            MainActivity.token = memory.getString("accessToken", "")!!
            true
        } else {
            Toast.makeText(
                this,
                "لطفا ابتدا به حساب کاربری خود وارد شوید",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("conflicts")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("کد جست و جو")
        headerRow.createCell(1).setCellValue("تعداد")
        headerRow.createCell(2).setCellValue("دسته")
        headerRow.createCell(3).setCellValue("کسری")
        headerRow.createCell(4).setCellValue("اضافی")
        headerRow.createCell(5).setCellValue("نشانه")

        uiList.values.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.KBarCode)
            row.createCell(1).setCellValue(it.scannedNumber.toDouble())

            if (it.conflictType == "کسری") {
                row.createCell(3).setCellValue(it.conflictNumber.toDouble())
            } else if (it.conflictType == "اضافی") {
                row.createCell(4).setCellValue(it.conflictNumber.toDouble())
            }

            if (it.KBarCode in signedProductCodes) {
                row.createCell(5).setCellValue("نشانه دار")
            }
        }

        val row = sheet.createRow(sheet.physicalNumberOfRows)
        row.createCell(0).setCellValue("مجموع")
        row.createCell(1).setCellValue(scannedNumber.toDouble())
        row.createCell(3).setCellValue(shortagesNumber.toDouble())
        row.createCell(4).setCellValue(additionalNumber.toDouble())

        val sheet2 = workbook.createSheet("کسری")

        val header2Row = sheet2.createRow(sheet2.physicalNumberOfRows)
        header2Row.createCell(0).setCellValue("کد جست و جو")
        header2Row.createCell(1).setCellValue("موجودی")
        header2Row.createCell(2).setCellValue("دسته")
        header2Row.createCell(3).setCellValue("کسری")
        header2Row.createCell(4).setCellValue("نشانه")

        uiList.values.forEach {

            if (it.conflictType == "کسری") {
                val shortageRow = sheet2.createRow(sheet2.physicalNumberOfRows)
                shortageRow.createCell(0).setCellValue(it.KBarCode)
                shortageRow.createCell(1)
                    .setCellValue(it.scannedNumber.toDouble() + it.conflictNumber.toDouble())
                shortageRow.createCell(3).setCellValue(it.conflictNumber.toDouble())

                if (it.KBarCode in signedProductCodes) {
                    shortageRow.createCell(4).setCellValue("نشانه دار")
                }
            }
        }

        val sheet3 = workbook.createSheet("اضافی")

        val header3Row = sheet3.createRow(sheet3.physicalNumberOfRows)
        header3Row.createCell(0).setCellValue("کد جست و جو")
        header3Row.createCell(1).setCellValue("موجودی")
        header3Row.createCell(2).setCellValue("دسته")
        header3Row.createCell(3).setCellValue("اضافی")
        header3Row.createCell(4).setCellValue("نشانه")

        uiList.values.forEach {

            if (it.conflictType == "اضافی") {
                val additionalRow = sheet3.createRow(sheet3.physicalNumberOfRows)
                additionalRow.createCell(0).setCellValue(it.KBarCode)
                additionalRow.createCell(1)
                    .setCellValue(it.conflictNumber - it.scannedNumber.toDouble())
                additionalRow.createCell(3).setCellValue(it.conflictNumber.toDouble())

                if (it.KBarCode in signedProductCodes) {
                    additionalRow.createCell(4).setCellValue("نشانه دار")
                }
            }
        }

        val dir = File(this.getExternalFilesDir(null), "/")

        val outFile = File(dir, "$fileName.xlsx")

        val outputStream = FileOutputStream(outFile.absolutePath)
        workbook.write(outputStream)
        outputStream.flush()
        outputStream.close()

        val uri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName + ".provider",
            outFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "application/octet-stream"
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this.startActivity(shareIntent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (scanTypeValue == "بارکد") {
                    stopRFScan()
                    startBarcodeScan()
                } else {
                    if (!rfidScan) {

                        scanningJob = CoroutineScope(IO).launch {
                            startRFScan()
                        }

                    } else {

                        stopRFScan()
                        syncScannedItemsToServer()
                    }
                }
            } else if (keyCode == 4) {
                back()
            } else if (keyCode == 139) {
                stopRFScan()
            }
        }
        return true
    }

    private fun stopRFScan() {

        scanningJob?.let {
            if (it.isActive) {
                rfidScan = false // cause scanning routine loop to stop
                runBlocking { it.join() }
            }
        }
    }

    private suspend fun startRFScan() {

        scanning = true
        rfidScan = true
        if (!setRFPower(state, rf, rfPower)) {
            rfidScan = false
            return
        }

        if (defineZoneMode) {
            searchModeEpcTable.clear()
        }

        rf.startInventoryTag(0, 0, 0)

        while (rfidScan) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        scannedEpcs.add(uhfTagInfo.epc)
                        if (defineZoneMode) {
                            searchModeEpcTable.add(uhfTagInfo.epc)
                        }
                    }
                } else {
                    break
                }
            }

            scannedEpcs = scannedEpcs.distinct().toMutableList()
            searchModeEpcTable = searchModeEpcTable.distinct().toMutableList()

            scannedNumber = scannedEpcs.size + scannedBarcodes.size

            val speed = scannedEpcs.size - epcTablePreviousSize
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
            epcTablePreviousSize = scannedEpcs.size

            saveToMemory()

            delay(1000)
        }

        rf.stopInventory()
        if (defineZoneMode) {
            getSearchModeProductsProperties()
            defineZoneMode = false
        }
        scannedNumber = scannedEpcs.size + scannedBarcodes.size
        saveToMemory()
        scanning = false
    }

    private fun filterUiList() {

        val signedFilterOutput =
            when (signedFilter) {
                "همه" -> {
                    uiList.values
                }
                "نشانه دار" -> {
                    uiList.values.filter {
                        it.KBarCode in signedProductCodes
                    } as MutableList<Product>
                }
                "بی نشانه" -> {
                    uiList.values.filter {
                        it.KBarCode !in signedProductCodes
                    } as MutableList<Product>
                }
                else -> {
                    uiList.values
                }
            }

        val zoneFilterOutput =
            if (defineZoneMode) {
                signedFilterOutput.filter {
                    it.productCode in searchModeProductCodes
                } as MutableList<Product>
            } else {
                signedFilterOutput
            }

        shortagesNumber = 0
        signedFilterOutput.filter {
            it.conflictType == "کسری"
        }.forEach {
            shortagesNumber += it.conflictNumber
        }

        additionalNumber = 0
        signedFilterOutput.filter {
            it.conflictType == "اضافی"
        }.forEach {
            additionalNumber += it.conflictNumber
        }

        val uiListParameters = zoneFilterOutput.filter {
            it.conflictType == scanFilter
        } as MutableList<Product>

        uiListParameters.sortBy {
            it.productCode
        }
        uiListParameters.sortBy {
            it.name
        }

        filteredUiList.clear()
        filteredUiList.addAll(uiListParameters)
    }

    private fun syncInputItemsToServer() {

        loading = true

        val barcodeTableForV4 = mutableListOf<String>()
        var inputProductsBiggerThan1000 = false

        run breakForEach@{
            inputBarcodes.distinct().forEach {
                if (it !in inputBarcodeMapWithProperties.keys) {
                    if (barcodeTableForV4.size < 1000) {
                        barcodeTableForV4.add(it)
                    } else {
                        inputProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }

        if (barcodeTableForV4.size == 0) {
            makeInputProductMap()
            syncScannedItemsToServer()
            loading = false
            return
        }

        scanFilter = "کسری"

        getProductsV4(queue, state, mutableListOf(), barcodeTableForV4, { _, barcodes, _, _ ->

            barcodes.forEach { product ->
                inputBarcodeMapWithProperties[product.scannedBarcode] = product
            }

            if (inputProductsBiggerThan1000) {
                syncInputItemsToServer()
            } else {
                makeInputProductMap()
                syncScannedItemsToServer()
            }

        }, {
            syncScannedItemsToServer()
        })
    }

    private fun makeInputProductMap() {

        inputProducts.clear()
        inputBarcodes.distinct().forEach {
            val product = inputBarcodeMapWithProperties[it]!!.copy()
            product.draftNumber = inputBarcodes.count { it1 ->
                it == it1
            }

            if (product.KBarCode !in inputProducts.keys) {
                inputProducts[product.KBarCode] = product.copy()
            } else {
                inputProducts[product.KBarCode]!!.draftNumber += product.draftNumber
            }
        }
    }

    private fun syncScannedItemsToServer() {

        loading = true
        if (scannedNumber == 0) {
            calculateConflicts()
            loading = false
            return
        }

        val epcTableForV4 = mutableListOf<String>()
        val barcodeTableForV4 = mutableListOf<String>()
        var scannedProductsBiggerThan1000 = false

        run breakForEach@{
            scannedEpcs.forEach {
                if (it !in scannedEpcMapWithProperties.keys) {
                    if (epcTableForV4.size < 1000) {
                        epcTableForV4.add(it)
                    } else {
                        scannedProductsBiggerThan1000 = true

                        return@breakForEach
                    }
                }
            }
        }
        run breakForEach@{
            scannedBarcodes.distinct().forEach {
                if (it !in scannedBarcodeMapWithProperties.keys) {
                    if (barcodeTableForV4.size < 1000) {
                        barcodeTableForV4.add(it)
                    } else {
                        scannedProductsBiggerThan1000 = true
                        return@breakForEach
                    }
                }
            }
        }

        if (epcTableForV4.size == 0 && barcodeTableForV4.size == 0) {

            makeScannedProductMap()
            calculateConflicts()
            loading = false
            return
        }

        getProductsV4(
            queue,
            state,
            epcTableForV4,
            barcodeTableForV4,
            { epcs, barcodes, invalidEpcs, invalidBarcodes ->

                epcs.forEach { product ->
                    scannedEpcMapWithProperties[product.scannedEPCs[0]] = product
                }

                barcodes.forEach { product ->
                    scannedBarcodeMapWithProperties[product.scannedBarcode] = product
                }

                for (i in 0 until invalidBarcodes.length()) {
                    scannedBarcodes.remove(invalidBarcodes[i])
                }
                for (i in 0 until invalidEpcs.length()) {
                    scannedEpcs.remove(invalidEpcs[i])
                }

                scannedNumber = scannedEpcs.size + scannedBarcodes.size

                if (scannedProductsBiggerThan1000) {
                    syncScannedItemsToServer()
                } else {
                    makeScannedProductMap()
                    calculateConflicts()
                    loading = false
                }

            },
            {
                calculateConflicts()
                loading = false
            })
    }

    private fun makeScannedProductMap() {

        scannedProducts.clear()

        scannedEpcs.forEach {

            val product = scannedEpcMapWithProperties[it]!!

            if (product.KBarCode in scannedProducts.keys) {
                scannedProducts[product.KBarCode]!!.scannedEPCs.add(it)
            } else {
                scannedProducts[product.KBarCode] = product.copy(scannedEPCs = mutableListOf(it))
                scannedProducts[product.KBarCode]!!.scannedEPCs = mutableListOf(it)
            }
        }

        scannedBarcodes.distinct().forEach {
            val product = scannedBarcodeMapWithProperties[it]!!.copy()
            product.scannedBarcodeNumber = scannedBarcodes.count { it1 ->
                it == it1
            }

            if (product.KBarCode !in scannedProducts.keys) {
                scannedProducts[product.KBarCode] = product.copy()
            } else {
                scannedProducts[product.KBarCode]!!.scannedBarcodeNumber += product.scannedBarcodeNumber
            }
        }
    }

    private fun getSearchModeProductsProperties() {

        if (searchModeEpcTable.size == 0) {
            return
        }

        getProductsV4(queue, state, searchModeEpcTable, mutableListOf(), { epcs, _, _, _ ->
            epcs.forEach {
                searchModeEpcTable.add(it.productCode)
            }
            filterUiList()
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "بارکد های جست و جو مشخص شدند. حال می توانید جنس های مشابه این بارکد ها را جست و جو کنید. برای دیدن همه اجناس، جست و جو را غیر فعال کنید.",
                    null,
                    SnackbarDuration.Long
                )
            }
        }, {})
    }

    private fun readXLSXFile(uri: Uri) {

        val workbook: XSSFWorkbook
        try {
            workbook = XSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل نامعتبر است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل خالی است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        inputBarcodes.clear()

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {

                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    inputBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        saveToMemory()
        syncInputItemsToServer()
    }

    private fun readXLSFile(uri: Uri) {

        val workbook: HSSFWorkbook
        try {
            workbook = HSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل نامعتبر است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "فایل خالی است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        inputBarcodes.clear()

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {
                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    inputBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        saveToMemory()
        syncInputItemsToServer()
    }

    private fun searchModeButton() {

        searchModeProductCodes.clear()
        defineZoneMode = true
        filterUiList()
        stopRFScan()
        rfPower = 5
        scanningJob = CoroutineScope(IO).launch {
            startRFScan()
        }
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            scannedBarcodes.add(barcode)
            scannedNumber = scannedEpcs.size + scannedBarcodes.size
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("FileAttachmentEPCTable", JSONArray(scannedEpcs).toString())
        edit.putString("FileAttachmentBarcodeTable", JSONArray(scannedBarcodes).toString())
        edit.putString(
            "FileAttachmentFileZoneCodesTable",
            JSONArray(searchModeProductCodes).toString()
        )
        edit.putString(
            "FileAttachmentFileSignedCodesTable",
            JSONArray(signedProductCodes).toString()
        )
        edit.putString("FileAttachmentFileBarcodeTable", JSONArray(inputBarcodes).toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scannedEpcs = Gson().fromJson(
            memory.getString("FileAttachmentEPCTable", ""),
            scannedEpcs.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = scannedEpcs.size

        scannedBarcodes = Gson().fromJson(
            memory.getString("FileAttachmentBarcodeTable", ""),
            scannedBarcodes.javaClass
        ) ?: mutableListOf()

        inputBarcodes = Gson().fromJson(
            memory.getString("FileAttachmentFileBarcodeTable", ""),
            inputBarcodes.javaClass
        ) ?: mutableStateListOf()

        searchModeProductCodes = Gson().fromJson(
            memory.getString("FileAttachmentFileZoneCodesTable", ""),
            searchModeProductCodes.javaClass
        ) ?: mutableListOf()

        signedProductCodes = Gson().fromJson(
            memory.getString("FileAttachmentFileSignedCodesTable", ""),
            signedProductCodes.javaClass
        ) ?: mutableListOf()

        scannedNumber = scannedEpcs.size + scannedBarcodes.size
    }

    private fun clear() {

        if (scannedNumber != 0) {
            scannedBarcodes.clear()
            scannedEpcs.clear()
            searchModeEpcTable.clear()
            searchModeProductCodes.clear()
            scannedBarcodeMapWithProperties.clear()
            scannedEpcMapWithProperties.clear()
            epcTablePreviousSize = 0
            scannedNumber = 0
            scannedProducts.clear()
            calculateConflicts()
            openDialog = false
            saveToMemory()
        } else {
            scannedBarcodes.clear()
            scannedEpcs.clear()
            searchModeEpcTable.clear()
            searchModeProductCodes.clear()
            epcTablePreviousSize = 0
            scannedNumber = 0
            inputBarcodes.clear()
            inputBarcodeMapWithProperties.clear()
            scannedProducts.clear()
            inputProducts.clear()
            uiList.clear()
            filteredUiList.clear()
            additionalNumber = 0
            shortagesNumber = 0
            scanFilter = "اضافی"
            scanTypeValue = "RFID"
            openDialog = false
            saveToMemory()
        }
    }

    private fun startBarcodeScan() {
        barcode2D.startScan(this)
    }

    private fun barcodeInit() {
        barcode2D.open(this, this)
    }

    private fun stopBarcodeScan() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
    }

    private fun back() {

        saveToMemory()
        stopRFScan()
        stopBarcodeScan()
        queue.stop()
        beep.release()
        finish()
    }

    private fun openSearchActivity(product: Product) {
        val intent = Intent(this, SearchSpecialProduct::class.java)
        intent.putExtra("product", Gson().toJson(product).toString())
        startActivity(intent)
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                    snackbarHost = { ErrorSnackBar(state) },
                    bottomBar = { if (inputBarcodes.isNotEmpty()) BottomBar() },
                )
            }
        }
    }

    @Composable
    fun BottomBar() {

        BottomAppBar(
            backgroundColor = JeanswestBottomBar,
            modifier = Modifier.wrapContentHeight()
        ) {

            Column {

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    ScanTypeDropDownList(
                        Modifier.align(Alignment.CenterVertically), scanTypeValue
                    ) {
                        scanTypeValue = it
                        if (scanTypeValue == "بارکد") {
                            searchModeProductCodes.clear()
                            defineZoneMode = false
                        }
                    }
                    ScanFilterDropDownList(modifier = Modifier.align(Alignment.CenterVertically))
                    SignedFilterDropDownList(modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }

    @Composable
    fun AppBar() {

        TopAppBar(

            navigationIcon = {
                IconButton(onClick = { back() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = ""
                    )
                }
            },

            actions = {
                IconButton(onClick = {
                    if (!scanning && !loading) {
                        openDialog = true
                    }
                }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_share_24),
                        contentDescription = ""
                    )
                }
                IconButton(modifier = Modifier.testTag("CountActivityClearButton"),
                    onClick = {
                        if (!scanning && !loading) {
                            openClearDialog = true
                        }
                    }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = "شمارش",
                    modifier = Modifier
                        .padding(start = 35.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        Column {

            if (openDialog) {
                FileAlertDialog()
            }

            if (openClearDialog) {
                ClearAlertDialog()
            }

            if (scanning || loading) {
                LoadingCircularProgressIndicator(scanning, loading)
            } else {

                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .border(
                            BorderStroke(1.dp, borderColor),
                            shape = MaterialTheme.shapes.small
                        )
                        .background(
                            MaterialTheme.colors.onPrimary,
                            shape = MaterialTheme.shapes.small
                        )
                        .fillMaxWidth()
                ) {

                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {

                        Text(
                            text = "اسکن: $scannedNumber",
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .align(Alignment.CenterVertically)
                                .weight(1F),
                        )
                        if (inputBarcodes.isNotEmpty()) {

                            Text(
                                text = "کسری: $shortagesNumber",
                                textAlign = TextAlign.Right,
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1F),
                            )
                            Text(
                                text = "اضافی: $additionalNumber",
                                textAlign = TextAlign.Right,
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1F),
                            )
                        }
                    }

                    PowerSlider(scanTypeValue == "RFID", rfPower) { rfPower = it }

                    if (scanTypeValue == "RFID" && inputBarcodes.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Button(
                                onClick = { searchModeButton() }) {
                                Text(
                                    text = "تعریف ناحیه جست و جو",
                                    textAlign = TextAlign.Right,
                                )
                            }
                        }
                    }
                }

                LazyColumn {

                    items(filteredUiList.size) { i ->
                        Item(
                            i,
                            filteredUiList,
                            true,
                            text3 = if (inputBarcodes.isNotEmpty()) {
                                "موجودی: " + filteredUiList[i].draftNumber
                            } else {
                                "رنگ: " + filteredUiList[i].color
                            },
                            text4 = if (inputBarcodes.isNotEmpty()) {
                                filteredUiList[i].conflictType + ":" + " " + filteredUiList[i].conflictNumber
                            } else {
                                "اسکن: " + filteredUiList[i].scannedNumber
                            },
                            filteredUiList[i].KBarCode in signedProductCodes,
                            onLongClick = {
                                if (filteredUiList[i].KBarCode !in signedProductCodes) {
                                    signedProductCodes.add(filteredUiList[i].KBarCode)
                                } else {
                                    signedProductCodes.remove(filteredUiList[i].KBarCode)
                                }
                                filterUiList()
                            }, onClick = {
                                openSearchActivity(filteredUiList[i])
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ScanFilterDropDownList(modifier: Modifier) {

        val scanValues =
            mutableListOf("اضافی", "کسری")

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = modifier
                .clickable { expanded = true }
                .testTag("CountActivityFilterDropDownList")
            ) {
                Text(text = scanFilter)
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                scanValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        scanFilter = it
                        filterUiList()
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun SignedFilterDropDownList(modifier: Modifier) {

        val signedValue = mutableListOf("همه", "نشانه دار", "بی نشانه")
        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = modifier.clickable { expanded = true }) {
                Text(text = signedFilter)
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                signedValue.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        signedFilter = it
                        filterUiList()
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun FileAlertDialog() {

        AlertDialog(

            buttons = {

                Column {

                    Text(
                        text = "نام فایل خروجی را وارد کنید", modifier = Modifier
                            .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                    )

                    OutlinedTextField(
                        value = fileName, onValueChange = {
                            fileName = it
                        },
                        modifier = Modifier
                            .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                            .align(Alignment.CenterHorizontally)
                    )

                    Button(modifier = Modifier
                        .padding(bottom = 10.dp, top = 10.dp, start = 10.dp, end = 10.dp)
                        .align(Alignment.CenterHorizontally),
                        onClick = {
                            openDialog = false
                            exportFile()
                        }) {
                        Text(text = "ذخیره")
                    }
                }
            },

            onDismissRequest = {
                openDialog = false
            }
        )
    }

    @Composable
    fun ClearAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openClearDialog = false
            },
            buttons = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = if (scannedNumber == 0) {
                            "فایل پاک شود؟"
                        } else {
                            "کالاهای اسکن شده پاک شوند؟"
                        },
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 22.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openClearDialog = false
                            clear()

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = { openClearDialog = false },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "خیر")
                        }
                    }
                }
            }
        )
    }
}
