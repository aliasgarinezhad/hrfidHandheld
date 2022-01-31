package com.jeanwest.reader.search

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
//import com.jeanwest.reader.testClasses.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.theme.MyApplicationTheme
import org.json.JSONArray
import org.json.JSONObject

class SearchActivity : ComponentActivity(), IBarcodeResult {

    private var productCode by mutableStateOf("")
    private var uiList by mutableStateOf(mutableListOf<SearchResultProducts>())
    private var filteredUiList by mutableStateOf(mutableListOf<SearchResultProducts>())
    private var colorFilterValues by mutableStateOf(mutableListOf("همه رنگ ها"))
    private var sizeFilterValues by mutableStateOf(mutableListOf("همه سایز ها"))
    private var wareHouseFilterValues by mutableStateOf(mutableListOf("فروشگاه و انبار", "فروشگاه", "انبار"))
    private var colorFilterValue by mutableStateOf("همه رنگ ها")
    private var sizeFilterValue by mutableStateOf("همه سایز ها")
    private var storeFilterValue = 0
    private var wareHouseFilterValue by mutableStateOf("فروشگاه و انبار")

    private var barcode2D = Barcode2D(this)

    @ExperimentalCoilApi
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
        storeFilterValue = memory.getInt("deviceLocationCode", 0)
    }

    @Throws(InterruptedException::class)
    override fun getBarcode(barcode: String) {

        if (barcode.isNotEmpty()) {

            filteredUiList = mutableListOf()
            uiList = mutableListOf()
            colorFilterValues = mutableListOf("همه رنگ ها")
            sizeFilterValues = mutableListOf("همه سایز ها")
            productCode = ""

            val url =
                "http://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&kbarcode=$barcode"

            val request = JsonObjectRequest(url, {

                val products = it.getJSONArray("products")
                if (products.length() > 0) {
                    jsonArrayProcess(products)
                }
            }, {
                Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
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

    private fun filterUiList(uiList: MutableList<SearchResultProducts>): MutableList<SearchResultProducts> {

        val wareHouseFilterOutput = when (wareHouseFilterValue) {
            "فروشگاه" -> {
                uiList.filter {
                    it.shoppingNumber > 0
                }
            }
            "انبار" -> {
                uiList.filter {
                    it.warehouseNumber > 0
                }
            }
            else -> {
                uiList
            }
        }

        val sizeFilterOutput = if (sizeFilterValue == "همه سایز ها") {
            wareHouseFilterOutput
        } else {
            wareHouseFilterOutput.filter {
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

        return colorFilterOutput.toMutableList()
    }

    private fun getSimilarProducts() {

        uiList = mutableListOf()
        filteredUiList = mutableListOf()
        colorFilterValues = mutableListOf("همه رنگ ها")
        sizeFilterValues = mutableListOf("همه سایز ها")

        val url1 =
            "http://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&K_Bar_Code=$productCode"

        val request1 = JsonObjectRequest(url1, { response1 ->

            val products = response1.getJSONArray("products")

            if (products.length() > 0) {
                jsonArrayProcess(products)
            } else {

                val url2 =
                    "http://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&kbarcode=$productCode"

                val request2 = JsonObjectRequest(url2, { response2 ->

                    val products2 = response2.getJSONArray("products")

                    if (products2.length() > 0) {
                        jsonArrayProcess(products2)
                    }
                }, { it2 ->
                    Toast.makeText(this, it2.toString(), Toast.LENGTH_SHORT).show()
                })
                val queue2 = Volley.newRequestQueue(this)
                queue2.add(request2)
            }
        }, {
            Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
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
                SearchResultProducts(
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
                    rfidKey = json.getLong("RFID")
                )
            )
        }

        productCode = uiList[0].productCode
        colorFilterValues = colorFilterValues.distinct().toMutableList()
        sizeFilterValues = sizeFilterValues.distinct().toMutableList()
        filteredUiList = filterUiList(uiList)
    }

    private fun openSearchActivity(product : SearchResultProducts) {

        val productJson = JSONObject()
        productJson.put("productName", product.name)
        productJson.put(  "K_Bar_Code", product.productCode)
        productJson.put("kbarcode", product.KBarCode)
        productJson.put("OrigPrice", product.originalPrice)
        productJson.put("SalePrice", product.salePrice)
        productJson.put("BarcodeMain_ID", product.primaryKey)
        productJson.put("RFID", product.rfidKey)
        productJson.put("ImgUrl", product.imageUrl)
        productJson.put("dbCountDepo", product.warehouseNumber)
        productJson.put("dbCountStore", product.shoppingNumber)
        productJson.put("Size", product.size)
        productJson.put("Color", product.color)

        val intent = Intent(this, SearchSubActivity::class.java)
        intent.putExtra("product", productJson.toString())
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
                    content = { Content() }
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

    @ExperimentalCoilApi
    @Composable
    fun Content() {
        val modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .wrapContentWidth()

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp, top = 5.dp)
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.small
                    )
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column {
                    ProductCodeTextField()
                    Row(
                        modifier = Modifier
                            .padding(bottom = 5.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        WarehouseFilterDropDownList()
                        ColorFilterDropDownList()
                        SizeFilterDropDownList()
                    }
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 2.dp)) {

                items(filteredUiList.size) { i ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .background(
                                color = MaterialTheme.colors.onPrimary,
                                shape = MaterialTheme.shapes.small
                            )
                            .fillMaxWidth()
                            .clickable {
                                openSearchActivity(filteredUiList[i])
                            }
                            .testTag("SearchItems"),

                        ) {
                        Column {
                            Text(
                                text = filteredUiList[i].name,
                                style = MaterialTheme.typography.h1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                //color = colorResource(id = R.color.Brown)
                            )

                            Text(
                                text = filteredUiList[i].size,
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                //color = colorResource(id = R.color.DarkGreen)
                            )

                            Text(
                                text = filteredUiList[i].color,
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                //color = colorResource(id = R.color.Goldenrod)
                            )
                        }

                        Image(
                            painter = rememberImagePainter(
                                filteredUiList[i].imageUrl,
                            ),
                            contentDescription = "",
                            modifier = Modifier
                                .height(100.dp)
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ProductCodeTextField() {

        OutlinedTextField(
            value = productCode, onValueChange = {
                productCode = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 10.dp)
                .fillMaxWidth()
                .testTag("SearchProductCodeTextField"),
            label = { Text(text = "کد محصول") },
            keyboardActions = KeyboardActions(onSearch = { getSimilarProducts() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
    }

    @Composable
    fun SizeFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .testTag("SearchSizeFilterDropDownList"),
            ) {
                Text(text = sizeFilterValue)
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                sizeFilterValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        sizeFilterValue = it
                        filteredUiList = filterUiList(uiList)
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun ColorFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier
                .clickable { expanded = true }
                .testTag("SearchColorFilterDropDownList")) {
                Text(text = colorFilterValue)
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                colorFilterValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        colorFilterValue = it
                        filteredUiList = filterUiList(uiList)
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }

    @Composable
    fun WarehouseFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier
                .clickable { expanded = true }
                .testTag("SearchWarehouseFilterDropDownList")) {
                Text(text = wareHouseFilterValue)
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                wareHouseFilterValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        wareHouseFilterValue = it
                        filteredUiList = filterUiList(uiList)
                    }) {
                        Text(text = it)
                    }
                }
            }
        }
    }
}