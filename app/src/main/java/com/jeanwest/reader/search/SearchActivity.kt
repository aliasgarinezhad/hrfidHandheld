package com.jeanwest.reader.search

import com.jeanwest.reader.hardware.Barcode2D
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.R
//import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.manualRefill.Product
import com.jeanwest.reader.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class SearchActivity : ComponentActivity(), IBarcodeResult {

    private var productCode by mutableStateOf("")
    private var uiList = mutableStateListOf<Product>()
    private var filteredUiList = mutableStateListOf<Product>()
    private var colorFilterValues = mutableStateListOf("همه رنگ ها")
    private var sizeFilterValues = mutableStateListOf("همه سایز ها")
    private var colorFilterValue by mutableStateOf("همه رنگ ها")
    private var sizeFilterValue by mutableStateOf("همه سایز ها")
    private var storeFilterValue = 0
    private var state = SnackbarHostState()

    private var barcode2D = Barcode2D(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Page()
        }
        loadMemory()
    }

    override fun onResume() {
        super.onResume()
        open()
    }

    override fun onPause() {
        super.onPause()
        close()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        storeFilterValue = memory.getInt("userLocationCode", 0)
    }

    @Throws(InterruptedException::class)
    override fun getBarcode(barcode: String) {

        if (barcode.isNotEmpty()) {

            filteredUiList.clear()
            uiList.clear()
            colorFilterValues = mutableStateListOf("همه رنگ ها")
            sizeFilterValues = mutableStateListOf("همه سایز ها")
            productCode = ""

            val url =
                "https://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&kbarcode=$barcode"

            val request = JsonObjectRequest(url, {

                val products = it.getJSONArray("products")
                if (products.length() > 0) {
                    jsonArrayProcess(products)
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
            })
            val queue = Volley.newRequestQueue(this)
            queue.add(request)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            start()
        } else if (keyCode == 4) {
            back()
        }
        return true
    }

    private fun start() {
        barcode2D.startScan(this)
    }

    private fun open() {
        barcode2D.open(this, this)
    }

    private fun close() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
    }

    private fun filterUiList() {

        val sizeFilterOutput = if (sizeFilterValue == "همه سایز ها") {
            uiList
        } else {
            uiList.filter {
                it.size == sizeFilterValue
            }
        }

        val colorFilterOutput = if (colorFilterValue == "همه رنگ ها") {
            sizeFilterOutput
        } else {
            sizeFilterOutput.filter {
                it.color == colorFilterValue
            }

        }

        filteredUiList.clear()
        filteredUiList.addAll(colorFilterOutput)
    }

    private fun getSimilarProducts() {

        uiList.clear()
        filteredUiList.clear()
        colorFilterValues = mutableStateListOf("همه رنگ ها")
        sizeFilterValues = mutableStateListOf("همه سایز ها")

        val url1 =
            "https://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&K_Bar_Code=$productCode"

        val request1 = JsonObjectRequest(url1, { response1 ->

            val products = response1.getJSONArray("products")

            if (products.length() > 0) {
                jsonArrayProcess(products)
            } else {

                val url2 =
                    "https://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&kbarcode=$productCode"

                val request2 = JsonObjectRequest(url2, { response2 ->

                    val products2 = response2.getJSONArray("products")

                    if (products2.length() > 0) {
                        jsonArrayProcess(products2)
                    }
                }, { it2 ->
                    when (it2) {
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
                                    it2.toString(),
                                    null,
                                    SnackbarDuration.Long
                                )
                            }
                        }
                    }
                })
                val queue2 = Volley.newRequestQueue(this)
                queue2.add(request2)
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
        })

        val queue = Volley.newRequestQueue(this)
        queue.add(request1)
    }

    private fun jsonArrayProcess(similarProductsJsonArray: JSONArray) {

        for (i in 0 until similarProductsJsonArray.length()) {

            val json = similarProductsJsonArray.getJSONObject(i)

            colorFilterValues.add(json.getString("Color"))
            sizeFilterValues.add(json.getString("Size"))

            uiList.add(
                Product(
                    name = json.getString("productName"),
                    KBarCode = json.getString("KBarCode"),
                    imageUrl = json.getString("ImgUrl"),
                    storeNumber = json.getInt("dbCountStore"),
                    wareHouseNumber = json.getInt("dbCountDepo"),
                    productCode = json.getString("K_Bar_Code"),
                    size = json.getString("Size"),
                    color = json.getString("Color"),
                    originalPrice = json.getString("OrigPrice"),
                    salePrice = json.getString("SalePrice"),
                    primaryKey = json.getLong("BarcodeMain_ID"),
                    rfidKey = json.getLong("RFID"),
                    kName = json.getString("K_Name"),
                )
            )
        }

        productCode = uiList[0].productCode
        colorFilterValues = colorFilterValues.distinct().toMutableStateList()
        sizeFilterValues = sizeFilterValues.distinct().toMutableStateList()
        filterUiList()
    }

    private fun openSearchActivity(product: Product) {

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", Gson().toJson(product).toString())
        startActivity(intent)
    }

    private fun back() {
        close()
        finish()
    }

    @ExperimentalCoilApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
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
                        "جست و جو", textAlign = TextAlign.Center,
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

    @Composable
    fun Content() {

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .shadow(6.dp, Shapes.medium)
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.large
                    )
                    .fillMaxWidth(),
            ) {
                Column {
                    ProductCodeTextField(modifier = Modifier
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 14.dp)
                        .fillMaxWidth()
                        .testTag("SearchProductCodeTextField"),
                        hint = "کد محصول",
                        onSearch = { getSimilarProducts() },
                        onValueChange = { productCode = it },
                        value = productCode
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilterDropDownList(
                            modifier = Modifier
                                .padding(start = 16.dp, bottom = 16.dp),
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_color_lens_24),
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
                                    text = colorFilterValue,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 4.dp)
                                )
                            },
                            onClick = {
                                colorFilterValue = it
                                filterUiList()
                            },
                            values = colorFilterValues
                        )
                        FilterDropDownList(
                            modifier = Modifier
                                .padding(start = 16.dp, bottom = 16.dp),
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.size),
                                    contentDescription = "",
                                    tint = iconColor,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 6.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = sizeFilterValue,
                                    style = MaterialTheme.typography.body2,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 6.dp)
                                )
                            },
                            onClick = {
                                sizeFilterValue = it
                                filterUiList()
                            },
                            values = sizeFilterValues
                        )
                    }
                }
            }

            LazyColumn {

                items(filteredUiList.size) { i ->
                    Item(i, filteredUiList, text1 = "رنگ: " + filteredUiList[i].color,
                    text2 = "سایز: " + filteredUiList[i].size, clickable = true) {
                        openSearchActivity(filteredUiList[i])
                    }
                }
            }
        }
    }

    @ExperimentalFoundationApi
    @Composable
    fun LazyColumnItem(i: Int) {

        Row(
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .background(
                    color = MaterialTheme.colors.onPrimary,
                    shape = MaterialTheme.shapes.small
                )
                .fillMaxWidth()
                .height(80.dp)
                .clickable(
                    onClick = {
                        openSearchActivity(filteredUiList[i])
                    },
                )
                .testTag("SearchItems"),
        ) {

            Image(
                painter = rememberImagePainter(
                    filteredUiList[i].imageUrl,
                ),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 8.dp)
            ) {

                Column(
                    modifier = Modifier
                        .weight(1.5F)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = filteredUiList[i].name,
                        style = MaterialTheme.typography.h1,
                        textAlign = TextAlign.Right,
                    )

                    Text(
                        text = filteredUiList[i].KBarCode,
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
                        text = "رنگ: " + filteredUiList[i].color,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                    Text(
                        text = "سایز: " + filteredUiList[i].size,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Right,
                    )
                }
            }
        }
    }
}