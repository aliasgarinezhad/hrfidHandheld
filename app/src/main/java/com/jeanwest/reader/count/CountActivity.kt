package com.jeanwest.reader.count

//import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.theme.MyApplicationTheme
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs


class CountActivity : ComponentActivity(), IBarcodeResult {

    private var lastScanEpcTable = mutableListOf<String>()
    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var epcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var barcodeTable = mutableListOf<String>()
    private var excelBarcodes = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    private var barcodeIsEnabled = false
    private val fileProducts = ArrayList<FileProduct>()
    private val scannedProducts = ArrayList<ScannedProduct>()
    private val invalidEpcs = ArrayList<String>()
    private var scanningJob: Job? = null
    private var barcodeToCategoryMap = mutableMapOf<String, String>()

    //ui parameters
    private var conflictResultProducts by mutableStateOf(ArrayList<ConflictResultProduct>())
    private var isScanning by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var shortageCodesNumber by mutableStateOf(0)
    private var additionalCodesNumber by mutableStateOf(0)
    private var number by mutableStateOf(0)
    private var fileName by mutableStateOf("خروجی")
    private var openDialog by mutableStateOf(false)
    private var uiList by mutableStateOf(ArrayList<ConflictResultProduct>())
    private var categoryFilter by mutableStateOf(0)
    private var categoryValues by mutableStateOf(arrayListOf("همه دسته ها", "نامعلوم"))
    private val scanValues =
        arrayListOf("همه اجناس", "تایید شده", "اضافی", "کسری", "اضافی فایل", "خراب")
    private var scanFilter by mutableStateOf(0)
    private var zoneValue by mutableStateOf(arrayListOf("همه", "در ناحیه"))
    private var signedValue by mutableStateOf(arrayListOf("همه", "نشانه دار", "بی نشانه"))
    private var zoneFilter by mutableStateOf(0)
    private var signedFilter by mutableStateOf(0)
    private var zoneProductCodes = mutableListOf<String>()
    private var signedProductCodes = mutableListOf<String>()
    private var openClearDialog by mutableStateOf(false)

    private val apiTimeout = 60000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)


    @OptIn(ExperimentalFoundationApi::class, ExperimentalCoilApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        setContent {
            Page()
        }
        loadMemory()

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode()

        if (intent.action == Intent.ACTION_SEND) {

            if (getString(R.string.xlsx) == intent.type || getString(R.string.xls) == intent.type) {

                checkLogin()
                rfInit()

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
                Toast.makeText(this, "فرمت فایل باید اکسل باشد", Toast.LENGTH_LONG).show()
                syncFileItemsToServer()
            }
        } else {
            while (!rf.setEPCMode()) {
                rf.free()
            }
            syncFileItemsToServer()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (barcodeIsEnabled) {
                    stopRFScan()
                    startBarcodeScan()
                } else {
                    if (!isScanning) {

                        scanningJob = CoroutineScope(IO).launch {
                            startRFScan()
                        }

                    } else {

                        stopRFScan()
                        if (number != 0) {
                            syncScannedItemsToServer()
                        }
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

    private fun setRFEpcMode(): Boolean {
        for (i in 0..11) {
            if (rf.setEPCMode()) {
                return true
            }
        }
        CoroutineScope(Main).launch {
            Toast.makeText(
                this@CountActivity,
                "مشکلی در سخت افزار پیش آمده است",
                Toast.LENGTH_LONG
            ).show()
        }
        return false
    }

    private fun stopRFScan() {

        scanningJob?.let {
            if (it.isActive) {
                isScanning = false // cause scanning routine loop to stop
                runBlocking { it.join() }
            }
        }
    }

    private fun checkLogin() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        if (memory.getString("username", "") != "") {

            MainActivity.username = memory.getString("username", "")!!
            MainActivity.token = memory.getString("accessToken", "")!!
        } else {
            Toast.makeText(
                this,
                "لطفا ابتدا به حساب کاربری خود وارد شوید",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun rfInit() {

        val frequency: Int
        val rfLink = 2

        when (Build.MODEL) {
            getString(R.string.EXARK) -> {
                frequency = 0x08
            }
            getString(R.string.chainway) -> {
                frequency = 0x04
            }
            else -> {
                Toast.makeText(
                    this,
                    "این دستگاه توسط برنامه پشتیبانی نمی شود",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        for (i in 0..11) {

            if (rf.init()) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            } else {
                rf.free()
            }
        }

        for (i in 0..11) {

            if (rf.setFrequencyMode(frequency)) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        for (i in 0..11) {

            if (rf.setRFLink(rfLink)) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        setRFEpcMode()
    }

    private suspend fun startRFScan() {

        isScanning = true
        if (!setRFPower(rfPower)) {
            isScanning = false
            return
        }

        lastScanEpcTable.clear()
        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        lastScanEpcTable.add(uhfTagInfo.epc)
                        epcTable.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            epcTable = epcTable.distinct().toMutableList()
            lastScanEpcTable = lastScanEpcTable.distinct().toMutableList()

            number = epcTable.size + barcodeTable.size

            val speed = epcTable.size - epcTablePreviousSize
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
            epcTablePreviousSize = epcTable.size

            saveToMemory()

            delay(1000)
        }

        rf.stopInventory()
        number = epcTable.size + barcodeTable.size
        saveToMemory()
    }

    private fun getConflicts(
        fileProducts: ArrayList<FileProduct>,
        scannedProducts: ArrayList<ScannedProduct>,
        invalidEPCs: ArrayList<String>
    ): ArrayList<ConflictResultProduct> {

        val result = ArrayList<ConflictResultProduct>()

        val samePrimaryKeys = ArrayList<Long>()

        scannedProducts.forEach { scannedProduct ->

            fileProducts.forEach { fileProduct ->

                if (scannedProduct.primaryKey == fileProduct.primaryKey) {

                    val resultData = ConflictResultProduct(
                        name = fileProduct.name,
                        KBarCode = fileProduct.KBarCode,
                        imageUrl = fileProduct.imageUrl,
                        category = fileProduct.category,
                        matchedNumber = abs(scannedProduct.scannedNumber - fileProduct.number),
                        scannedNumber = scannedProduct.scannedNumber,
                        result =
                        when {
                            scannedProduct.scannedNumber > fileProduct.number -> {
                                "اضافی: " + (scannedProduct.scannedNumber - fileProduct.number) + "(${fileProduct.number})"
                            }
                            scannedProduct.scannedNumber < fileProduct.number -> {
                                "کسری: " + (fileProduct.number - scannedProduct.scannedNumber) + "(${fileProduct.number})"
                            }
                            else -> {
                                "تایید شده: " + fileProduct.number
                            }
                        },
                        scan = when {
                            scannedProduct.scannedNumber > fileProduct.number -> {
                                "اضافی فایل"
                            }
                            scannedProduct.scannedNumber < fileProduct.number -> {
                                "کسری"
                            }
                            else -> {
                                "تایید شده"
                            }
                        },
                        productCode = fileProduct.productCode,
                        size = fileProduct.size,
                        color = fileProduct.color,
                        originalPrice = fileProduct.originalPrice,
                        salePrice = fileProduct.salePrice,
                        rfidKey = fileProduct.rfidKey,
                        primaryKey = fileProduct.primaryKey
                    )
                    result.add(resultData)
                    samePrimaryKeys.add(fileProduct.primaryKey)
                }
            }
            if (scannedProduct.primaryKey !in samePrimaryKeys) {
                val resultData = ConflictResultProduct(
                    name = scannedProduct.name,
                    KBarCode = scannedProduct.KBarCode,
                    imageUrl = scannedProduct.imageUrl,
                    category = "نامعلوم",
                    matchedNumber = scannedProduct.scannedNumber,
                    scannedNumber = scannedProduct.scannedNumber,
                    result = "اضافی: " + scannedProduct.scannedNumber + "(0)",
                    scan = "اضافی",
                    productCode = scannedProduct.productCode,
                    size = scannedProduct.size,
                    color = scannedProduct.color,
                    originalPrice = scannedProduct.originalPrice,
                    salePrice = scannedProduct.salePrice,
                    rfidKey = scannedProduct.rfidKey,
                    primaryKey = scannedProduct.primaryKey
                )
                result.add(resultData)
            }
        }

        fileProducts.forEach { fileProduct ->
            if (fileProduct.primaryKey !in samePrimaryKeys) {
                val resultData = ConflictResultProduct(
                    name = fileProduct.name,
                    KBarCode = fileProduct.KBarCode,
                    imageUrl = fileProduct.imageUrl,
                    category = fileProduct.category,
                    matchedNumber = fileProduct.number,
                    scannedNumber = 0,
                    result = "کسری: " + fileProduct.number + "(${fileProduct.number})",
                    scan = "کسری",
                    productCode = fileProduct.productCode,
                    size = fileProduct.size,
                    color = fileProduct.color,
                    originalPrice = fileProduct.originalPrice,
                    salePrice = fileProduct.salePrice,
                    rfidKey = fileProduct.rfidKey,
                    primaryKey = fileProduct.primaryKey
                )
                result.add(resultData)
            }
        }

        invalidEPCs.forEach {
            val resultData = ConflictResultProduct(
                name = it,
                KBarCode = "",
                imageUrl = "",
                category = "نامعلوم",
                matchedNumber = 0,
                scannedNumber = 1,
                result = "خراب: " + 1,
                scan = "خراب",
                productCode = "",
                size = "",
                color = "",
                originalPrice = "",
                salePrice = "",
                rfidKey = 0L,
                primaryKey = 0L
            )
            result.add(resultData)
        }

        return result
    }

    private fun filterResult(conflictResult: ArrayList<ConflictResultProduct>): ArrayList<ConflictResultProduct> {

        val signedFilterOutput =
            when {
                signedValue[signedFilter] == "همه" -> {
                    conflictResult
                }
                signedValue[signedFilter] == "نشانه دار" -> {
                    conflictResult.filter {
                        it.KBarCode in signedProductCodes
                    } as ArrayList<ConflictResultProduct>
                }
                signedValue[signedFilter] == "بی نشانه" -> {
                    conflictResult.filter {
                        it.KBarCode !in signedProductCodes
                    } as ArrayList<ConflictResultProduct>
                }
                else -> {
                    conflictResult
                }
            }

        val zoneFilterOutput =
            when {
                zoneValue[zoneFilter] == "همه" -> {
                    signedFilterOutput
                }
                zoneValue[zoneFilter] == "در ناحیه" -> {
                    signedFilterOutput.filter {
                        it.productCode in zoneProductCodes
                    } as ArrayList<ConflictResultProduct>
                }
                else -> {
                    signedFilterOutput
                }
            }

        val categoryFilterOutput =
            if (categoryValues[categoryFilter] == "همه دسته ها") {
                zoneFilterOutput
            } else {
                zoneFilterOutput.filter {
                    it.category == categoryValues[categoryFilter]
                } as ArrayList<ConflictResultProduct>
            }

        shortagesNumber = 0
        categoryFilterOutput.filter {
            it.scan == "کسری"
        }.forEach {
            shortagesNumber += it.matchedNumber
        }

        additionalNumber = 0
        categoryFilterOutput.filter {
            it.scan == "اضافی" || it.scan == "اضافی فایل"
        }.forEach {
            additionalNumber += it.matchedNumber
        }

        shortageCodesNumber = categoryFilterOutput.filter { it.scan == "کسری" }.size
        additionalCodesNumber =
            categoryFilterOutput.filter { it.scan == "اضافی" || it.scan == "اضافی فایل" }.size

        val uiListParameters =
            when {
                scanValues[scanFilter] == "همه اجناس" -> {
                    categoryFilterOutput
                }
                scanValues[scanFilter] == "اضافی" -> {
                    categoryFilterOutput.filter {
                        it.scan == "اضافی" || it.scan == "اضافی فایل"
                    } as ArrayList<ConflictResultProduct>
                }
                else -> {
                    categoryFilterOutput.filter {
                        it.scan == scanValues[scanFilter]
                    } as ArrayList<ConflictResultProduct>
                }
            }

        return uiListParameters
    }

    private fun setRFPower(power: Int): Boolean {
        if (rf.power != power) {

            for (i in 0..11) {
                if (rf.setPower(power)) {
                    return true
                }
            }
            CoroutineScope(Main).launch {
                Toast.makeText(
                    this@CountActivity,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        } else {
            return true
        }
    }

    private fun syncFileItemsToServer() {

        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val fileJsonArray = it.getJSONArray("KBarCodes")
            fileProducts.clear()

            for (i in 0 until fileJsonArray.length()) {

                val fileProduct = FileProduct(
                    name = fileJsonArray.getJSONObject(i).getString("productName"),
                    KBarCode = fileJsonArray.getJSONObject(i).getString("KBarCode"),
                    imageUrl = fileJsonArray.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = fileJsonArray.getJSONObject(i).getLong("BarcodeMain_ID"),
                    number = fileJsonArray.getJSONObject(i).getInt("handheldCount"),
                    category = barcodeToCategoryMap[fileJsonArray.getJSONObject(i)
                        .getString("KBarCode")] ?: "نامعلوم",
                    productCode = fileJsonArray.getJSONObject(i).getString("K_Bar_Code"),
                    size = fileJsonArray.getJSONObject(i).getString("Size"),
                    color = fileJsonArray.getJSONObject(i).getString("Color"),
                    originalPrice = fileJsonArray.getJSONObject(i).getString("OrgPrice"),
                    salePrice = fileJsonArray.getJSONObject(i).getString("SalePrice"),
                    rfidKey = fileJsonArray.getJSONObject(i).getLong("RFID"),
                )
                fileProducts.add(fileProduct)
            }

            categoryValues.clear()
            categoryValues.add("همه دسته ها")
            categoryValues.add("نامعلوم")
            barcodeToCategoryMap.values.forEach { category ->
                if (category !in categoryValues) {
                    categoryValues.add(category)
                }
            }
            if (number != 0) {
                conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
                uiList = filterResult(conflictResultProducts)
                syncScannedItemsToServer()
            } else {
                conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
                uiList = filterResult(conflictResultProducts)
            }
        }, { response ->

            if (excelBarcodes.isEmpty()) {
                Toast.makeText(
                    this@CountActivity,
                    "هیچ بارکدی در فایل یافت نشد",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this@CountActivity, response.toString(), Toast.LENGTH_LONG)
                    .show()
            }

            if (number != 0) {
                syncScannedItemsToServer()
            }

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

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                excelBarcodes.forEach {
                    barcodeArray.put(it)
                }

                json.put("KBarCodes", barcodeArray)

                return json.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        val queue = Volley.newRequestQueue(this@CountActivity)
        queue.add(request)
    }

    private fun syncScannedItemsToServer() {

        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")
            val invalids = it.getJSONArray("invalidEpcs")
            val similarIndexes = arrayListOf<Int>()

            for (i in 0 until barcodes.length()) {

                for (j in 0 until epcs.length()) {

                    if (barcodes.getJSONObject(i)
                            .getString("KBarCode") == epcs.getJSONObject(j)
                            .getString("KBarCode")
                    ) {
                        epcs.getJSONObject(j).put(
                            "handheldCount",
                            epcs.getJSONObject(j)
                                .getInt("handheldCount") + barcodes.getJSONObject(i)
                                .getInt("handheldCount")
                        )
                        similarIndexes.add(i)
                        break
                    }
                }
            }
            for (i in 0 until barcodes.length()) {

                if (i !in similarIndexes) {
                    epcs.put(it.getJSONArray("KBarCodes")[i])
                }
            }

            scannedProducts.clear()

            for (i in 0 until epcs.length()) {

                val scannedProduct = ScannedProduct(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedNumber = epcs.getJSONObject(i).getInt("handheldCount"),
                    productCode = epcs.getJSONObject(i).getString("K_Bar_Code"),
                    size = epcs.getJSONObject(i).getString("Size"),
                    color = epcs.getJSONObject(i).getString("Color"),
                    originalPrice = epcs.getJSONObject(i).getString("OrgPrice"),
                    salePrice = epcs.getJSONObject(i).getString("SalePrice"),
                    rfidKey = epcs.getJSONObject(i).getLong("RFID"),
                )
                scannedProducts.add(scannedProduct)
            }

            invalidEpcs.clear()
            for (i in 0 until invalids.length()) {
                invalidEpcs.add(invalids.getString(i))
            }

            conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)

        }, { response ->
            if ((epcTable.size + barcodeTable.size) == 0) {
                Toast.makeText(this, "کالایی جهت بررسی وجود ندارد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CountActivity, response.toString(), Toast.LENGTH_LONG)
                    .show()
            }
            conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)
            Log.e("network error", response.toString())
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

                epcTable.forEach {
                    epcArray.put(it)
                }

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                barcodeTable.forEach {
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

        val queue = Volley.newRequestQueue(this@CountActivity)
        queue.add(request)
    }


    private fun getZoneItems() {
        val url = "http://rfid-api.avakatan.ir/products/v3"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")

            zoneProductCodes.clear()
            for (i in 0 until epcs.length()) {
                zoneProductCodes.add(epcs.getJSONObject(i).getString("K_Bar_Code"))
            }
            uiList = filterResult(conflictResultProducts)

            Toast.makeText(this, "ناحیه تعریف شد", Toast.LENGTH_SHORT).show()

        }, { response ->
            if (lastScanEpcTable.size == 0) {
                Toast.makeText(this, "کالایی جهت بررسی وجود ندارد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CountActivity, response.toString(), Toast.LENGTH_LONG)
                    .show()
            }
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

                lastScanEpcTable.forEach {
                    epcArray.put(it)
                }

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                json.put("KBarCodes", barcodeArray)

                return json.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        val queue = Volley.newRequestQueue(this@CountActivity)
        queue.add(request)
    }

    private fun readXLSXFile(uri: Uri) {

        val workbook: XSSFWorkbook
        try {
            workbook = XSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {
            Toast.makeText(this, "فایل نامعتبر است", Toast.LENGTH_LONG).show()
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {
            Toast.makeText(this, "فایل خالی است", Toast.LENGTH_LONG).show()
            return
        }

        barcodeToCategoryMap.clear()
        excelBarcodes.clear()

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {

                barcodeToCategoryMap[sheet.getRow(i).getCell(0).stringCellValue] =
                    sheet.getRow(i).getCell(2)?.stringCellValue ?: "نامعلوم"

                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    excelBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        saveToMemory()
        syncFileItemsToServer()
    }

    private fun readXLSFile(uri: Uri) {

        val workbook: HSSFWorkbook
        try {
            workbook = HSSFWorkbook(contentResolver.openInputStream(uri))
        } catch (e: IOException) {
            Toast.makeText(this, "فایل نامعتبر است", Toast.LENGTH_LONG).show()
            return
        }

        val sheet = workbook.getSheetAt(0)
        if (sheet == null) {
            Toast.makeText(this, "فایل خالی است", Toast.LENGTH_LONG).show()
            return
        }

        barcodeToCategoryMap.clear()
        excelBarcodes.clear()

        for (i in 1 until sheet.physicalNumberOfRows) {
            if (sheet.getRow(i).getCell(0) == null || sheet.getRow(i).getCell(1) == null) {
                break
            } else {

                barcodeToCategoryMap[sheet.getRow(i).getCell(0).stringCellValue] =
                    sheet.getRow(i).getCell(2)?.stringCellValue ?: "نامعلوم"

                repeat(sheet.getRow(i).getCell(1).numericCellValue.toInt()) {
                    excelBarcodes.add(sheet.getRow(i).getCell(0).stringCellValue)
                }
            }
        }

        saveToMemory()
        syncFileItemsToServer()
    }


    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            barcodeTable.add(barcode)
            number = epcTable.size + barcodeTable.size
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("FileAttachmentEPCTable", JSONArray(epcTable).toString())
        edit.putString("FileAttachmentBarcodeTable", JSONArray(barcodeTable).toString())
        edit.putString("FileAttachmentFileZoneCodesTable", JSONArray(zoneProductCodes).toString())
        edit.putString(
            "FileAttachmentFileSignedCodesTable",
            JSONArray(signedProductCodes).toString()
        )
        edit.putString("FileAttachmentFileBarcodeTable", JSONArray(excelBarcodes).toString())
        edit.putString(
            "FileAttachmentBarcodeToCategoryMapTable",
            JSONObject(barcodeToCategoryMap as Map<*, *>).toString()
        )
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        epcTable = Gson().fromJson(
            memory.getString("FileAttachmentEPCTable", ""),
            epcTable.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = epcTable.size

        barcodeTable = Gson().fromJson(
            memory.getString("FileAttachmentBarcodeTable", ""),
            barcodeTable.javaClass
        ) ?: mutableListOf()

        excelBarcodes = Gson().fromJson(
            memory.getString("FileAttachmentFileBarcodeTable", ""),
            excelBarcodes.javaClass
        ) ?: mutableListOf()

        barcodeToCategoryMap = Gson().fromJson(
            memory.getString("FileAttachmentBarcodeToCategoryMapTable", ""),
            barcodeToCategoryMap.javaClass
        ) ?: mutableMapOf()

        zoneProductCodes = Gson().fromJson(
            memory.getString("FileAttachmentFileZoneCodesTable", ""),
            zoneProductCodes.javaClass
        ) ?: mutableListOf()

        signedProductCodes = Gson().fromJson(
            memory.getString("FileAttachmentFileSignedCodesTable", ""),
            signedProductCodes.javaClass
        ) ?: mutableListOf()

        number = epcTable.size + barcodeTable.size
    }

    private fun clear() {

        if (number != 0) {
            barcodeTable.clear()
            epcTable.clear()
            lastScanEpcTable.clear()
            invalidEpcs.clear()
            zoneProductCodes.clear()
            epcTablePreviousSize = 0
            number = 0
            scannedProducts.clear()
            conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)
            openDialog = false
            saveToMemory()
        } else {
            barcodeTable.clear()
            epcTable.clear()
            lastScanEpcTable.clear()
            invalidEpcs.clear()
            zoneProductCodes.clear()
            epcTablePreviousSize = 0
            number = 0
            excelBarcodes.clear()
            barcodeToCategoryMap.clear()
            scannedProducts.clear()
            fileProducts.clear()
            conflictResultProducts = getConflicts(fileProducts, scannedProducts, invalidEpcs)
            uiList = filterResult(conflictResultProducts)
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
        finish()
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

        conflictResultProducts.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.KBarCode)
            row.createCell(1).setCellValue(it.scannedNumber.toDouble())
            row.createCell(2).setCellValue(it.category)

            if (it.scan == "کسری") {
                row.createCell(3).setCellValue(it.matchedNumber.toDouble())
            } else if (it.scan == "اضافی" || it.scan == "اضافی فایل") {
                row.createCell(4).setCellValue(it.matchedNumber.toDouble())
            }

            if (it.KBarCode in signedProductCodes) {
                row.createCell(5).setCellValue("نشانه دار")
            }
        }

        val row = sheet.createRow(sheet.physicalNumberOfRows)
        row.createCell(0).setCellValue("مجموع")
        row.createCell(1).setCellValue(number.toDouble())
        row.createCell(3).setCellValue(shortagesNumber.toDouble())
        row.createCell(4).setCellValue(additionalNumber.toDouble())

        val sheet2 = workbook.createSheet("کسری")

        val header2Row = sheet2.createRow(sheet2.physicalNumberOfRows)
        header2Row.createCell(0).setCellValue("کد جست و جو")
        header2Row.createCell(1).setCellValue("موجودی")
        header2Row.createCell(2).setCellValue("دسته")
        header2Row.createCell(3).setCellValue("کسری")
        header2Row.createCell(4).setCellValue("نشانه")

        conflictResultProducts.forEach {

            if (it.scan == "کسری") {
                val shortageRow = sheet2.createRow(sheet2.physicalNumberOfRows)
                shortageRow.createCell(0).setCellValue(it.KBarCode)
                shortageRow.createCell(1)
                    .setCellValue(it.scannedNumber.toDouble() + it.matchedNumber.toDouble())
                shortageRow.createCell(2).setCellValue(it.category)
                shortageRow.createCell(3).setCellValue(it.matchedNumber.toDouble())

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

        conflictResultProducts.forEach {

            if (it.scan == "اضافی") {
                val additionalRow = sheet3.createRow(sheet3.physicalNumberOfRows)
                additionalRow.createCell(0).setCellValue(it.KBarCode)
                additionalRow.createCell(1).setCellValue(it.matchedNumber - it.scannedNumber.toDouble())
                additionalRow.createCell(2).setCellValue(it.category)
                additionalRow.createCell(3).setCellValue(it.matchedNumber.toDouble())

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
        applicationContext.startActivity(shareIntent)
    }

    private fun openSearchActivity(product: ConflictResultProduct) {

        val productJson = JSONObject()
        productJson.put("productName", product.name)
        productJson.put("K_Bar_Code", product.productCode)
        productJson.put("kbarcode", product.KBarCode)
        productJson.put("OrigPrice", product.originalPrice)
        productJson.put("SalePrice", product.salePrice)
        productJson.put("BarcodeMain_ID", product.primaryKey)
        productJson.put("RFID", product.rfidKey)
        productJson.put("ImgUrl", product.imageUrl)
        productJson.put("dbCountDepo", 0)
        productJson.put("dbCountStore", 0)
        productJson.put("Size", product.size)
        productJson.put("Color", product.color)

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", productJson.toString())
        startActivity(intent)
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                )
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
                IconButton(onClick = { openDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_share_24),
                        contentDescription = ""
                    )
                }
                IconButton(onClick = { openClearDialog = true }) {
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

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        var slideValue by rememberSaveable { mutableStateOf(30F) }
        var switchValue by rememberSaveable { mutableStateOf(false) }

        Column {

            if (openDialog) {
                FileAlertDialog()
            }

            if (openClearDialog) {
                ClearAlertDialog()
            }

            Column(
                modifier = Modifier
                    .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier.height(40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    Row(
                        modifier = Modifier
                            .weight(2.5F)
                            .fillMaxHeight()
                    ) {

                        Text(
                            text = "توان (" + slideValue.toInt() + ")  ",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .align(Alignment.CenterVertically),
                            textAlign = TextAlign.Center
                        )

                        Slider(
                            value = slideValue,
                            onValueChange = {
                                slideValue = it
                                rfPower = it.toInt()
                            },
                            enabled = true,
                            valueRange = 5f..30f,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 8.dp)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "بارکد",
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .align(Alignment.CenterVertically),
                        )

                        Switch(
                            checked = switchValue,
                            onCheckedChange = {
                                barcodeIsEnabled = it
                                switchValue = it
                            },
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    Text(
                        text = "تعداد اسکن شده: $number",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
                            .weight(1F),
                    )

                    Text(
                        text = "کسری ها(یکتا): $shortagesNumber($shortageCodesNumber)",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .weight(1F),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    Text(
                        text = "تعریف به عنوان ناحیه",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
                            .clickable { getZoneItems() }
                            .weight(1F),
                    )

                    Text(
                        text = "اضافی ها(یکتا): $additionalNumber($additionalCodesNumber)",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .weight(1F),
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(),
                ) {

                    Row(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 8.dp),
                    ) {
                        ScanFilterDropDownList()
                        ZoneFilterDropDownList()
                    }
                    Row(
                        modifier = Modifier
                            .weight(1F)
                    ) {
                        CategoryFilterDropDownList()
                        SignedFilterDropDownList()
                    }
                }

                if (isScanning) {
                    Row(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.primary)
                    }
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 2.dp)) {

                items(uiList.size) { i ->
                    LazyColumnItem(i)
                }
            }
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun LazyColumnItem(i: Int) {

        val modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp, end = 8.dp)
            .wrapContentWidth()

        Row(
            modifier = Modifier
                .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                .background(
                    color = if (uiList[i].KBarCode !in signedProductCodes) {
                        MaterialTheme.colors.onPrimary
                    } else {
                        MaterialTheme.colors.onSecondary
                    },
                    shape = MaterialTheme.shapes.small
                )
                .fillMaxWidth()
                .height(100.dp)
                .combinedClickable(
                    onClick = {
                        openSearchActivity(uiList[i])
                    },
                    onLongClick = {
                        if (uiList[i].KBarCode !in signedProductCodes) {
                            signedProductCodes.add(uiList[i].KBarCode)
                        } else {
                            signedProductCodes.remove(uiList[i].KBarCode)
                        }
                        uiList = filterResult(conflictResultProducts)
                    },
                ),
        ) {

            Image(
                painter = rememberImagePainter(
                    uiList[i].imageUrl,
                ),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(100.dp)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )

            Column(modifier = Modifier.fillMaxHeight()) {
                Text(
                    text = uiList[i].name,
                    style = MaterialTheme.typography.h1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )

                Text(
                    text = uiList[i].KBarCode,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )

                Text(
                    text = uiList[i].result,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
            }

            /*Text(
                text = uiList[i].scannedNumber.toString(),
                style = MaterialTheme.typography.h1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically)
            )*/
        }
    }

    @Composable
    fun ScanFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = scanValues[scanFilter])
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
                        scanFilter = scanValues.indexOf(it)
                        uiList = filterResult(conflictResultProducts)
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun CategoryFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = categoryValues[categoryFilter])
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                categoryValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        categoryFilter = categoryValues.indexOf(it)
                        uiList = filterResult(conflictResultProducts)
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun ZoneFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = zoneValue[zoneFilter])
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                zoneValue.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        zoneFilter = zoneValue.indexOf(it)
                        uiList = filterResult(conflictResultProducts)
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun SignedFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                Text(text = signedValue[signedFilter])
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
                        signedFilter = signedValue.indexOf(it)
                        uiList = filterResult(conflictResultProducts)
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
                        text = if (number == 0) {
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

    @ExperimentalFoundationApi
    @Preview
    @Composable
    fun Preview() {

        openClearDialog = true

        ClearAlertDialog()
    }
}