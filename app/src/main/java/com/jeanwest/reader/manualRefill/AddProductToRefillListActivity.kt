package com.jeanwest.reader.manualRefill

//import com.jeanwest.reader.sharedClassesAndFiles.Barcode2D
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.R
import com.jeanwest.reader.manualRefill.ManualRefillActivity.Companion.products
import com.jeanwest.reader.sharedClassesAndFiles.*
import com.jeanwest.reader.sharedClassesAndFiles.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import org.json.JSONArray

class AddProductToRefillListActivity : ComponentActivity(), IBarcodeResult {

    private var productCode by mutableStateOf("")
    private var uiList = mutableStateListOf<Product>()
    private var filteredUiList = mutableStateListOf<Product>()
    private var colorFilterValues = mutableStateListOf("همه رنگ ها")
    private var sizeFilterValues = mutableStateListOf("همه سایز ها")
    private var barcode2D = Barcode2D(this)
    private var colorFilterValue by mutableStateOf("همه رنگ ها")
    private var sizeFilterValue by mutableStateOf("همه سایز ها")
    private var storeFilterValue = 0
    private var state = SnackbarHostState()
    private val beep: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private lateinit var queue : RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Page()
        }
        queue = Volley.newRequestQueue(this)
        open()
        loadMemory()
        filterUiList()

        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()!!))
    }

    private fun loadMemory() {

        val type = object : TypeToken<SnapshotStateList<Product>>() {}.type

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        storeFilterValue = memory.getInt("userLocationCode", 0)

        productCode = memory.getString("productCodeAddManualRefill", "") ?: ""
        colorFilterValue =
            memory.getString("colorFilterValueAddManualRefill", "همه رنگ ها") ?: "همه رنگ ها"
        sizeFilterValue =
            memory.getString("sizeFilterValueAddManualRefill", "همه سایز ها") ?: "همه سایز ها"

        uiList = Gson().fromJson(
            memory.getString("uiListAddManualRefill", ""),
            type
        ) ?: mutableStateListOf()

        sizeFilterValues = Gson().fromJson(
            memory.getString("sizeFilterValuesAddManualRefill", ""),
            sizeFilterValues.javaClass
        ) ?: mutableStateListOf("همه سایز ها")

        colorFilterValues = Gson().fromJson(
            memory.getString("colorFilterValuesAddManualRefill", ""),
            colorFilterValues.javaClass
        ) ?: mutableStateListOf("همه رنگ ها")

    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = memory.edit()

        edit.putString("colorFilterValuesAddManualRefill", JSONArray(colorFilterValues).toString())
        edit.putString("sizeFilterValuesAddManualRefill", JSONArray(sizeFilterValues).toString())
        edit.putString("uiListAddManualRefill", Gson().toJson(uiList).toString())
        edit.putString("productCodeAddManualRefill", productCode)
        edit.putString("colorFilterValueAddManualRefill", colorFilterValue)
        edit.putString("sizeFilterValueAddManualRefill", sizeFilterValue)
        edit.apply()
    }

    private fun filterUiList() {

        uiList.forEach {
            var isRequested = false
            products.forEach { it1 ->
                if (it.KBarCode == it1.KBarCode) {
                    it.requestedNum = it1.requestedNum
                    isRequested = true
                }
            }
            if (!isRequested) {
                it.requestedNum = 0
            }
        }

        val wareHouseFilterOutput = uiList.filter {
            it.wareHouseNumber > 0
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

        filteredUiList.clear()
        filteredUiList.addAll(colorFilterOutput)
    }

    private fun getSimilarProducts() {

        uiList.clear()
        filteredUiList.clear()
        colorFilterValues.clear()
        colorFilterValues.add("همه رنگ ها")
        sizeFilterValues.clear()
        sizeFilterValues.add("همه سایز ها")

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
                queue.add(request2)
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
                    wareHouseNumber = json.getInt("dbCountDepo"),
                    productCode = json.getString("K_Bar_Code"),
                    size = json.getString("Size"),
                    color = json.getString("Color"),
                    originalPrice = json.getString("OrigPrice"),
                    salePrice = json.getString("SalePrice"),
                    primaryKey = json.getLong("BarcodeMain_ID"),
                    rfidKey = json.getLong("RFID"),
                    scannedEPCs = mutableListOf(),
                    scannedBarcode = "",
                    scannedEPCNumber = 0,
                    scannedBarcodeNumber = 0,
                    kName = json.getString("K_Name"),
                    requestedNum = 0,
                    storeNumber = json.getInt("dbCountStore"),
                )
            )
        }

        productCode = uiList[0].productCode
        colorFilterValues = colorFilterValues.distinct().toMutableStateList()
        sizeFilterValues = sizeFilterValues.distinct().toMutableStateList()
        filterUiList()
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

    override fun getBarcode(barcode: String?) {
        if (!barcode.isNullOrEmpty()) {
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            productCode = barcode
            getSimilarProducts()
        }
    }

    private fun addToRefillList(KBarCode: String) {

        val productIndexInUiList = filteredUiList.indexOfLast {
            it.KBarCode == KBarCode
        }

        products.forEach {
            if (filteredUiList[productIndexInUiList].KBarCode == it.KBarCode) {
                if (it.requestedNum >= it.wareHouseNumber) {
                    CoroutineScope(Main).launch {
                        state.showSnackbar(
                            "تعداد درخواستی از موجودی انبار بیشتر است.",
                            null,
                            SnackbarDuration.Long,
                        )
                    }
                    return
                } else {
                    it.requestedNum++
                    filterUiList()
                    saveToMemory()
                    return
                }
            }
        }
        products.add(filteredUiList[productIndexInUiList])
        products[products.indexOfLast {
            it.KBarCode == KBarCode
        }].requestedNum++

        filterUiList()
        saveToMemory()
    }

    private fun back() {
        saveToMemory()
        close()
        beep.release()
        queue.stop()
        finish()
    }

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
                        "اضافه کردن کالای جدید", textAlign = TextAlign.Center,
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

        Column {

            Column(
                modifier = Modifier
                    .padding(bottom = 0.dp)
                    .shadow(elevation = 6.dp, shape = MaterialTheme.shapes.large)
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.large
                    )
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CustomTextField(
                        modifier = Modifier
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 14.dp)
                            .weight(1F)
                            .fillMaxWidth(),
                        hint = "کد محصول",
                        onSearch = { getSimilarProducts() },
                        onValueChange = { productCode = it },
                        value = productCode
                    )
                }

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

            LazyColumn {

                items(filteredUiList.size) { i ->
                    Item(i, filteredUiList, true,
                        text1 = "فروشگاه: " + filteredUiList[i].storeNumber,
                        text2 = "انبار: " + filteredUiList[i].wareHouseNumber) {
                        addToRefillList(filteredUiList[i].KBarCode)
                    }
                }
            }
        }
    }
}