package com.jeanwest.reader.checkOut

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
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONArray
import org.json.JSONObject

@ExperimentalCoilApi
class CheckOutActivity : ComponentActivity(), IBarcodeResult {

    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 5
    private var scannedEpcTable = mutableListOf<String>()
    private var epcTablePreviousSize = 0
    private var scannedBarcodeTable = mutableListOf<String>()
    private val barcode2D = Barcode2D(this)
    val scannedProducts = ArrayList<CheckOutProduct>()
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
    var scanTypeValue by mutableStateOf("بارکد")
    private var state = SnackbarHostState()
    private var selectMode by mutableStateOf(false)
    var uiList = mutableStateListOf<CheckOutProduct>()

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
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

        if (intent.getStringExtra("productEPCs") ?: "" == "") {
            loadMemory()
        } else {
            scannedEpcTable = Gson().fromJson(
                intent.getStringExtra("productEPCs") ?: "",
                scannedEpcTable.javaClass
            ) ?: mutableListOf()
            numberOfScanned = scannedEpcTable.size
            saveToMemory()
        }

        if (numberOfScanned != 0) {
            syncScannedItemsToServer()
        }
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

        isDataLoading = true

        val url = "https://rfid-api.avakatan.ir/products/v4"

        val request = object : JsonObjectRequest(Method.POST, url, null, {

            val epcs = it.getJSONArray("epcs")
            val barcodes = it.getJSONArray("KBarCodes")

            scannedEpcTable.clear()
            scannedProducts.clear()
            for (i in 0 until epcs.length()) {
                val checkOutProduct = CheckOutProduct(
                    name = epcs.getJSONObject(i).getString("productName"),
                    KBarCode = epcs.getJSONObject(i).getString("KBarCode"),
                    imageUrl = epcs.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = epcs.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedNumber = 1,
                    productCode = epcs.getJSONObject(i).getString("K_Bar_Code"),
                    size = epcs.getJSONObject(i).getString("Size"),
                    color = epcs.getJSONObject(i).getString("Color"),
                    originalPrice = epcs.getJSONObject(i).getString("OrgPrice"),
                    salePrice = epcs.getJSONObject(i).getString("SalePrice"),
                    rfidKey = epcs.getJSONObject(i).getLong("RFID"),
                    wareHouseNumber = epcs.getJSONObject(i).getInt("depoCount"),
                    scannedBarcode = "",
                    scannedEPCs = mutableListOf(),
                    kName = epcs.getJSONObject(i).getString("K_Name"),
                )

                checkOutProduct.scannedEPCs.add(epcs.getJSONObject(i).getString("epc"))
                scannedEpcTable.add(epcs.getJSONObject(i).getString("epc"))

                var isInRefillProductList = false
                scannedProducts.forEach { it1 ->
                    if (it1.KBarCode == checkOutProduct.KBarCode) {
                        it1.scannedEPCs.add(checkOutProduct.scannedEPCs[0])
                        it1.scannedNumber += 1
                        isInRefillProductList = true
                        return@forEach
                    }
                }
                if (!isInRefillProductList) {
                    scannedProducts.add(checkOutProduct)
                }
            }

            scannedBarcodeTable.clear()
            for (i in 0 until barcodes.length()) {
                val checkOutProduct = CheckOutProduct(
                    name = barcodes.getJSONObject(i).getString("productName"),
                    KBarCode = barcodes.getJSONObject(i).getString("KBarCode"),
                    imageUrl = barcodes.getJSONObject(i).getString("ImgUrl"),
                    primaryKey = barcodes.getJSONObject(i).getLong("BarcodeMain_ID"),
                    scannedNumber = 1,
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
                )

                scannedBarcodeTable.add(barcodes.getJSONObject(i).getString("kbarcode"))

                var isInCheckOutProductList = false
                scannedProducts.forEach { it1 ->
                    if (it1.KBarCode == checkOutProduct.KBarCode) {
                        it1.scannedBarcode = checkOutProduct.scannedBarcode
                        it1.scannedNumber += 1
                        isInCheckOutProductList = true
                        return@forEach
                    }
                }
                if (!isInCheckOutProductList) {
                    scannedProducts.add(checkOutProduct)
                }
            }

            uiList.clear()
            uiList.addAll(scannedProducts)
            isDataLoading = false

        }, {
            if ((scannedEpcTable.size + scannedBarcodeTable.size) == 0) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "کالایی جهت بررسی وجود ندارد",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {
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
            }
            uiList.clear()
            uiList.addAll(scannedProducts)
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
                val epcArray = JSONArray()

                scannedEpcTable.forEach {
                    epcArray.put(it)
                }

                json.put("epcs", epcArray)

                val barcodeArray = JSONArray()

                scannedBarcodeTable.forEach {
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

        val queue = Volley.newRequestQueue(this)
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
            "CheckOutEPCTable",
            JSONArray(scannedEpcTable).toString()
        )
        edit.putString(
            "CheckOutBarcodeTable",
            JSONArray(scannedBarcodeTable).toString()
        )

        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        scannedEpcTable = Gson().fromJson(
            memory.getString("CheckOutEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        epcTablePreviousSize = scannedEpcTable.size

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("CheckOutBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
    }

    fun clear() {

        val removedRefillProducts = mutableListOf<CheckOutProduct>()

        scannedProducts.forEach {
            if (it.KBarCode in signedProductCodes) {

                scannedEpcTable.removeAll(it.scannedEPCs)
                scannedBarcodeTable.removeAll { it1 ->
                    it1 == it.scannedBarcode
                }
                removedRefillProducts.add(it)
            }
        }
        scannedProducts.removeAll(removedRefillProducts)

        numberOfScanned = scannedEpcTable.size + scannedBarcodeTable.size
        uiList.clear()
        uiList.addAll(scannedProducts)
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
            uiList.addAll(scannedProducts)
        } else {
            saveToMemory()
            stopRFScan()
            stopBarcodeScan()
            finish()
        }
    }

    private fun openSendToStoreActivity() {

        Intent(this, SendToDestinationActivity::class.java).also {
            it.putExtra("CheckOutProducts", Gson().toJson(scannedProducts).toString())
            it.putExtra("CheckOutValidScannedProductsNumber", numberOfScanned)
            startActivity(it)
        }
    }

    private fun openSearchActivity(product: CheckOutProduct) {

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
                        uiList.addAll(scannedProducts)
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
                        uiList.addAll(scannedProducts)
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
                    color = //if (uiList[i].scannedNumber == 0) {
                    MaterialTheme.colors.onPrimary
                    /*} else {
                        MaterialTheme.colors.onSecondary
                    }*/,
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
                            uiList.addAll(scannedProducts)
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
                        uiList.addAll(scannedProducts)
                        uiList.sortBy { it1 ->
                            it1.scannedNumber > 0
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
                        text = "موجودی انبار: " + uiList[i].wareHouseNumber,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                    Text(
                        text = "اسکن شده: " + uiList[i].scannedNumber.toString(),
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
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