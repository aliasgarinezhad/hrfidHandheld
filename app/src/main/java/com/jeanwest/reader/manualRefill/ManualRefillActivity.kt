package com.jeanwest.reader.manualRefill

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import coil.annotation.ExperimentalCoilApi
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.sharedClassesAndFiles.*
import com.jeanwest.reader.sharedClassesAndFiles.test.Barcode2D
import com.jeanwest.reader.sharedClassesAndFiles.test.RFIDWithUHFUART
import com.jeanwest.reader.sharedClassesAndFiles.theme.*
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalFoundationApi::class)
class ManualRefillActivity : ComponentActivity(), IBarcodeResult {

    private val barcode2D = Barcode2D(this)
    val inputBarcodes = ArrayList<String>()

    //ui parameters
    private var isDataLoading by mutableStateOf(false)
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var state = SnackbarHostState()
    var uiList = mutableStateListOf<Product>()
    private lateinit var queue: RequestQueue

    companion object {
        var scannedBarcodeTable = mutableListOf<String>()
        var products = mutableListOf<Product>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        queue = Volley.newRequestQueue(this)

        setContent {
            Page()
        }

        loadMemory()

        if (scannedBarcodeTable.size != 0) {
            syncScannedItemsToServer()
        }

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onResume() {
        super.onResume()
        uiList.clear()
        uiList.addAll(products)
        uiList.sortBy {
            it.productCode
        }
        uiList.sortBy {
            it.name
        }
        uiList.sortBy { it1 ->
            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
        }
        barcodeInit()
        saveToMemory()
        loadMemory()
        getRefillBarcodes()
    }

    override fun onPause() {
        super.onPause()
        stopBarcodeScan()
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

        val url = "https://rfid-api.avakatan.ir/charge-requests"

        val request = object : JsonArrayRequest(Method.GET, url, null, {

            inputBarcodes.clear()

            for (i in 0 until it.length()) {

                inputBarcodes.add(it.getJSONObject(i).getString("KBarCode"))
            }
            isDataLoading = false
            getRefillItems()
        }, {
            when {
                it is NoConnectionError -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
                it.networkResponse.statusCode == 422 -> {
                    Log.e("error", "charge database is empty")
                }
                else -> {
                    val error = JSONObject(
                        it.networkResponse?.data?.decodeToString().toString()
                    ).getJSONObject("error")
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

            barcodes.forEach {

                var isInRefillProductList = false

                run forEach1@{
                    products.forEach { it1 ->
                        if (it1.KBarCode == it.KBarCode) {
                            it1.requestedNum += 1
                            isInRefillProductList = true
                            return@forEach1
                        }
                    }
                }
                if (!isInRefillProductList) {
                    it.scannedBarcodeNumber = 0
                    it.scannedEPCNumber = 0
                    it.scannedBarcode = ""
                    it.scannedEPCs.clear()
                    products.add(it)
                }
            }

            isDataLoading = false

            if (scannedBarcodeTable.size != 0) {
                syncScannedItemsToServer()
            } else {
                uiList.clear()
                uiList.addAll(products)
                uiList.sortBy {
                    it.productCode
                }
                uiList.sortBy {
                    it.name
                }
                uiList.sortBy { it1 ->
                    it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                }
            }

        }, {
            isDataLoading = false

            if (scannedBarcodeTable.size != 0) {
                syncScannedItemsToServer()
            }
        })
    }

    private fun syncScannedItemsToServer() {

        val barcodeArray = mutableListOf<String>()
        val alreadySyncedBarcodes = mutableListOf<String>()

        products.forEach {
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
        }

        scannedBarcodeTable.forEach {
            if (it !in alreadySyncedBarcodes) {
                barcodeArray.add(it)
            } else {
                val productIndex = products.indexOf(products.last { it1 ->
                    it1.scannedBarcode == it
                })
                products[productIndex].scannedBarcodeNumber =
                    scannedBarcodeTable.count { it1 ->
                        it1 == it
                    }
            }
        }

        if (barcodeArray.size == 0) {
            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy {
                it.productCode
            }
            uiList.sortBy {
                it.name
            }
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false
            return
        }

        isDataLoading = true

        getProductsV4(queue, state, mutableListOf(), barcodeArray, { _, barcodes ->


            barcodes.forEach {
                var isInRefillProductList = false

                run forEach1@{
                    products.forEach { it1 ->
                        if (it1.KBarCode == it.KBarCode) {
                            it1.scannedBarcode = it.scannedBarcode
                            it1.scannedBarcodeNumber += 1
                            isInRefillProductList = true
                            return@forEach1
                        }
                    }
                }
                if (!isInRefillProductList) {
                    products.add(it)
                }
            }

            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy {
                it.productCode
            }
            uiList.sortBy {
                it.name
            }
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false

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

            uiList.clear()
            uiList.addAll(products)
            uiList.sortBy { it1 ->
                it1.productCode
            }
            uiList.sortBy { it1 ->
                it1.name
            }
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false
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

        edit.putString(
            "ManualRefillWarehouseManagerBarcodeTable",
            JSONArray(scannedBarcodeTable).toString()
        )

        edit.putString(
            "ManualRefillProducts",
            Gson().toJson(products).toString()
        )

        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        val type = object : TypeToken<List<Product>>() {}.type

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("ManualRefillWarehouseManagerBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        products = Gson().fromJson(
            memory.getString("ManualRefillProducts", ""),
            type
        ) ?: ArrayList()
    }

    fun clear(product: Product) {

        val removedRefillProducts = mutableListOf<Product>()

        products.forEach {
            if (it.KBarCode == product.KBarCode) {

                scannedBarcodeTable.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                removedRefillProducts.add(it)
            }
        }
        products.removeAll(removedRefillProducts)
        removedRefillProducts.clear()

        uiList.clear()
        uiList.addAll(products)
        uiList.sortBy { it1 ->
            it1.productCode
        }
        uiList.sortBy { it1 ->
            it1.name
        }
        uiList.sortBy { it1 ->
            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
        }
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
        beep.release()
        queue.stop()
        finish()
    }

    private fun openSearchActivity(product: Product) {

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(product).toString())
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
                    bottomBar = { BottomBar() },
                    content = { Content() },
                    snackbarHost = { ErrorSnackBar(state) },
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

                    Button(onClick = {
                        Intent(
                            this@ManualRefillActivity,
                            AddProductToRefillListActivity::class.java
                        ).apply {
                            startActivity(this)
                        }
                    }) {
                        Text(text = "تعریف شارژ")
                    }

                    Button(onClick = {
                        Intent(this@ManualRefillActivity, SendToStoreActivity::class.java).also {
                            startActivity(it)
                        }
                    }) {
                        Text(text = "ارسال")
                    }
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

            title = {
                Text(
                    text = stringResource(id = R.string.manualRefill),
                    modifier = Modifier
                        .padding(end = 50.dp)
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

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .background(JeanswestBackground, Shapes.small)
                    .fillMaxWidth()
            ) {
                LoadingCircularProgressIndicator(false, isDataLoading)
            }

            LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {

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
                text2 = "انبار: " + uiList[i].wareHouseNumber,
                colorFull = uiList[i].scannedNumber >= uiList[i].requestedNum,
                enableWarehouseNumberCheck = true,
            ) {
                openSearchActivity(uiList[i])
            }

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