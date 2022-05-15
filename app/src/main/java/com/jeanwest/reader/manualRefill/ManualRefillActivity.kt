package com.jeanwest.reader.manualRefill

//import com.jeanwest.reader.hardware.Barcode2D
//import com.rscja.deviceapi.RFIDWithUHFUART
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.hardware.setRFEpcMode
import com.jeanwest.reader.hardware.setRFPower
import com.jeanwest.reader.search.SearchResultProducts
import com.jeanwest.reader.search.SearchSubActivity
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.theme.doneColor
import com.jeanwest.reader.theme.doneColorDarkerShade
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalFoundationApi::class)
class ManualRefillActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 5
    private val barcode2D = Barcode2D(this)
    private var scanningJob: Job? = null

    //ui parameters
    private var isScanning by mutableStateOf(false)
    private var isDataLoading by mutableStateOf(false)
    private var numberOfScanned by mutableStateOf(0)
    private var openClearDialog by mutableStateOf(false)
    private val apiTimeout = 30000
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    var signedProductCodes = mutableListOf<String>()
    private val scanTypeValues = mutableListOf("RFID", "بارکد")
    private var scanTypeValue by mutableStateOf("بارکد")
    private var state = SnackbarHostState()
    var uiList = mutableStateListOf<ManualRefillProduct>()
    private var selectMode by mutableStateOf(false)

    companion object {
        var scannedEpcTable = mutableListOf<String>()
        var scannedBarcodeTable = mutableListOf<String>()
        var manualRefillProducts = ArrayList<ManualRefillProduct>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        setRFEpcMode(rf, state)

        setContent {
            Page()
        }

        /*userDefinedProducts.clear()
        scannedProducts.clear()
        scannedEpcTable.clear()
        scannedBarcodeTable.clear()
        saveToMemory()
*/
        loadMemory()

        if (numberOfScanned != 0) {
            syncScannedItemsToServer()
        }
    }

    override fun onResume() {
        super.onResume()
        uiList.clear()
        uiList.addAll(manualRefillProducts)
        uiList.sortBy { it1 ->

            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
        }
        barcodeInit()
        saveToMemory()
    }

    override fun onPause() {
        super.onPause()
        stopBarcodeScan()
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

    private fun syncScannedItemsToServer() {

        val epcArray = JSONArray()
        val barcodeArray = JSONArray()

        val alreadySyncedEpcs = mutableListOf<String>()
        val alreadySyncedBarcodes = mutableListOf<String>()
        manualRefillProducts.forEach {
            if (it.scannedEPCNumber > 0) {
                alreadySyncedEpcs.addAll(it.scannedEPCs)
            }
            if (it.scannedBarcodeNumber > 0) {
                alreadySyncedBarcodes.add(it.scannedBarcode)
            }
        }

        scannedEpcTable.forEach {
            if (it !in alreadySyncedEpcs) {
                epcArray.put(it)
            }
        }

        scannedBarcodeTable.forEach {
            if (it !in alreadySyncedBarcodes) {
                barcodeArray.put(it)
            } else {
                val productIndex = manualRefillProducts.indexOf(manualRefillProducts.last { it1 ->
                    it1.scannedBarcode == it
                })
                manualRefillProducts[productIndex].scannedBarcodeNumber =
                    scannedBarcodeTable.count { it1 ->
                        it1 == it
                    }
            }
        }

        if (epcArray.length() == 0 && barcodeArray.length() == 0) {
            uiList.clear()
            uiList.addAll(manualRefillProducts)
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false
            return
        }

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/products/v4"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")

            for (i in 0 until epcs.length()) {
                val refillProduct = ManualRefillProduct(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedEPCNumber = 1,
                    productCode = epcs.getJSONObject(i).getString("K_Bar_Code"),
                    size = epcs.getJSONObject(i).getString("Size"),
                    color = epcs.getJSONObject(i).getString("Color"),
                    originalPrice = epcs.getJSONObject(i).getString("OrgPrice"),
                    salePrice = epcs.getJSONObject(i).getString("SalePrice"),
                    rfidKey = epcs.getJSONObject(i).getLong("RFID"),
                    wareHouseNumber = epcs.getJSONObject(i).getInt("depoCount"),
                    kName = epcs.getJSONObject(i).getString("K_Name"),
                    scannedBarcode = "",
                    scannedEPCs = mutableListOf(epcs.getJSONObject(i).getString("epc")),
                    scannedBarcodeNumber = 0,
                    requestedNum = 0,
                    storeNumber = 0,
                )

                var isInRefillProductList = false
                manualRefillProducts.forEach { it1 ->
                    if (it1.KBarCode == refillProduct.KBarCode) {
                        it1.scannedEPCs.add(refillProduct.scannedEPCs[0])
                        it1.scannedEPCNumber += 1
                        isInRefillProductList = true
                        return@forEach
                    }
                }
                if (!isInRefillProductList) {
                    manualRefillProducts.add(refillProduct)
                }
            }

            for (i in 0 until barcodes.length()) {
                val refillProduct = ManualRefillProduct(
                    name = barcodes.getJSONObject(i).getString("productName"),
                    KBarCode = barcodes.getJSONObject(i).getString("KBarCode"),
                    imageUrl = barcodes.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = barcodes.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedBarcodeNumber = 1,
                    productCode = barcodes.getJSONObject(i).getString("K_Bar_Code"),
                    size = barcodes.getJSONObject(i).getString("Size"),
                    color = barcodes.getJSONObject(i).getString("Color"),
                    originalPrice = barcodes.getJSONObject(i).getString("OrgPrice"),
                    salePrice = barcodes.getJSONObject(i).getString("SalePrice"),
                    rfidKey = barcodes.getJSONObject(i).getLong("RFID"),
                    wareHouseNumber = barcodes.getJSONObject(i).getInt("depoCount"),
                    scannedBarcode = barcodes.getJSONObject(i).getString("kbarcode"),
                    scannedEPCs = mutableListOf(),
                    kName = barcodes.getJSONObject(i).getString("K_Name"),
                    scannedEPCNumber = 0,
                    requestedNum = 0,
                    storeNumber = 0,
                )
                var isInRefillProductList = false
                manualRefillProducts.forEach { it1 ->
                    if (it1.KBarCode == refillProduct.KBarCode) {
                        it1.scannedBarcode = refillProduct.scannedBarcode
                        it1.scannedBarcodeNumber += 1
                        isInRefillProductList = true
                        return@forEach
                    }
                }
                if (!isInRefillProductList) {
                    manualRefillProducts.add(refillProduct)
                }
            }

            uiList.clear()
            uiList.addAll(manualRefillProducts)
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
            uiList.addAll(manualRefillProducts)
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
            isDataLoading = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {
                val json = JSONObject()
                json.put("epcs", epcArray)
                json.put("KBarCodes", barcodeArray)
                return json.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        val queue = Volley.newRequestQueue(this@ManualRefillActivity)
        queue.add(request)
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

        edit.putString(
            "ManualRefillWarehouseManagerEPCTable",
            JSONArray(scannedEpcTable).toString()
        )
        edit.putString(
            "ManualRefillWarehouseManagerBarcodeTable",
            JSONArray(scannedBarcodeTable).toString()
        )

        edit.putString(
            "ManualRefillProducts",
            Gson().toJson(manualRefillProducts).toString()
        )

        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        val type = object : TypeToken<List<ManualRefillProduct>>() {}.type

        scannedEpcTable = Gson().fromJson(
            memory.getString("ManualRefillWarehouseManagerEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("ManualRefillWarehouseManagerBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        manualRefillProducts = Gson().fromJson(
            memory.getString("ManualRefillProducts", ""),
            type
        ) ?: ArrayList()

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
    }

    fun clear() {

        val removedRefillProducts = mutableListOf<ManualRefillProduct>()

        manualRefillProducts.forEach {
            if (it.KBarCode in signedProductCodes) {

                scannedEpcTable.removeAll(it.scannedEPCs)
                scannedBarcodeTable.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                removedRefillProducts.add(it)
            }
        }
        manualRefillProducts.removeAll(removedRefillProducts)
        removedRefillProducts.clear()

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
        uiList.clear()
        uiList.addAll(manualRefillProducts)
        uiList.sortBy { it1 ->
            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
        }
        selectMode = false
        signedProductCodes = mutableListOf()
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

        if (selectMode) {
            signedProductCodes = mutableListOf()
            selectMode = false
            uiList.clear()
            uiList.addAll(manualRefillProducts)
            uiList.sortBy { it1 ->
                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
            }
        } else {
            saveToMemory()
            stopRFScan()
            stopBarcodeScan()
            finish()
        }
    }

    private fun openSendToStoreActivity() {

        Intent(this, SendManualRefillToStoreActivity::class.java).also {
            it.putExtra("ManualRefillProducts", Gson().toJson(manualRefillProducts.filter { it1 ->
                it1.scannedBarcodeNumber + it1.scannedEPCNumber > 0
            }).toString())
            it.putExtra("ManualRefillValidScannedProductsNumber", numberOfScanned)
            startActivity(it)
        }
    }

    private fun openSearchActivity(product: ManualRefillProduct) {

        val searchResultProduct = SearchResultProducts(
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
            shoppingNumber = 0,
            warehouseNumber = 0
        )

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(searchResultProduct).toString())
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
                    bottomBar = { if (selectMode) SelectedBottomAppBar() else BottomBar() },
                    content = { Content() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun SelectedBottomAppBar() {
        BottomAppBar(
            backgroundColor = colorResource(id = R.color.JeanswestBottomBar),
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
                        uiList.forEach {
                            signedProductCodes.add(it.KBarCode)
                        }
                        uiList.clear()
                        uiList.addAll(manualRefillProducts)
                        uiList.sortBy { it1 ->
                            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                        }
                    }) {
                        Text(text = "انتخاب همه")
                    }
                    Button(onClick = {
                        openClearDialog = true
                    }) {
                        Text(text = "پاک کردن")
                    }
                    Button(onClick = {
                        signedProductCodes.clear()
                        selectMode = false
                        uiList.clear()
                        uiList.addAll(manualRefillProducts)
                        uiList.sortBy { it1 ->
                            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                        }
                    }) {
                        Text(text = "بازگشت")
                    }
                }
            }
        }
    }

    @Composable
    fun BottomBar() {
        BottomAppBar(
            backgroundColor = colorResource(id = R.color.JeanswestBottomBar),
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
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Text(
                        text = "اسکن شده: $numberOfScanned",
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    )

                    Button(onClick = {
                        Intent(
                            this@ManualRefillActivity,
                            AddProductToManualRefillListActivityActivity::class.java
                        ).apply {
                            startActivity(this)
                        }
                    }) {
                        Text(text = "تعریف شارژ")
                    }

                    Button(onClick = { openSendToStoreActivity() }) {
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

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        var slideValue by rememberSaveable { mutableStateOf(rfPower.toFloat()) }

        Column {

            if (openClearDialog) {
                ClearAlertDialog()
            }

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .background(
                        MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth()
            ) {

                if (scanTypeValue == "RFID") {
                    Row {

                        Text(
                            text = "قدرت آنتن (" + slideValue.toInt() + ")  ",
                            modifier = Modifier
                                .padding(start = 16.dp)
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
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                }
                if (isScanning || isDataLoading) {
                    Row(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colors.primary)

                        if (isScanning) {
                            Text(
                                text = "در حال اسکن",
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                        if (isDataLoading) {
                            Text(
                                text = "در حال بارگذاری",
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 2.dp, bottom = 56.dp)) {

                items(uiList.size) { i ->
                    LazyColumnItem(i)
                }
            }
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun LazyColumnItem(i: Int) {

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .background(
                    color = if (uiList[i].scannedBarcodeNumber + uiList[i].scannedEPCNumber < uiList[i].requestedNum) {
                        MaterialTheme.colors.onPrimary
                    } else {
                        MaterialTheme.colors.onSecondary
                    },
                    shape = MaterialTheme.shapes.small
                )

                .fillMaxWidth()
                .height(80.dp)
                .combinedClickable(
                    onClick = {
                        if (!selectMode) {
                            openSearchActivity(uiList[i])
                        } else {
                            if (uiList[i].KBarCode !in signedProductCodes) {
                                signedProductCodes.add(uiList[i].KBarCode)
                            } else {
                                signedProductCodes.remove(uiList[i].KBarCode)
                                if (signedProductCodes.size == 0) {
                                    selectMode = false
                                }
                            }
                            uiList.clear()
                            uiList.addAll(manualRefillProducts)
                            uiList.sortBy { it1 ->
                                it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                            }
                        }
                    },
                    onLongClick = {
                        selectMode = true
                        if (uiList[i].KBarCode !in signedProductCodes) {
                            signedProductCodes.add(uiList[i].KBarCode)
                        } else {
                            signedProductCodes.remove(uiList[i].KBarCode)
                            if (signedProductCodes.size == 0) {
                                selectMode = false
                            }
                        }
                        uiList.clear()
                        uiList.addAll(manualRefillProducts)
                        uiList.sortBy { it1 ->
                            it1.scannedEPCNumber + it1.scannedBarcodeNumber > 0
                        }
                    },
                )
                .testTag("refillItems"),
        ) {

            if (uiList[i].KBarCode in signedProductCodes) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_check_circle_24),
                    tint = doneColor,
                    contentDescription = "",
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterVertically)
                )
            }

            Image(
                painter = rememberImagePainter(
                    uiList[i].imageUrl,
                ),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .padding(horizontal = 8.dp)
            )

            Row(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.5F)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = uiList[i].name,
                        style = MaterialTheme.typography.h1,
                        textAlign = TextAlign.Right,
                    )

                    Text(
                        text = uiList[i].KBarCode,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1F)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "درخواستی: " + uiList[i].requestedNum,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                    Text(
                        text = "اسکن شده: " + (uiList[i].scannedEPCNumber + uiList[i].scannedBarcodeNumber).toString(),
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                        color = if (uiList[i].scannedBarcodeNumber + uiList[i].scannedEPCNumber >= uiList[i].requestedNum) {
                            doneColorDarkerShade
                        } else {
                            MaterialTheme.colors.onBackground
                        }
                    )
                }
            }
        }
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
                        text = "کالاهای انتخاب شده پاک شوند؟",
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

    @Composable
    fun ScanTypeDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = true }
                    .testTag("scanTypeDropDownList")) {
                Text(text = scanTypeValue)
                Icon(
                    painter = if (!expanded) {
                        painterResource(id = R.drawable.ic_baseline_arrow_drop_down_24)
                    } else {
                        painterResource(id = R.drawable.ic_baseline_arrow_drop_up_24)
                    }, ""
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                scanTypeValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        scanTypeValue = it
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }
}