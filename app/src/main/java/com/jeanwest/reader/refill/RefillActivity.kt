package com.jeanwest.reader.refill


import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.shared.*
import com.jeanwest.reader.shared.hardware.Barcode2D
import com.jeanwest.reader.shared.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class RefillActivity : ComponentActivity(), IBarcodeResult {

    private val barcode2D = Barcode2D(this)
    val inputBarcodes = ArrayList<String>()

    //ui parameters
    private var foundProductsNumber by mutableStateOf(0)
    var uiList = mutableStateListOf<Product>()
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var state = SnackbarHostState()
    var loading by mutableStateOf(false)
    private lateinit var queue: RequestQueue
    private var scanMode by mutableStateOf(true)
    private var creatingStockDraft by mutableStateOf(false)
    var refillProducts = mutableListOf<Product>()
    var scannedBarcodes = mutableListOf<String>()

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

        loading = true

        val url = "https://rfid-api.avakatan.ir/refill"

        val request = object : JsonArrayRequest(Method.GET, url, null, {

            inputBarcodes.clear()

            for (i in 0 until it.length()) {

                inputBarcodes.add(it.getJSONObject(i).getString("KBarCode"))
            }

            if (inputBarcodes.isEmpty()) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "خطی صفر است!",
                        null,
                        SnackbarDuration.Long
                    )
                }
                loading = false

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
                    val error =
                        JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            error.getString("message"),
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
            }
            loading = false
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

        loading = true

        getProductsV4(queue, state, mutableListOf(), inputBarcodes, { _, barcodes, _, _ ->

            refillProducts.clear()
            barcodes.forEach {
                it.scannedBarcodeNumber = 0
                it.scannedBarcode = ""
                it.scannedEPCs.clear()
                refillProducts.add(it)
            }
            syncScannedItemsToServer()
        }, {})
    }

    private fun syncScannedItemsToServer() {

        loading = true

        if (scannedBarcodes.size == 0) {

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
            loading = false
            return
        }

        val barcodeTableForV4 = mutableListOf<String>()

        val alreadySyncedBarcodes = mutableListOf<String>()
        refillProducts.forEach {
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
        }

        scannedBarcodes.forEach {
            if (it !in alreadySyncedBarcodes) {
                barcodeTableForV4.add(it)
            } else {
                val productIndex = refillProducts.indexOf(refillProducts.last { refillProduct ->
                    refillProduct.scannedBarcode == it
                })
                refillProducts[productIndex].scannedBarcodeNumber =
                    scannedBarcodes.count { it1 ->
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
            loading = false
            return
        }

        getProductsV4(
            queue,
            state,
            mutableListOf(),
            barcodeTableForV4,
            { _, barcodes, _, invalidBarcodes ->

                val junkBarcodes = mutableListOf<String>()
                for (i in 0 until barcodes.size) {

                    val isInRefillList = refillProducts.any { refillProduct ->
                        refillProduct.primaryKey == barcodes[i].primaryKey
                    }

                    if (isInRefillList) {

                        val productIndex =
                            refillProducts.indexOf(refillProducts.last { refillProduct ->
                                refillProduct.primaryKey == barcodes[i].primaryKey
                            })

                        refillProducts[productIndex].scannedBarcodeNumber =
                            scannedBarcodes.count { it1 ->
                                it1 == barcodes[i].scannedBarcode
                            }

                        refillProducts[productIndex].scannedBarcode = barcodes[i].scannedBarcode
                    } else {
                        junkBarcodes.add(barcodes[i].scannedBarcode)
                    }
                }

                for (i in 0 until invalidBarcodes.length()) {
                    junkBarcodes.add(invalidBarcodes[i].toString())
                }

                scannedBarcodes.removeAll(junkBarcodes)

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
                foundProductsNumber = uiList.filter { refillProduct ->
                    refillProduct.scannedBarcodeNumber > 0
                }.size
                loading = false

            },
            {
                uiList.clear()
                uiList.addAll(refillProducts)
                loading = false
            })
    }

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {

            scannedBarcodes.add(barcode)
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            saveToMemory()
            syncScannedItemsToServer()
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString(
            "refillProductsForTest",
            Gson().toJson(refillProducts).toString()
        )

        edit.putString("RefillBarcodeTable", JSONArray(scannedBarcodes).toString())
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scannedBarcodes = Gson().fromJson(
            memory.getString("RefillBarcodeTable", ""),
            scannedBarcodes.javaClass
        ) ?: mutableListOf()
    }

    fun clear(product: Product) {

        refillProducts.forEach {
            if (it.KBarCode == product.KBarCode) {
                it.scannedBarcodeNumber = 0
                scannedBarcodes.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                it.scannedBarcode = ""
            }
        }
        uiList.clear()
        uiList.addAll(refillProducts)
        foundProductsNumber = uiList.filter { refillProduct ->
            refillProduct.scannedBarcodeNumber > 0
        }.size
        saveToMemory()
    }

    private fun sendToStore() {

        if (foundProductsNumber == 0) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "کالایی برای ارسال وجود ندارد",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        uiList.forEach {
            if (it.scannedBarcodeNumber + it.scannedEPCNumber > it.wareHouseNumber) {
                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "تعداد ارسالی کالای ${it.KBarCode}" + " از موجودی انبار بیشتر است.",
                        null,
                        SnackbarDuration.Long
                    )
                }
                return
            }
        }

        creatingStockDraft = true

        val url = "https://rfid-api.avakatan.ir/stock-draft/refill"
        val request = object : JsonObjectRequest(Method.POST, url, null, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "اجناس با موفقیت به فروشگاه حواله شدند",
                    null,
                    SnackbarDuration.Long
                )
            }
            creatingStockDraft = false
            scannedBarcodes.clear()
            refillProducts.removeAll {
                it.scannedBarcodeNumber > 0
            }
            syncScannedItemsToServer()
            saveToMemory()

        }, {
            if (it is NoConnectionError) {
                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {
                val error =
                    JSONObject(it.networkResponse.data.decodeToString()).getJSONObject("error")
                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        error.getString("message"),
                        null,
                        SnackbarDuration.Long
                    )
                }
            }

            creatingStockDraft = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = java.util.HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] =
                    "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {

                val body = JSONObject()
                val products = JSONArray()

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
                Log.e("time", sdf.format(Date()))

                uiList.forEach {
                    repeat(it.scannedBarcodeNumber + it.scannedEPCNumber) { _ ->
                        val productJson = JSONObject()
                        productJson.put("BarcodeMain_ID", it.primaryKey)
                        productJson.put("kbarcode", it.KBarCode)
                        productJson.put("K_Name", it.kName)
                        products.put(productJson)
                    }
                }

                body.put("desc", "خطی با RFID")
                body.put("createDate", sdf.format(Date()))
                body.put("products", products)

                Log.e("error", body.toString())

                return body.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
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
        if (scanMode) {
            saveToMemory()
            stopBarcodeScan()
            queue.stop()
            beep.release()
            finish()
        } else {
            saveToMemory()
            scanMode = true
            getRefillBarcodes()
        }
    }

    private fun openSearchActivity(product: Product) {

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(product).toString())
        startActivity(intent)
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { if (scanMode) Content() else Content2() },
                    bottomBar = { if (scanMode) BottomBar() },
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
                        text = "پیدا شده: $foundProductsNumber",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Button(onClick = {
                        if (refillProducts.filter { it1 ->
                                it1.scannedNumber > 0
                            }.toMutableStateList().isEmpty()) {
                            CoroutineScope(Dispatchers.Default).launch {
                                state.showSnackbar(
                                    "هنوز کالایی برای ارسال اسکن نکرده اید",
                                    null,
                                    SnackbarDuration.Long
                                )
                            }
                        } else {
                            scanMode = false
                        }
                    }) {
                        Text(text = "ارسال اسکن شده ها")
                    }
                }
            }
        }
    }

    @Composable
    fun Content() {

        Column {

            if (loading) {

                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp)
                        .background(JeanswestBackground, Shapes.small)
                        .fillMaxWidth()
                ) {
                    LoadingCircularProgressIndicator(false, loading)
                }
            } else {
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

            if (uiList[i].scannedNumber > 0) {
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

    @Composable
    fun Content2() {

        Column {

            if (uiList.filter {
                    it.scannedBarcodeNumber > 0
                }.toMutableList().isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 56.dp)
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(256.dp)
                    ) {
                        Box(

                            modifier = Modifier
                                .background(color = Color.White, shape = Shapes.medium)
                                .size(256.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_empty_box),
                                contentDescription = "",
                                tint = Color.Unspecified,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Text(
                            "هنوز کالایی برای ارسال به فروشگاه اسکن نکرده اید",
                            style = Typography.h1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                        )
                    }
                }
            } else {

                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {

                    items(uiList.filter {
                        it.scannedBarcodeNumber > 0
                    }.size) { i ->
                        Item(
                            i,
                            uiList.filter {
                                it.scannedBarcodeNumber > 0
                            }.toMutableList(),
                            text1 = "اسکن: " + uiList.filter {
                                it.scannedBarcodeNumber > 0
                            }.toMutableList()[i].scannedNumber,
                            text2 = "انبار: " + uiList.filter {
                                it.scannedBarcodeNumber > 0
                            }.toMutableList()[i].wareHouseNumber.toString(),
                            enableWarehouseNumberCheck = true,
                        )
                    }
                }
            }
        }

        if (uiList.filter {
                it.scannedBarcodeNumber > 0
            }.toMutableList().isNotEmpty()) {
            SendRequestButton()
        }
    }

    @Composable
    fun SendRequestButton() {

        Box(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(0.dp))
                    .background(Color.White, RoundedCornerShape(0.dp))
                    .height(100.dp)
                    .align(Alignment.BottomCenter),
            ) {

                Button(modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp)
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .align(Alignment.Center),
                    enabled = !creatingStockDraft,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Jeanswest,
                        disabledBackgroundColor = DisableButtonColor,
                        disabledContentColor = Color.White
                    ),
                    onClick = {
                        if (!creatingStockDraft) {
                            sendToStore()
                        }
                    }) {
                    Text(
                        text = if (creatingStockDraft) "در حال ارسال ..." else "ارسال به فروشگاه",
                        style = Typography.body1,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
