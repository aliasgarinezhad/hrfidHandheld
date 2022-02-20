package com.jeanwest.reader.manualRefill

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme
import org.json.JSONArray

class ScannedProductsPropertyActivity : ComponentActivity() {

    private var uiList = mutableStateListOf<ManualRefillAllProduct>()
    private var storeFilterValue = 0
    private var productsCode = ""
    private var color = ""
    private var sumOfSingleProductRequestedRefill by mutableStateOf(0)
    private var sumOfScanned by mutableStateOf(0)

    @ExperimentalCoilApi
    override fun onResume() {
        super.onResume()
        setContent {
            Page()
        }

        loadMemory()
        productsCode = intent.getStringExtra("ProductsCode") ?: ""
        color = intent.getStringExtra("ProductsColor") ?: ""
        getWarehouseProductsList()
        calculateSumOfRefillAndScanned()
    }

    private fun calculateSumOfRefillAndScanned() {
        sumOfSingleProductRequestedRefill = 0
        sumOfScanned = 0
        ManualRefillActivity.scannedAndRelatedToScannedProducts.forEach {
            if (it.productCode == productsCode && it.color == color) {
                sumOfSingleProductRequestedRefill += it.refillNumber
                sumOfScanned += it.scannedNumber
            }
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        storeFilterValue = memory.getInt("userLocationCode", 0)
    }

    private fun getWarehouseProductsList() {

        val url1 =
            "http://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&K_Bar_Code=$productsCode"

        val request1 = JsonObjectRequest(url1, { response1 ->

            val products = response1.getJSONArray("products")

            jsonArrayProcess(products)

        }, {
            when (it) {
                is NoConnectionError -> {
                    Toast.makeText(
                        this,
                        "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Toast.makeText(this, it.toString(), Toast.LENGTH_LONG).show()
                }
            }
        })

        val queue = Volley.newRequestQueue(this)
        queue.add(request1)
    }

    private fun jsonArrayProcess(wareHouseProductsJsonArray: JSONArray) {

        uiList.clear()

        for (i in 0 until wareHouseProductsJsonArray.length()) {

            val json = wareHouseProductsJsonArray.getJSONObject(i)

            val uiListItem = ManualRefillAllProduct(
                name = json.getString("productName"),
                KBarCode = json.getString("KBarCode"),
                imageUrl = json.getString("ImgUrl"),
                shoppingNumber = json.getInt("dbCountStore"),
                warehouseNumber = json.getInt("dbCountDepo"),
                productCode = json.getString("K_Bar_Code"),
                size = json.getString("Size"),
                color = json.getString("Color"),
                originalPrice = json.getString("OrigPrice"),
                salePrice = json.getString("SalePrice"),
                primaryKey = json.getLong("BarcodeMain_ID"),
                rfidKey = json.getLong("RFID"),
                scannedNumber = 0,
                refillNumber = 0
            )

            if (uiListItem.color == color && uiListItem.productCode == productsCode) {

                val index = ManualRefillActivity.scannedAndRelatedToScannedProducts.indexOfLast {
                    it.primaryKey == uiListItem.primaryKey
                }
                if (index != -1) {
                    uiListItem.scannedNumber =
                        ManualRefillActivity.scannedAndRelatedToScannedProducts[index].scannedNumber
                    uiListItem.refillNumber =
                        ManualRefillActivity.scannedAndRelatedToScannedProducts[index].refillNumber
                    ManualRefillActivity.scannedAndRelatedToScannedProducts[index].warehouseNumber =
                        uiListItem.warehouseNumber
                } else {
                    ManualRefillActivity.scannedAndRelatedToScannedProducts.add(uiListItem)
                }
                uiList.add(uiListItem)
            }
        }
    }

    @ExperimentalCoilApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() }
                )
            }
        }
    }

    @Composable
    fun AppBar() {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { finish() }) {
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
                        .padding(end = 60.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Right
                )
            },
        )
    }

    @ExperimentalCoilApi
    @Composable
    fun Content() {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Column(
                Modifier
                    .padding(start = 5.dp, end = 5.dp, top = 8.dp)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    ),
            ) {
                if (uiList.size > 0)
                    ColumnItem()
            }

            LazyColumn(
                modifier = Modifier
                    .padding(top = 8.dp),
            ) {
                items(uiList.size) { i ->
                    LazyColumnItem(i)
                }
            }
        }
    }

    @Composable
    fun ColumnItem() {

        val modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .wrapContentWidth()

        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colors.onPrimary,
                    shape = MaterialTheme.shapes.small
                )
                .fillMaxWidth()
                .height(165.dp)
                .padding(5.dp),
            //horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Image(
                painter = rememberImagePainter(data = uiList[0].imageUrl),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(165.dp)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )

            Column {
                Text(
                    text = uiList[0].name,
                    style = MaterialTheme.typography.h1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "کد: " + uiList[0].productCode,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "رنگ: " + uiList[0].color,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "موجودی شارژ: $sumOfSingleProductRequestedRefill",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
                Text(
                    text = "مجموع اسکن شده: $sumOfScanned",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = modifier,
                )
            }
        }
    }

    @Composable
    fun LazyColumnItem(i: Int) {

        Row(
            modifier = Modifier
                .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                .background(
                    color = MaterialTheme.colors.onPrimary,
                    shape = MaterialTheme.shapes.small
                )
                .fillMaxWidth(),
        ) {

            Column(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp, start = 16.dp)
                    .weight(1F)
            ) {
                Text(
                    text = "سایز: " + uiList[i].size,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                Text(
                    text = "اسکن شده: " + uiList[i].scannedNumber,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp)
                    .weight(1F)
            ) {
                Text(
                    text = "موجودی انبار: " + uiList[i].warehouseNumber,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )

                Text(
                    text = "تعداد شارژ: " + uiList[i].refillNumber,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )

                Text(
                    text = "موجودی فروش: " + uiList[i].shoppingNumber,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp)
                    .weight(1F)
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colors.background,
                            shape = MaterialTheme.shapes.small
                        )
                        .wrapContentWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (uiList[i].refillNumber < uiList[i].warehouseNumber) {
                                uiList[i].refillNumber += 1
                                val uiListTemp = mutableStateListOf<ManualRefillAllProduct>()
                                uiListTemp.addAll(uiList)
                                uiList.clear()
                                uiList.addAll(uiListTemp)
                                val index =
                                    ManualRefillActivity.scannedAndRelatedToScannedProducts.indexOfLast {
                                        it.primaryKey == uiList[i].primaryKey
                                    }
                                if (index != -1) {
                                    ManualRefillActivity.scannedAndRelatedToScannedProducts[index].refillNumber =
                                        uiList[i].refillNumber
                                } else {
                                    Toast.makeText(
                                        this@ScannedProductsPropertyActivity,
                                        "مشکلی در پردازش اطلاعات به وجود آمده است",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                calculateSumOfRefillAndScanned()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            "",
                            tint = if (uiList[i].refillNumber < uiList[i].warehouseNumber) {
                                MaterialTheme.colors.primary
                            } else {
                                MaterialTheme.colors.secondary
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            if (uiList[i].refillNumber >= 1) {
                                uiList[i].refillNumber -= 1
                                val uiListTemp = mutableStateListOf<ManualRefillAllProduct>()
                                uiListTemp.addAll(uiList)
                                uiList.clear()
                                uiList.addAll(uiListTemp)
                                val index =
                                    ManualRefillActivity.scannedAndRelatedToScannedProducts.indexOfLast {
                                        it.primaryKey == uiList[i].primaryKey
                                    }
                                if (index != -1) {
                                    ManualRefillActivity.scannedAndRelatedToScannedProducts[index].refillNumber =
                                        uiList[i].refillNumber
                                } else {
                                    Toast.makeText(
                                        this@ScannedProductsPropertyActivity,
                                        "مشکلی در پردازش اطلاعات به وجود آمده است",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                calculateSumOfRefillAndScanned()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_remove_24),
                            "",
                            tint = if (uiList[i].refillNumber >= 1) {
                                MaterialTheme.colors.primary
                            } else {
                                MaterialTheme.colors.secondary
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    @Preview
    fun PreviewFun() {
        AppBar()
    }
}