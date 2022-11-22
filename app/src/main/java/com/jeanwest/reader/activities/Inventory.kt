package com.jeanwest.reader.activities

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
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.RequestQueue
import com.android.volley.TimeoutError
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.R
import com.jeanwest.reader.management.*
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.test.RFIDWithUHFUART
import com.jeanwest.reader.ui.*
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream


@ExperimentalFoundationApi
class Inventory : ComponentActivity() {

    private var saveToServerId = ""
    private lateinit var rf: RFIDWithUHFUART
    private var rfPower = 30
    private var epcTablePreviousSize = 0
    private var inputBarcodes = mutableListOf<String>()
    val inputProducts = mutableMapOf<String, Product>()
    private val scannedProducts = mutableMapOf<String, Product>()
    private var scanningJob: Job? = null
    private var scannedEpcMapWithProperties = mutableMapOf<String, Product>()
    private var warehouseCode by mutableStateOf("")
    private var locations = mutableMapOf<String, String>()
    private var inputBarcodeMapWithProperties = mutableMapOf<String, Product>()
    private var currentScannedProductProductCodes = mutableListOf<String>()
    private var currentScannedEpcs = mutableListOf<String>()
    private var isInProgress = false
    private var resultsSentToServer = mutableStateMapOf<String, Product>()
    private var lastInventoryTime = 0L
    //ui parameters
    private var openFinishDialog by mutableStateOf(false)
    var uiList = mutableStateListOf<Product>()
    var inventoryResult = mutableStateMapOf<String, Product>()
    var loading by mutableStateOf(false)
    private var rfidScan by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    private var number by mutableStateOf(0)
    private var fileName by mutableStateOf("خروجی")
    private var openDialog by mutableStateOf(false)
    private var openClearDialog by mutableStateOf(false)
    private var openStartOrContinueDialog by mutableStateOf(false)
    private var state = SnackbarHostState()
    private var inventoryProgress by mutableStateOf(0F)
    var inventoryStarted by mutableStateOf(false)
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var numberOfScanned by mutableStateOf(0)
    private var isInShortageAdditionalPage by mutableStateOf(false)
    private lateinit var queue: RequestQueue
    private var signedKBarCode = mutableStateListOf<String>()
    private var scanFilter by mutableStateOf("کسری")
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    companion object {
        var scannedEpcs = mutableListOf<String>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        if (inventoryStarted) {
            getWarehouseBarcodes()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!loading) {
            number = scannedEpcs.size
            syncScannedItemsToServer()
        }
    }

    private fun getWarehouseBarcodes() {

        loading = true

        getWarehouseProducts(queue, state, warehouseCode, { isInProgress, barcodes ->
            this.isInProgress = isInProgress
            inputBarcodes.clear()
            inputBarcodeMapWithProperties.clear()
            inputProducts.clear()
            inputBarcodes.addAll(barcodes)
            syncInputItemsToServer()
        }, {
            loading = false
        })
    }

    private fun calculateConflicts() {

        loading = true

        val conflicts = mutableStateMapOf<String, Product>()

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
            conflicts[it.key] = product
        }

        inventoryResult.clear()
        inventoryResult.putAll(conflicts)

        inventoryProgress = if (inputProducts.isEmpty()) {
            0F
        } else {
            (inventoryResult.filter {
                it.value.inventoryConflictType == "تایید شده"
            }.size.toFloat()) / inventoryResult.size.toFloat()
        }

        var numberOfProducts = 0
        inputProducts.forEach {
            numberOfProducts += it.value.storeNumber
        }

        shortagesNumber = 0
        additionalNumber = 0
        numberOfScanned = 0

        inventoryResult.values.toMutableStateList().forEach {
            if (it.inventoryConflictType == "کسری") {
                shortagesNumber += it.inventoryConflictAbs
            } else if (it.inventoryConflictType == "اضافی") {
                additionalNumber += it.inventoryConflictAbs
            }
            numberOfScanned += it.scannedNumber
        }

        val shortageAndAdditional = inventoryResult.filter { it1 ->
            it1.value.inventoryConflictType == "کسری" || it1.value.inventoryConflictType == "اضافی"
        }.toMutableMap()

        val uiListTemp = mutableListOf<Product>()
        uiListTemp.addAll(shortageAndAdditional.values.toMutableStateList())

        uiListTemp.sortBy {
            it.productCode
        }

        uiListTemp.sortBy {
            it.productCode !in currentScannedProductProductCodes
        }

        uiListTemp.sortBy {
            it.name
        }

        signedKBarCode.removeAll {
            uiListTemp.indexOfLast { it1 ->
                it1.KBarCode == it
            } == -1
        }

        uiList.clear()
        uiList.addAll(uiListTemp)
        filterUiList()

        loading = false
        saveToMemory()
    }

    private fun filterUiList() {

        val uiListParameters = uiList.filter {
            it.inventoryConflictType == scanFilter
        } as MutableList<Product>

        uiList.clear()
        uiList.addAll(uiListParameters)
    }

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("conflicts")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("کد جست و جو")
        headerRow.createCell(1).setCellValue("مغایرت")

        val isInDepo = locations[warehouseCode]?.contains("دپو") ?: false

        inventoryResult.values.forEach {

            if (it.inventoryConflictType != "تایید شده") {

                val diff = if (isInDepo) it.countedWarehouseNumber else it.countedStoreNumber

                val row = sheet.createRow(sheet.physicalNumberOfRows)
                row.createCell(0).setCellValue(it.KBarCode)
                row.createCell(1).setCellValue(diff.toDouble())
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

                if (inventoryStarted) {

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
                if (isInShortageAdditionalPage) {
                    isInShortageAdditionalPage = false
                } else {
                    back()
                }
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

        if (rfPower < 10) {
            rfPower = 30
        }
    }

    private suspend fun startRFScan() {

        scanning = true
        rfidScan = true
        if (!setRFPower(state, rf, rfPower)) {
            rfidScan = false
            return
        }

        if (rfPower < 10) {
            currentScannedEpcs.clear()
        }

        rf.startInventoryTag(0, 0, 0)

        while (rfidScan) {

            var uhfTagInfo: UHFTAGInfo?
            while (true) {
                uhfTagInfo = rf.readTagFromBuffer()
                if (uhfTagInfo != null) {
                    if (uhfTagInfo.epc.startsWith("30")) {
                        scannedEpcs.add(uhfTagInfo.epc)
                        if (rfPower < 10) {
                            currentScannedEpcs.add(uhfTagInfo.epc)
                        }
                    }
                } else {
                    break
                }
            }

            scannedEpcs = scannedEpcs.distinct().toMutableList()

            number = scannedEpcs.size

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
        number = scannedEpcs.size
        saveToMemory()
        scanning = false
    }

    private fun syncInputItemsToServer() {

        val barcodeTableForV4 = mutableListOf<String>()

        loading = true
        var inputProductsBiggerThan1000 = false

        run breakForEach@{
            inputBarcodes.forEach {
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
            syncScannedItemsToServer()
            loading = false
            return
        }

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
            loading = false
        })
    }

    private fun makeInputProductMap() {

        loading = true

        val isInDepo = locations[warehouseCode]?.contains("دپو") ?: false

        inputBarcodes.distinct().forEach {

            val product = inputBarcodeMapWithProperties[it]!!
            product.inventoryOnDepo = isInDepo

            if ((product.brandName == "JeansWest" || product.brandName == "JootiJeans" || product.brandName == "Baleno")
                && !product.name.contains("جوراب")
                && !product.name.contains("عينك")
                && !product.name.contains("شاپينگ")
            ) {

                if (product.KBarCode !in inputProducts.keys) {
                    inputProducts[product.KBarCode] = product.copy()

                    if (!isInProgress) {
                        if (isInDepo) {
                            inputProducts[product.KBarCode]!!.countedWarehouseNumber =
                                -1 * inputProducts[product.KBarCode]!!.wareHouseNumber
                        } else {
                            inputProducts[product.KBarCode]!!.countedStoreNumber =
                                -1 * inputProducts[product.KBarCode]!!.storeNumber
                        }
                    }
                }
            }
        }

        loading = false
    }

    private fun syncScannedItemsToServer() {

        loading = true

        if (number == 0) {
            calculateConflicts()
            loading = false
            return
        }

        val epcTableForV4 = mutableListOf<String>()
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

        if (epcTableForV4.size == 0) {

            calculateConflicts()
            loading = false
            return
        }

        getProductsV4(queue, state, epcTableForV4, mutableListOf(), { epcs, _, invalidEpcs, _ ->

            epcs.forEach { product ->
                scannedEpcMapWithProperties[product.scannedEPCs[0]] = product
            }

            for (i in 0 until invalidEpcs.length()) {
                scannedEpcs.remove(invalidEpcs[i])
            }
            number = scannedEpcs.size

            if (scannedProductsBiggerThan1000) {
                syncScannedItemsToServer()
            } else {
                makeScannedProductMap()
                calculateConflicts()
                loading = false
            }

        }, {
            calculateConflicts()
            loading = false
        })
    }

    private fun makeScannedProductMap() {

        val isInDepo = locations[warehouseCode]?.contains("دپو") ?: false

        loading = true

        scannedProducts.clear()
        currentScannedProductProductCodes.clear()

        scannedEpcMapWithProperties.forEach { it1 ->

            currentScannedEpcs.forEach {
                if (it == it1.key) {
                    currentScannedProductProductCodes.add(it1.value.productCode)
                }
            }
        }

        scannedEpcs.forEach {

            val product = scannedEpcMapWithProperties[it]!!
            product.inventoryOnDepo = isInDepo

            if ((product.brandName == "JeansWest" || product.brandName == "JootiJeans" || product.brandName == "Baleno")
                && !product.name.contains("جوراب")
                && !product.name.contains("عينك")
                && !product.name.contains("شاپينگ")
            ) {

                if (product.KBarCode in scannedProducts.keys) {
                    if (product.scannedEPCs[0] !in scannedProducts[product.KBarCode]!!.scannedEPCs) {
                        scannedProducts[product.KBarCode]!!.scannedEPCs.add(product.scannedEPCs[0])
                    }
                } else {
                    scannedProducts[product.KBarCode] =
                        product.copy(scannedEPCs = mutableListOf(it))
                    if (!isInProgress) {
                        if (isInDepo) {
                            scannedProducts[product.KBarCode]!!.countedWarehouseNumber =
                                -1 * scannedProducts[product.KBarCode]!!.wareHouseNumber
                        } else {
                            scannedProducts[product.KBarCode]!!.countedStoreNumber =
                                -1 * scannedProducts[product.KBarCode]!!.storeNumber
                        }
                    }
                }
            }
        }
        loading = false
    }

    private fun saveResultsToServer() {
        loading = true

        saveInventoryDataGetId(queue, state, warehouseCode, {
            saveToServerId = it
            saveApi2()
        }, {
            loading = false
        })
    }

    private fun saveApi2() {

        loading = true
        var resultsBiggerThan500 = false
        val productListForSend = mutableListOf<Product>()

        run breakForEach@{
            inventoryResult.keys.forEach {
                if (it !in resultsSentToServer.keys) {
                    if (productListForSend.size < 500) {
                        productListForSend.add(inventoryResult[it]!!)
                        resultsSentToServer[it] = inventoryResult[it]!!.copy()
                    } else {
                        resultsBiggerThan500 = true
                        return@breakForEach
                    }
                }
            }
        }

        if (productListForSend.isEmpty()) {
            saveApi3()
            return
        }

        saveInventoryDataSendPackets(queue, state, productListForSend, {
            if (resultsBiggerThan500) {
                saveApi2()
            } else {
                saveApi3()
            }
        }, {
            loading = false
        })
    }

    private fun saveApi3() {

        loading = true

        saveInventoryDataConfirm(queue, state, saveToServerId, {
            inventoryStarted = false
            inputBarcodes.clear()
            inputProducts.clear()
            inputBarcodeMapWithProperties.clear()
            clear()
            lastInventoryTime = System.currentTimeMillis()
            saveToMemory()
            loading = false
        }, {
            if (it is TimeoutError) {
                inventoryStarted = false
                inputBarcodes.clear()
                inputProducts.clear()
                inputBarcodeMapWithProperties.clear()
                clear()
                lastInventoryTime = System.currentTimeMillis()
                saveToMemory()
            }
            loading = false
        })
    }

    private fun checkServerIsReady() {

        if(System.currentTimeMillis() > lastInventoryTime + 300000) {
            openStartOrContinueDialog = true
        } else {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "سرور مشغول ثبت اطلاعات انبارگردانی قبلی است. لطفا بعدا امتحان کنید.",
                    null,
                    SnackbarDuration.Long
                )
            }
        }
    }

    fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("InventoryEPCTable", JSONArray(scannedEpcs).toString())
        edit.putString("InventorySignedProductCodes", JSONArray(signedKBarCode).toString())
        edit.putString("warehouseCodeForInventory", warehouseCode)
        edit.putBoolean("inventoryStarted", inventoryStarted)
        edit.putLong("lastInventoryTime", lastInventoryTime)
        edit.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        locations = Gson().fromJson(
            memory.getString("userWarehouses", ""),
            locations.javaClass
        ) ?: mutableMapOf()
        warehouseCode = memory.getString("warehouseCodeForInventory", "") ?: ""
        inventoryStarted = memory.getBoolean("inventoryStarted", false)

        if (!inventoryStarted || warehouseCode !in locations.keys) {
            if (locations.keys.isEmpty()) {
                CoroutineScope(Main).launch {
                    state.showSnackbar(
                        "لطفا دوباره وارد حساب کاربری خود شوید",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {
                warehouseCode = locations.keys.toMutableList()[0]
            }
        }

        scannedEpcs = Gson().fromJson(
            memory.getString("InventoryEPCTable", ""),
            scannedEpcs.javaClass
        ) ?: mutableListOf()

        signedKBarCode = Gson().fromJson(
            memory.getString("InventorySignedProductCodes", ""),
            signedKBarCode.javaClass
        ) ?: mutableStateListOf()

        lastInventoryTime = memory.getLong("lastInventoryTime", 0L)

        epcTablePreviousSize = scannedEpcs.size

        number = scannedEpcs.size
    }

    private fun finishPackage() {

        loading = true

        finishInventoryPackage(queue, state, warehouseCode, {
            signedKBarCode.clear()
            saveToMemory()
            loading = false

            inventoryStarted = true
            getWarehouseBarcodes()
        }, {
            loading = false
        })
    }

    private fun clear() {

        scannedEpcs.clear()
        epcTablePreviousSize = 0
        number = 0
        scannedProducts.clear()
        scannedEpcMapWithProperties.clear()
        inventoryResult.clear()
        calculateConflicts()
        saveToMemory()
        openDialog = false
    }

    private fun back() {

        saveToMemory()
        stopRFScan()
        queue.stop()
        beep.release()
        loading = false
        loading = false
        isInProgress = false
        rfidScan = false
        loading = false
        finish()
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @OptIn(ExperimentalCoilApi::class)
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { if (isInShortageAdditionalPage) ListAppBar() else AppBar() },
                    content = { if (isInShortageAdditionalPage) ListContent() else Content() },
                    bottomBar = { if (isInShortageAdditionalPage) ListBottomAppBar() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun AppBar() {

        TopAppBar(

            navigationIcon = {
                IconButton(modifier = Modifier.testTag("back"), onClick = { back() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = ""
                    )
                }
            },

            actions = {
                IconButton(onClick = {
                    if (!rfidScan && !loading) {
                        openDialog = true
                    }
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_share_24),
                        contentDescription = ""
                    )
                }
                IconButton(modifier = Modifier.testTag("clear"),
                    onClick = {
                        if (!rfidScan && !loading) {
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
                    text = stringResource(id = R.string.inventoryText),
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

        Box(
            modifier = Modifier
                .padding(bottom = 150.dp)
                .fillMaxSize()
        ) {

            if (rfidScan || loading) {
                LoadingCircularProgressIndicator(rfidScan, loading)
            }

            if (!rfidScan && !loading) {


                Box(
                    modifier = Modifier
                        .shadow(1.dp, shape = Shapes.small)
                        .size(200.dp)
                        .align(
                            Alignment.Center
                        )
                        .background(color = Color.White, shape = Shapes.medium),
                ) {

                    CircularProgressIndicator(
                        inventoryProgress,
                        modifier = Modifier
                            .size(150.dp)
                            .align(
                                Alignment.Center
                            ),
                        Jeanswest,
                        8.dp
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "${inventoryProgress * 100}%", modifier = Modifier
                            .fillMaxWidth()
                            .align(
                                Alignment.Center
                            ),
                        textAlign = TextAlign.Center,
                        style = Typography.h5
                    )
                }
            }
        }

        if (!loading && !loading && !rfidScan && !loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {

                if (openDialog) {
                    FileAlertDialog()
                }
                if (openClearDialog) {
                    ClearAlertDialog()
                }
                if (openFinishDialog) {
                    FinishAlertDialog()
                }
                if (openStartOrContinueDialog) {
                    StartOrContinueAlertDialog()
                }

                Column(
                    modifier = Modifier
                        .shadow(1.dp)
                        .fillMaxWidth()
                        .background(color = Color.White)
                ) {

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
                    ) {

                        FilterDropDownList(
                            modifier = Modifier.wrapContentWidth(),
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_location_city_24),
                                    contentDescription = "",
                                    tint = iconColor,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 4.dp)
                                )
                            },
                            text = {
                                Text(
                                    style = MaterialTheme.typography.body2,
                                    text = locations[warehouseCode] ?: "",
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 4.dp)
                                )
                            },
                            values = locations.values.toMutableList(),
                            onClick = {
                                if (inventoryStarted) {
                                    CoroutineScope(Main).launch {
                                        state.showSnackbar(
                                            "در هنگام انبارگردانی امکان تغییر مکان وجود ندارد. بعد از پایان انبارگردانی فعلی، می توانید مکان جدیدی را برای انبارگردانی انتخاب کنید",
                                            null,
                                            SnackbarDuration.Long
                                        )
                                    }
                                } else {
                                    warehouseCode = locations.filter { it1 ->
                                        it1.value == it
                                    }.keys.toMutableList()[0]
                                    saveToMemory()
                                }
                            }
                        )

                        Button(modifier = Modifier
                            .padding(start = 24.dp)
                            .fillMaxWidth()
                            .height(48.dp),
                            onClick = {
                                isInShortageAdditionalPage = true
                            }) {
                            Text(text = "مغایرت ها", style = Typography.h2)
                        }
                    }
                    Button(modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                        .fillMaxWidth()
                        .height(48.dp),
                        onClick = {
                            if (!inventoryStarted) {
                                checkServerIsReady()
                            } else {
                                openFinishDialog = true
                            }
                        }) {
                        Text(
                            text = if (inventoryStarted) "پایان انبارگردانی" else "شروع انبارگردانی",
                            style = Typography.h2
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ListBottomAppBar() {
        BottomAppBar(backgroundColor = MaterialTheme.colors.background) {

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "کسری: " + inventoryResult.values.toMutableStateList().filter {
                        it.inventoryConflictType == "کسری"
                    }.size,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )

                Text(
                    text = "اضافی: " + inventoryResult.values.toMutableStateList().filter {
                        it.inventoryConflictType == "اضافی"
                    }.size,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )
                ScanFilterDropDownList()
            }
        }
    }

    @Composable
    fun ListAppBar() {

        TopAppBar(

            navigationIcon = {
                IconButton(
                    modifier = Modifier.testTag("back"),
                    onClick = { isInShortageAdditionalPage = false }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.inventoryText),
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
    fun ListContent() {

        Column(Modifier.fillMaxSize()) {

            if (this@Inventory.loading || this@Inventory.loading || rfidScan) {
                LoadingCircularProgressIndicator(
                    rfidScan,
                    this@Inventory.loading || this@Inventory.loading
                )
            } else {

                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .align(Alignment.CenterHorizontally),
                    onClick = {

                        if (inventoryStarted) {
                            stopRFScan()
                            rfPower = 5
                            scanningJob = CoroutineScope(IO).launch {
                                startRFScan()
                            }
                        }
                    }) {
                    Text(text = "تعریف ناحیه جستجو")
                }

                LazyColumn(modifier = Modifier.padding(top = 8.dp, bottom = 56.dp)) {

                    items(uiList.size) { i ->

                        Item(
                            i,
                            uiList,
                            text3 = "موجودی: " + uiList[i].inventoryNumber,
                            text4 = uiList[i].inventoryConflictType + ":" + " " + uiList[i].inventoryConflictAbs,
                            clickable = true,
                            onClick = {
                                Intent(
                                    this@Inventory,
                                    InventorySearch::class.java
                                ).apply {
                                    this.putExtra(
                                        "product",
                                        Gson().toJson(uiList[i]).toString()
                                    )
                                    startActivity(this)
                                }
                            },
                            colorFull = uiList[i].KBarCode in signedKBarCode,
                            onLongClick = {
                                if (uiList[i].KBarCode !in signedKBarCode) {
                                    signedKBarCode.add(uiList[i].KBarCode)
                                } else {
                                    signedKBarCode.remove(uiList[i].KBarCode)
                                }
                                saveToMemory()
                            }
                        )
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
                        text = "کالاهای اسکن شده پاک شوند؟",
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
    fun FinishAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openFinishDialog = false
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
                        text = "کالاهای اسکن شده ثبت شوند؟",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 22.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openFinishDialog = false
                            saveResultsToServer()

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = {
                                openFinishDialog = false
                                inventoryStarted = false
                                this@Inventory.loading = false
                                this@Inventory.loading = false
                                inputBarcodes.clear()
                                inputProducts.clear()
                                inputBarcodeMapWithProperties.clear()
                                clear()
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "خیر، نتایج پاک شوند")
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun StartOrContinueAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openStartOrContinueDialog = false
            },
            buttons = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Button(onClick = {
                        openStartOrContinueDialog = false
                        inventoryStarted = true
                        clear()
                        getWarehouseBarcodes()

                    }, modifier = Modifier.padding(top = 10.dp)) {
                        Text(text = "ادامه انبارگردانی قبلی")
                    }
                    Button(
                        onClick = {
                            openStartOrContinueDialog = false
                            finishPackage()
                        },
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(text = "شروع انبارگردانی جدید")
                    }
                }
            }
        )
    }

    @Composable
    fun ScanFilterDropDownList() {

        val scanValues =
            mutableListOf("اضافی", "کسری")

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier
                .clickable { expanded = true }
                .testTag("scanFilterDropDownList")
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
                        calculateConflicts()
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }
}
