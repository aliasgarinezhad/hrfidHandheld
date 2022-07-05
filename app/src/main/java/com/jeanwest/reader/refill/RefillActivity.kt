package com.jeanwest.reader.refill


import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.sharedClassesAndFiles.*
import com.jeanwest.reader.sharedClassesAndFiles.theme.*
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray

class RefillActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 5
    private val barcode2D = Barcode2D(this)
    val inputBarcodes = ArrayList<String>()
    private var scanningJob: Job? = null

    //ui parameters
    private var isScanning by mutableStateOf(false)
    private var numberOfScanned by mutableStateOf(0)
    private var unFoundProductsNumber by mutableStateOf(0)
    private var uiList = mutableStateListOf<Product>()
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var scanTypeValue by mutableStateOf("بارکد")
    private var state = SnackbarHostState()
    private var isDataLoading by mutableStateOf(false)
    private lateinit var queue : RequestQueue

    companion object {
        var refillProducts = mutableListOf<Product>()
        var scannedEpcTable = mutableListOf<String>()
        var scannedBarcodeTable = mutableListOf<String>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        queue = Volley.newRequestQueue(this)

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode(rf, state)

        setContent {
            Page()
        }
        loadMemory()
        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onResume() {
        super.onResume()
        saveToMemory()
        loadMemory()
        getRefillBarcodes()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {

                if (scanTypeValue == "بارکد") {
                    stopRFScan()
                    startBarcodeScan()
                } else {
                    if (!isScanning) {

                        scanningJob = CoroutineScope(IO).launch {
                            startRFScan()
                        }

                    } else {

                        stopRFScan()
                        if (numberOfScanned != 0) {
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

    private fun stopRFScan() {

        scanningJob?.let {
            if (it.isActive) {
                isScanning = false // cause scanning routine loop to stop
                runBlocking { it.join() }
            }
        }
    }

    private suspend fun startRFScan() {

        isScanning = true
        if (!setRFPower(state, rf, rfPower)) {
            isScanning = false
            return
        }

        var epcTablePreviousSize = scannedEpcTable.size

        rf.startInventoryTag(0, 0, 0)

        while (isScanning) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        scannedEpcTable.add(uhfTagInfo.epc)
                    }
                } else {
                    break
                }
            }

            scannedEpcTable = scannedEpcTable.distinct().toMutableList()

            numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size

            val speed = scannedEpcTable.size - epcTablePreviousSize
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
            epcTablePreviousSize = scannedEpcTable.size

            saveToMemory()

            delay(1000)
        }

        rf.stopInventory()
        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
        saveToMemory()
    }

    private fun getRefillBarcodes() {

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/refill"

        val request = object : JsonArrayRequest(Method.GET, url, null, {

            inputBarcodes.clear()

            for (i in 0 until it.length()) {

                inputBarcodes.add(it.getJSONObject(i).getString("KBarCode"))
            }
            isDataLoading = false

            if (inputBarcodes.isEmpty()) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "خطی صفر است!",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {
                getRefillItems()
            }
        }, {
            when (it) {
                is NoConnectionError -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
                else -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            it.toString(),
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
            }
            isDataLoading = false
        }) {
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

    private fun getRefillItems() {

        isDataLoading = true

        getProductsV4(queue, state, mutableListOf(), inputBarcodes, { _, barcodes ->

            refillProducts.clear()
            barcodes.forEach {
                refillProducts.add(it)
            }

            isDataLoading = false

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
            } else {

                refillProducts.sortBy { refillProduct ->
                    refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
                }
                uiList.clear()
                uiList.addAll(refillProducts)
                unFoundProductsNumber = uiList.size
            }

        }, {

            isDataLoading = false

            if (numberOfScanned != 0) {
                syncScannedItemsToServer()
            }
        })
    }

    private fun syncScannedItemsToServer() {

        val epcTableForV4 = mutableListOf<String>()
        val barcodeTableForV4 = mutableListOf<String>()

        val alreadySyncedEpcs = mutableListOf<String>()
        val alreadySyncedBarcodes = mutableListOf<String>()
        refillProducts.forEach {
            if (it.scannedEPCNumber > 0) {
                alreadySyncedEpcs.addAll(it.scannedEPCs)
            }
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
        }

        scannedEpcTable.forEach {
            if (it !in alreadySyncedEpcs) {
                epcTableForV4.add(it)
            }
        }

        scannedBarcodeTable.forEach {
            if (it !in alreadySyncedBarcodes) {
                barcodeTableForV4.add(it)
            } else {
                val productIndex = refillProducts.indexOf(refillProducts.last { refillProduct ->
                    refillProduct.scannedBarcode == it
                })
                refillProducts[productIndex].scannedBarcodeNumber =
                    scannedBarcodeTable.count { it1 ->
                        it1 == it
                    }
            }
        }

        if (epcTableForV4.size == 0 && barcodeTableForV4.size == 0) {
            refillProducts.sortBy { refillProduct ->
                refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
            }
            uiList.clear()
            uiList.addAll(refillProducts)
            return
        }

       getProductsV4(queue, state, epcTableForV4, barcodeTableForV4, { epcs, barcodes ->

            val junkEpcs = mutableListOf<String>()
            for (i in 0 until epcs.size) {

                val isInRefillList = refillProducts.any { refillProduct ->
                    refillProduct.primaryKey == epcs[i].primaryKey
                }
                if (isInRefillList) {

                    val productIndex = refillProducts.indexOf(refillProducts.last { refillProduct ->
                        refillProduct.primaryKey == epcs[i].primaryKey
                    })

                    refillProducts[productIndex].scannedEPCNumber += 1
                    refillProducts[productIndex].scannedEPCs.add(epcs[i].scannedEPCs[0])
                } else {
                    junkEpcs.add(epcs[i].scannedEPCs[0])
                }
            }
            scannedEpcTable.removeAll(junkEpcs)

            val junkBarcodes = mutableListOf<String>()
            for (i in 0 until barcodes.size) {

                val isInRefillList = refillProducts.any { refillProduct ->
                    refillProduct.primaryKey == barcodes[i].primaryKey
                }

                if (isInRefillList) {

                    val productIndex = refillProducts.indexOf(refillProducts.last { refillProduct ->
                        refillProduct.primaryKey == barcodes[i].primaryKey
                    })

                    refillProducts[productIndex].scannedBarcodeNumber =
                        scannedBarcodeTable.count { it1 ->
                            it1 == barcodes[i].scannedBarcode
                        }

                    refillProducts[productIndex].scannedBarcode = barcodes[i].scannedBarcode
                } else {
                    junkBarcodes.add(barcodes[i].scannedBarcode)
                }
            }
            scannedBarcodeTable.removeAll(junkBarcodes)

            refillProducts.sortBy { refillProduct ->
                refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
            }
            uiList.clear()
            uiList.addAll(refillProducts)
            unFoundProductsNumber = uiList.filter { refillProduct ->
                refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber == 0
            }.size

            numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size

        }, {
            uiList.clear()
            uiList.addAll(refillProducts)
        })
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            scannedBarcodeTable.add(barcode)
            numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("RefillBarcodeTable", JSONArray(scannedBarcodeTable).toString())
        edit.putString("RefillEPCTable", JSONArray(scannedEpcTable).toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scannedEpcTable = Gson().fromJson(
            memory.getString("RefillEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("RefillBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
    }

    fun clear(product: Product) {

        refillProducts.forEach {
            if (it.KBarCode == product.KBarCode) {
                it.scannedEPCNumber = 0
                it.scannedBarcodeNumber = 0
                scannedEpcTable.removeAll(it.scannedEPCs)
                scannedBarcodeTable.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                it.scannedEPCs.clear()
                it.scannedBarcode = ""
            }
        }
        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
        uiList.clear()
        uiList.addAll(refillProducts)
        unFoundProductsNumber = uiList.filter { refillProduct ->
            refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber == 0
        }.size
        saveToMemory()
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

    private fun openSendToStoreActivity() {

        Intent(this, SendToStoreActivity::class.java).also {
            it.putExtra("RefillProducts", Gson().toJson(refillProducts.filter { it1 ->
                it1.scannedBarcodeNumber + it1.scannedEPCNumber > 0
            }).toString())
            it.putExtra("validScannedProductsNumber", numberOfScanned)
            startActivity(it)
        }
    }

    private fun openSearchActivity(product: Product) {

        val searchResultProduct = Product(
            name = product.name,
            KBarCode = product.KBarCode,
            imageUrl = product.imageUrl,
            color = product.color,
            size = product.size,
            productCode = product.productCode,
            rfidKey = product.rfidKey,
            primaryKey = product.primaryKey,
            originalPrice = product.originalPrice,
            salePrice = product.salePrice,
        )

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(searchResultProduct).toString())
        startActivity(intent)
    }

    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                    bottomBar = { BottomBar() },
                    snackbarHost = { ErrorSnackBar(state) },
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

            title = {
                Text(
                    text = stringResource(id = R.string.refill),
                    modifier = Modifier
                        .padding(end = 50.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
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
                    }

                    Text(
                        text = "خطی: ${uiList.size}",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Text(
                        text = "پیدا نشده: $unFoundProductsNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Button(onClick = { openSendToStoreActivity() }) {
                        Text(text = "ارسال")
                    }
                }
            }
        }
    }

    @Composable
    fun Content() {

        Column {

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .background(JeanswestBackground, Shapes.small)
                    .fillMaxWidth()
            ) {

                PowerSlider(scanTypeValue == "RFID", rfPower) { rfPower = it }
                LoadingCircularProgressIndicator(isScanning, isDataLoading)
            }

            LazyColumn(
                modifier = Modifier
                    .padding(bottom = 56.dp)
                    .testTag("RefillActivityLazyColumn")
            ) {

                items(uiList.size) { i ->
                    LazyColumnItem(i)
                }
            }
        }
    }

    @Composable
    fun LazyColumnItem(i: Int) {

        val topPaddingClearButton = if (i == 0) 8.dp else 4.dp

        Box {

            Item(
                i, uiList, true,
                text1 = "اسکن: " + uiList[i].scannedNumber,
                text2 = "انبار: " + uiList[i].wareHouseNumber.toString(),
                colorFull = uiList[i].scannedNumber > 0,
                enableWarehouseNumberCheck = true,
            ) {
                openSearchActivity(uiList[i])
            }

            if(uiList[i].scannedNumber > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = topPaddingClearButton, end = 8.dp)
                        .background(
                            shape = RoundedCornerShape(36.dp),
                            color = deleteCircleColor
                        )
                        .size(30.dp)
                        .align(Alignment.TopEnd)
                        .testTag("clear")
                        .clickable {
                            clear(uiList[i])
                        }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_clear_24),
                        contentDescription = "",
                        tint = deleteColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}