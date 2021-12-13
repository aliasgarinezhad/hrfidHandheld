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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
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
    private var storeFilterValues by mutableStateOf(mutableMapOf(
            "0" to "دفتر مركزي",
            "5" to "انبار (بالنو و ساموئل)",
            "10" to "كرج مهستان",
            "11" to "انبار (لوازم مصرفي)",
            "17" to "شيراز ستارخان",
            "20" to "دفتر رويال",
            "21" to "اصفهان سيتي 1",
            "22" to "تهران ميرداماد",
            "24" to "رشت گلسار",
            "29" to "تهران پالاديوم",
            "34" to "نمك آبرود",
            "38" to "انبار (اصفهان)",
            "39" to "شيراز معالي آباد",
            "41" to "تهران سعادت آباد",
            "42" to "تهران كورش",
            "44" to "فروشگاه مركزي",
            "46" to "آرتميس",
            "47" to "اصفهان سيتي 2",
            "51" to "انبار (شيراز)",
            "52" to "تبريز اطلس",
            "53" to "تهران بام لند",
            "54" to "آمل اكسين",
            "56" to "اهواز تشريفات",
            "58" to "تهران امير 2",
            "59" to "مشهد سجاد",
            "60" to "جردن",
            "61" to "قم بازار شهر",
            "64" to "انبار (ضايعات)",
            "66" to "سانا",
            "67" to "تشميران",
            "68" to "مگامال",
            "69" to "سيوان",
            "70" to "اكومال",
            "71" to "تهران ايران مال 1",
            "73" to "تهران اپال",
            "74" to "تبريز لاله پارك",
            "75" to "اروميه سيتاديوم",
            "81" to "انبار (تبريز)",
            "82" to "انبار (اهواز)",
            "83" to "كرج مهراد",
            "84" to "بروجرد ديپلمات",
            "86" to "تهران برج نگار",
            "87" to "انزلي هپي مارت",
            "88" to "اروميه سيتاديوم بچگانه",
            "97" to "تهران كارخانه",
            "98" to "دفتر امارت",
            "101" to "كرج مهراد بچگانه",
            "103" to "شيراز شيرازمال",
            "104" to "مشهد سيتي استار",
            "105" to "اصفهان فدك مال",
            "107" to "ايران مال جين وست-باني مد",
            "108" to "ايران مال ديجي واش",
            "109" to "انبار (اروميه)",
            "110" to "انبار(انزلي)",
            "111" to "اهواز پارك سنتر",
            "112" to "اپليكيشن جين وست"
        ))
    private var wareHouseFilterValues by mutableStateOf(mutableListOf("همه", "فروشگاه", "انبار"))
    private var colorFilterValue by mutableStateOf("همه رنگ ها")
    private var sizeFilterValue by mutableStateOf("همه سایز ها")
    private var storeFilterValue by mutableStateOf("68")
    private var wareHouseFilterValue by mutableStateOf("همه")

    private var barcode2D = Barcode2D(this)

    @ExperimentalCoilApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Page()
        }
    }

    override fun onResume() {
        super.onResume()
        open()
    }

    override fun onPause() {
        super.onPause()
        close()
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
                "http://rfid-api-0-1.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&kbarcode=$barcode"

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
            "http://rfid-api-0-1.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&K_Bar_Code=$productCode"

        val request1 = JsonObjectRequest(url1, { response1 ->

            val products = response1.getJSONArray("products")

            if (products.length() > 0) {
                jsonArrayProcess(products)
            } else {

                val url2 =
                    "http://rfid-api-0-1.avakatan.ir/products/similars?DepartmentInfo_ID=$storeFilterValue&kbarcode=$productCode"

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
                    kbarcode = json.getString("KBarCode"),
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
        productJson.put("K_Bar_Code", product.productCode)
        productJson.put("kbarcode", product.kbarcode)
        productJson.put("OrigPrice", product.originalPrice)
        productJson.put("SalePrice", product.salePrice)
        productJson.put("BarcodeMain_ID", product.primaryKey)
        productJson.put("RFID", product.rfidKey)
        productJson.put("ImgUrl", product.imageUrl)
        productJson.put("dbCountDepo", product.warehouseNumber)
        productJson.put("dbCountStore", product.shoppingNumber)
        productJson.put("Size", product.size)
        productJson.put("Color", product.shoppingNumber)

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
                        StoreFilterDropDownList()
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
                                openSearchActivity(uiList[i])
                            },

                        ) {
                        Column {
                            Text(
                                text = filteredUiList[i].name,
                                style = MaterialTheme.typography.h1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Brown)
                            )

                            Text(
                                text = filteredUiList[i].size,
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.DarkGreen)
                            )

                            Text(
                                text = filteredUiList[i].color,
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Goldenrod)
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
                .fillMaxWidth(),
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
            Row(modifier = Modifier.clickable { expanded = true }) {
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
            Row(modifier = Modifier.clickable { expanded = true }) {
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
            Row(modifier = Modifier.clickable { expanded = true }) {
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

    @Composable
    fun StoreFilterDropDownList() {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box {
            Row(modifier = Modifier.clickable { expanded = true }) {
                storeFilterValues[storeFilterValue]?.let { Text(text = it) }
                Icon(imageVector = Icons.Filled.ArrowDropDown, "")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                storeFilterValues.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        storeFilterValue = it.key
                        getSimilarProducts()
                    }) {
                        Text(text = it.value)
                    }
                }
            }
        }
    }
}