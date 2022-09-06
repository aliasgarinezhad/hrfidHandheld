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
import com.jeanwest.reader.sharedClassesAndFiles.test.Barcode2D
import com.jeanwest.reader.sharedClassesAndFiles.theme.*
import com.jeanwest.reader.sharedClassesAndFiles.test.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject

class RefillActivity : ComponentActivity(), IBarcodeResult {

    private val barcode2D = Barcode2D(this)
    val inputBarcodes = ArrayList<String>()

    //ui parameters
    private var unFoundProductsNumber by mutableStateOf(0)
    private var uiList = mutableStateListOf<Product>()
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var state = SnackbarHostState()
    private var isDataLoading by mutableStateOf(false)
    private lateinit var queue : RequestQueue

    companion object {
        var refillProducts = mutableListOf<Product>()
        var scannedBarcodeTable = mutableListOf<String>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        queue = Volley.newRequestQueue(this)

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
                startBarcodeScan()
            } else if (keyCode == 4) {
                back()
            }
        }
        return true
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
                    val error = JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            error.getString("message"),
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
                it.scannedBarcodeNumber = 0
                it.scannedEPCNumber = 0
                it.scannedBarcode = ""
                it.scannedEPCs.clear()
                refillProducts.add(it)
            }

            isDataLoading = false

            if (scannedBarcodeTable.size != 0) {
                syncScannedItemsToServer()
            } else {

                refillProducts.sortBy {
                    it.productCode
                }
                refillProducts.sortBy {
                    it.name
                }
                refillProducts.sortBy { refillProduct ->
                    refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
                }
                uiList.clear()
                uiList.addAll(refillProducts)
                unFoundProductsNumber = uiList.size
            }

        }, {

            isDataLoading = false

            if (scannedBarcodeTable.size != 0) {
                syncScannedItemsToServer()
            }
        })
    }

    private fun syncScannedItemsToServer() {

        val barcodeTableForV4 = mutableListOf<String>()

        val alreadySyncedBarcodes = mutableListOf<String>()
        refillProducts.forEach {
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
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

        if (barcodeTableForV4.size == 0) {

            refillProducts.sortBy {
                it.productCode
            }
            refillProducts.sortBy {
                it.name
            }
            refillProducts.sortBy { refillProduct ->
                refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
            }
            uiList.clear()
            uiList.addAll(refillProducts)
            return
        }

       getProductsV4(queue, state, mutableListOf(), barcodeTableForV4, { _, barcodes ->

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

            refillProducts.sortBy {
               it.productCode
            }
            refillProducts.sortBy {
               it.name
            }
            refillProducts.sortBy { refillProduct ->
               refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber > 0
            }
            uiList.clear()
            uiList.addAll(refillProducts)
            unFoundProductsNumber = uiList.filter { refillProduct ->
                refillProduct.scannedBarcodeNumber + refillProduct.scannedEPCNumber == 0
            }.size

        }, {
            uiList.clear()
            uiList.addAll(refillProducts)
        })
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            scannedBarcodeTable.add(barcode)
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("RefillBarcodeTable", JSONArray(scannedBarcodeTable).toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("RefillBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()
    }

    fun clear(product: Product) {

        refillProducts.forEach {
            if (it.KBarCode == product.KBarCode) {
                it.scannedEPCNumber = 0
                it.scannedBarcodeNumber = 0
                scannedBarcodeTable.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                it.scannedEPCs.clear()
                it.scannedBarcode = ""
            }
        }
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
        stopBarcodeScan()
        queue.stop()
        beep.release()
        finish()
    }

    private fun openSearchActivity(product: Product) {

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(product).toString())
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

                    Button(onClick = {
                        Intent(this@RefillActivity, SendToStoreActivity::class.java).also {
                        startActivity(it)
                    } }) {
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
                LoadingCircularProgressIndicator(false, isDataLoading)
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