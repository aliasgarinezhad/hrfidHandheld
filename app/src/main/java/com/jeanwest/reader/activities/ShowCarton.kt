package com.jeanwest.reader.activities

import android.annotation.SuppressLint
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.R
import com.jeanwest.reader.management.*
import com.jeanwest.reader.data.DraftProperties
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.ui.MyApplicationTheme
import com.jeanwest.reader.ui.borderColor
import kotlinx.coroutines.*

@OptIn(ExperimentalFoundationApi::class)
class ShowCarton : ComponentActivity(),
    IBarcodeResult {

    private val barcode2D = Barcode2D(this)
    val inputProducts = mutableMapOf<String, Product>()
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var inputBarcodeMapWithProperties = mutableMapOf<String, Product>()
    var cartonProperties = DraftProperties(number = 0L, numberOfItems = 0)

    //ui parameters
    var productConflicts = mutableStateListOf<Product>()
    var loading by mutableStateOf(false)
    private var uiList = mutableStateListOf<Product>()
    private var state = SnackbarHostState()
    private lateinit var queue: RequestQueue
    private var cartonNumber by mutableStateOf("")
    var showDetailMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        barcodeInit()
        queue = Volley.newRequestQueue(this)

        setContent {
            Page()
        }
        if (showDetailMode) {
            syncInputItemsToServer()
        }

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {

            if (keyCode == 280 || keyCode == 293) {
                if (!showDetailMode) {
                    startBarcodeScan()
                }
            } else if (keyCode == 4) {
                back()
            }
        }
        return true
    }

    private fun calculateConflicts() {

        val conflicts = mutableListOf<Product>()

        conflicts.addAll(inputProducts.values)
        productConflicts.clear()
        productConflicts.addAll(conflicts)
        filterResult(conflicts)
    }

    private fun filterResult(conflictResult: MutableList<Product>) {

        uiList.clear()
        uiList.addAll(conflictResult)
        uiList.sortBy {
            it.productCode
        }
        uiList.sortBy {
            it.name
        }
    }


    private fun syncInputItemsToServer() {

        loading = true

        val barcodeTableForV4 = mutableListOf<String>()
        var inputProductsBiggerThan1000 = false

        run breakForEach@{
            cartonProperties.barcodeTable.distinct().forEach {
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
            calculateConflicts()
            loading = false
            return
        }

        getProductsV4(
            queue,
            state,
            mutableListOf(),
            barcodeTableForV4,
            { _, barcodes, _, _ ->

                barcodes.forEach { product ->
                    inputBarcodeMapWithProperties[product.scannedBarcode] = product
                }

                if (inputProductsBiggerThan1000) {
                    syncInputItemsToServer()
                } else {
                    makeInputProductMap()
                    calculateConflicts()
                    loading = false
                }
            },
            {
                loading = false
            })
    }

    private fun makeInputProductMap() {

        inputProducts.clear()
        cartonProperties.barcodeTable.distinct().forEach {
            val product = inputBarcodeMapWithProperties[it]!!.copy()
            product.draftNumber = cartonProperties.barcodeTable.count { it1 ->
                it == it1
            }

            if (product.KBarCode !in inputProducts.keys) {
                inputProducts[product.KBarCode] = product.copy()
            } else {
                inputProducts[product.KBarCode]!!.draftNumber += product.draftNumber
            }
        }
    }

    override fun getBarcode(barcode: String?) {

        if (!showDetailMode) {
            if (!barcode.isNullOrBlank()) {
                getCartonDetails(barcode)
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            }
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

        if (showDetailMode) {
            showDetailMode = false
        } else {
            stopBarcodeScan()
            queue.stop()
            beep.release()
            finish()
        }
    }

    private fun openSearchActivity(product: Product) {

        val searchResultProduct = Product(
            name = product.name + " از حواله شماره " + cartonProperties.number,
            KBarCode = product.KBarCode,
            imageUrl = product.imageUrl,
            color = product.color,
            size = product.size,
            productCode = product.productCode,
            rfidKey = product.rfidKey,
            primaryKey = product.primaryKey,
            originalPrice = product.originalPrice,
            salePrice = product.salePrice,
            storeNumber = product.storeNumber,
            wareHouseNumber = product.wareHouseNumber,
        )

        val intent = Intent(this, SearchSpecialProduct::class.java)
        intent.putExtra("product", Gson().toJson(searchResultProduct).toString())
        startActivity(intent)
    }

    private fun getCartonDetails(code: String) {

        loading = true

        if (code.isEmpty()) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "لطفا شماره کارتن را وارد کنید",
                    null,
                    SnackbarDuration.Long
                )
            }
            loading = false
            return
        }

        getStockDraftDetails(queue, state, code, {
            cartonProperties = it
            showDetailMode = true
            syncInputItemsToServer()
        }, {
            loading = false
        })
    }

    private fun printLabel() {

    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @OptIn(ExperimentalCoilApi::class)
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { if (showDetailMode) Content() else Content2() },
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
                    text = stringResource(id = R.string.showCarton),
                    modifier = Modifier
                        .padding(end = 50.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Right,
                )
            }
        )
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        Column {

            if (loading) {
                LoadingCircularProgressIndicator(isDataLoading = loading)
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
                            text = "مجموع: ${cartonProperties.numberOfItems}",
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .align(Alignment.CenterVertically)
                                .weight(1F),
                        )
                        Text(
                            text = "انبار جاری: ${cartonProperties.source}",
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1F),
                        )
                        Text(
                            text = "شرح: ${cartonProperties.specification}",
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1F),
                        )
                    }
                }

                LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {

                    items(uiList.size) { i ->
                        Item(
                            i,
                            uiList,
                            true,
                            text3 = "موجودی: " + uiList[i].draftNumber,
                            text4 = uiList[i].conflictType + ":" + " " + uiList[i].conflictNumber
                        ) {
                            openSearchActivity(uiList[i])
                        }
                    }
                }
            }
        }
        BottomBarButton(text = "پرینت لیبل") {
            printLabel()
        }
    }

    @Composable
    fun Content2() {

        Column(modifier = Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .background(color = Color.White)
                    .border(
                        BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                CustomTextField(
                    modifier = Modifier
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .border(
                            BorderStroke(1.dp, borderColor),
                            shape = MaterialTheme.shapes.small
                        )
                        .fillMaxWidth(),
                    hint = "لطفا شماره کارتن را وارد یا اسکن کنید",
                    onSearch = { getCartonDetails(cartonNumber) },
                    onValueChange = { cartonNumber = it },
                    value = cartonNumber,
                    isError = (cartonNumber.toLongOrNull() == null && cartonNumber != ""),
                    keyboardType = KeyboardType.Number
                )
            }
        }
    }
}
