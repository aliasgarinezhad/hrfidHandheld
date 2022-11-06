package com.jeanwest.reader.stockDraftList

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.shared.*
import com.jeanwest.reader.shared.theme.MyApplicationTheme
import com.jeanwest.reader.shared.theme.borderColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StockDraftsList : ComponentActivity() {

    private var state = SnackbarHostState()
    private lateinit var queue: RequestQueue
    var loading by mutableStateOf(false)
    private var stockDraftIDs = mutableListOf<Long>()
    var uiList = mutableStateMapOf<Long, DraftProperties>()
    var showProductsMode by mutableStateOf(false)
    private var selectedStockDraft = DraftProperties(number = 0L, numberOfItems = 0)
    private var barcodeMapWithProperties = mutableMapOf<String, Product>()
    private val selectedStockDraftProducts = mutableMapOf<String, Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Page()
        }
        queue = Volley.newRequestQueue(this)
        getDraftIDs()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (event.repeatCount == 0) {
            if (keyCode == 4) {
                back()
            }
        }
        return true
    }


    private fun getDraftIDs() {
        loading = true
        getStockDraftIDs(queue, state, {
            stockDraftIDs.clear()
            stockDraftIDs.addAll(it)
            uiList.clear()
            getStockDraftsDetails()
        }, {
            loading = false
        })
    }

    private fun getStockDraftsDetails() {

        loading = true
        var stockDraftID = 0L

        run breakForEach@{
            stockDraftIDs.forEach {
                if (it !in uiList.keys) {
                    stockDraftID = it
                    return@breakForEach
                }
            }
        }

        if (stockDraftID == 0L) {
            loading = false
            Log.e("ui map", uiList.toString())
            return
        }

        getStockDraftDetails(queue, state, stockDraftID.toString(), {
            uiList[it.number] = it
            getStockDraftsDetails()
        }, {
            loading = false
        })
    }

    private fun syncInputItemsToServer() {

        loading = true

        val barcodeTableForV4 = mutableListOf<String>()
        var inputProductsBiggerThan1000 = false

        run breakForEach@{
            selectedStockDraft.barcodeTable.distinct().forEach {
                if (it !in barcodeMapWithProperties.keys) {
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
            makeInputProductMap()
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
                    barcodeMapWithProperties[product.scannedBarcode] = product
                }

                if (inputProductsBiggerThan1000) {
                    syncInputItemsToServer()
                } else {
                    makeInputProductMap()
                    loading = false
                }

            },
            {
                loading = false
            })
    }

    private fun makeInputProductMap() {

        selectedStockDraftProducts.clear()
        selectedStockDraft.barcodeTable.distinct().forEach {
            val product = barcodeMapWithProperties[it]!!.copy()
            product.draftNumber = selectedStockDraft.barcodeTable.count { it1 ->
                it == it1
            }

            if (product.KBarCode !in selectedStockDraftProducts.keys) {
                selectedStockDraftProducts[product.KBarCode] = product.copy()
            } else {
                selectedStockDraftProducts[product.KBarCode]!!.draftNumber += product.draftNumber
            }
        }
    }

    private fun back() {
        if (showProductsMode) {
            showProductsMode = false
        } else {
            queue.stop()
            finish()
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { if (showProductsMode) Content2() else Content() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun AppBar() {
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 0.dp, end = 50.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.stockDraftsList), textAlign = TextAlign.Center,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { back() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = ""
                    )
                }
            }
        )
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content2() {

        Column {

            if (loading) {

                LoadingCircularProgressIndicator(isDataLoading = loading)

            } else {

                Column {
                    LazyColumn {
                        items(selectedStockDraftProducts.size) { i ->
                            Item(
                                i,
                                selectedStockDraftProducts.values.toMutableList(),
                                text3 = "حواله: " + selectedStockDraftProducts.values.toMutableList()[i].draftNumber,
                                text4 = "انبار: " + selectedStockDraftProducts.values.toMutableList()[i].wareHouseNumber
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Content() {

        if (loading) {
            LoadingCircularProgressIndicator(isDataLoading = loading)
        } else {

            Column(modifier = Modifier.fillMaxSize()) {

                LazyColumn(modifier = Modifier.padding(top = 4.dp)) {

                    items(uiList.size) { i ->
                        Item2(
                            clickable = true,
                            text1 = "حواله: " + uiList.values.toList()[i].number,
                            text2 = "تعداد کالاها: " + uiList.values.toList()[i].numberOfItems,
                            text3 = "تاریخ ثبت: " + uiList.values.toList()[i].date,
                            text4 = "مبدا: " + uiList.values.toList()[i].source,
                            text5 = "شرح: " + "",
                            text6 = "مقصد: " + uiList.values.toList()[i].destination,
                        ) {
                            showProductsMode = true
                            selectedStockDraftProducts.clear()
                            selectedStockDraft = uiList.values.toList()[i]
                            syncInputItemsToServer()
                        }
                    }
                }
            }
        }
    }
}
