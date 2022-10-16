package com.jeanwest.reader.checkOut

import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.shared.*
import com.jeanwest.reader.shared.test.Barcode2D
import com.jeanwest.reader.shared.theme.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

@OptIn(ExperimentalFoundationApi::class)
class CheckOutActivity : ComponentActivity(), IBarcodeResult {

    private val barcode2D = Barcode2D(this)

    //ui parameters
    var isDataLoading by mutableStateOf(false)
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var state = SnackbarHostState()
    var uiList = mutableStateListOf<Product>()
    private lateinit var queue: RequestQueue
    private var isSubmitting by mutableStateOf(false)
    private var scanMode by mutableStateOf(true)
    var scannedBarcodeTable = mutableListOf<String>()
    private var destination by mutableStateOf("انتخاب مقصد")
    private var destinations = mutableStateMapOf<String, Int>()
    private var source = 0
    var products = mutableListOf<Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        queue = Volley.newRequestQueue(this)

        setContent {
            Page()
        }

        getDestinationLists()
        loadMemory()

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
        syncScannedItemsToServer()
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

    private fun getDestinationLists() {

        val url = "https://rfid-api.avakatan.ir/department-infos"
        val request = object : JsonArrayRequest(Method.GET, url, null, {

            for (i in 0 until it.length()) {
                try {
                    val warehouses = it.getJSONObject(i).getJSONArray("wareHouses")

                    for (j in 0 until warehouses.length()) {
                        val warehouse = warehouses.getJSONObject(j)
                        if (warehouse.getString("WareHouseTypes_ID") == "2") {
                            destinations[warehouse.getString("WareHouseTitle")] =
                                warehouse.getInt("WareHouse_ID")
                        }
                    }
                } catch (e: Exception) {

                }
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
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = java.util.HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }
        }

        queue.add(request)
    }

    private fun syncScannedItemsToServer() {

        isDataLoading = true

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
            isDataLoading = false
            return
        }

        getProductsV4(queue, state, mutableListOf(), barcodeArray, { _, barcodes, _, _ ->

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
                    it.scannedBarcodeNumber = 1
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
            isDataLoading = false
        })
    }

    private fun sendToDestination() {

        if (destination == "انتخاب مقصد") {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "لطفا مقصد را انتخاب کنید",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        if (products.size == 0) {
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
            if (it.scannedBarcodeNumber > it.wareHouseNumber) {
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

        isSubmitting = true

        val url = "https://rfid-api.avakatan.ir/stock-draft"
        val request = object : JsonObjectRequest(Method.POST, url, null, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "حواله با موفقیت ثبت شد",
                    null,
                    SnackbarDuration.Long
                )
            }
            isSubmitting = false
            scannedBarcodeTable.clear()
            products.removeAll {
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
            isSubmitting = false
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
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
                val barcodeArray = JSONArray()
                val epcArray = JSONArray()

                uiList.forEach {
                    repeat(it.scannedEPCNumber) { i ->
                        val productJson = JSONObject()
                        productJson.put("BarcodeMain_ID", it.primaryKey)
                        productJson.put("kbarcode", it.KBarCode)
                        productJson.put("K_Name", it.kName)
                        productJson.put("epc", it.scannedEPCs[i])
                        epcArray.put(productJson)
                    }
                    repeat(it.scannedBarcodeNumber) { _ ->
                        val productJson = JSONObject()
                        productJson.put("BarcodeMain_ID", it.primaryKey)
                        productJson.put("kbarcode", it.KBarCode)
                        productJson.put("K_Name", it.kName)
                        barcodeArray.put(productJson)
                    }
                }

                body.put("desc", "حواله بین انباری با RFID")
                body.put("createDate", sdf.format(Date()))
                body.put("fromWarehouseId", source)
                body.put("toWarehouseId", destinations[destination])
                body.put("kbarcodes", barcodeArray)
                body.put("epcs", epcArray)

                return body.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
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
            "CheckOutBarcodeTable",
            JSONArray(scannedBarcodeTable).toString()
        )

        edit.putString(
            "CheckOutProducts",
            Gson().toJson(products).toString()
        )

        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        val type = object : TypeToken<List<Product>>() {}.type

        source = memory.getInt("userWarehouseCode", 0)

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("CheckOutBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        products = Gson().fromJson(
            memory.getString("CheckOutProducts", ""),
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

        if (scanMode) {
            saveToMemory()
            stopBarcodeScan()
            beep.release()
            queue.stop()
            finish()
        } else {
            saveToMemory()
            scanMode = true
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

                    CategoryFilterDropDownList(modifier = Modifier.align(Alignment.CenterVertically))

                    Button(onClick = {

                        if (destination == "انتخاب مقصد") {

                            CoroutineScope(Dispatchers.Default).launch {
                                state.showSnackbar(
                                    "لطفا مقصد را انتخاب کنید",
                                    null,
                                    SnackbarDuration.Long
                                )
                            }
                        } else if (products.filter { it1 ->
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
                    text = stringResource(id = R.string.checkOut),
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

            if (isDataLoading) {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp)
                        .background(JeanswestBackground, Shapes.small)
                        .fillMaxWidth()
                ) {
                    LoadingCircularProgressIndicator(false, isDataLoading)
                }
            } else {

                if (uiList.isEmpty()) {
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
                                "هنوز کالایی برای ثبت حواله اسکن نکرده اید",
                                style = Typography.h1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                            )
                        }
                    }
                } else {

                    LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {

                        items(uiList.size) { i ->
                            LazyColumnItem(i)
                        }
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
                text2 = "انبار: " + uiList[i].wareHouseNumber,
                colorFull = uiList[i].scannedNumber >= uiList[i].requestedNumber,
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
                            "هنوز کالایی برای ثبت حواله اسکن نکرده اید",
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
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Jeanswest,
                        disabledBackgroundColor = DisableButtonColor,
                        disabledContentColor = Color.White
                    ),
                    onClick = {
                        if (!isSubmitting) {
                            sendToDestination()
                        }
                    }) {
                    Text(
                        text = if (isSubmitting) "در حال ثبت ..." else "ثبت نهایی حواله",
                        style = Typography.body1,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun CategoryFilterDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
            Row(modifier = modifier.clickable { expanded = true }) {
                Text(
                    text = destination, Modifier
                        .align(Alignment.CenterVertically)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown, "", Modifier
                        .align(Alignment.CenterVertically)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                destinations.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        destination = it.key
                    }) {
                        Text(text = it.key)
                    }
                }
            }
        }
    }
}