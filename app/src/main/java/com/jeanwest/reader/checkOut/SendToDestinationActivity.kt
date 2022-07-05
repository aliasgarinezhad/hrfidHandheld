package com.jeanwest.reader.checkOut

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.sharedClassesAndFiles.JalaliDate.JalaliDate
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.sharedClassesAndFiles.Product
import com.jeanwest.reader.sharedClassesAndFiles.ErrorSnackBar
import com.jeanwest.reader.sharedClassesAndFiles.Item
import com.jeanwest.reader.sharedClassesAndFiles.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalFoundationApi::class)
class SendToDestinationActivity : ComponentActivity() {

    private var uiList = mutableStateListOf<Product>()
    private var fileName by mutableStateOf("حواله ارسالی تاریخ ")
    private var openFileDialog by mutableStateOf(false)
    private var numberOfScanned by mutableStateOf(0)
    private var destination by mutableStateOf("انتخاب مقصد")
    private var destinations = mutableStateMapOf<String, Int>()
    private var source = 0
    private var scannedBarcodeTable = mutableListOf<String>()
    private var scannedEpcTable = mutableListOf<String>()
    private var state = SnackbarHostState()
    private val apiTimeout = 30000
    private var isSubmitting by mutableStateOf(false)
    private lateinit var queue : RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }

        queue = Volley.newRequestQueue(this)

        val type = object : TypeToken<SnapshotStateList<Product>>() {}.type

        uiList = Gson().fromJson(
            intent.getStringExtra("CheckOutProducts"), type
        ) ?: mutableStateListOf()

        numberOfScanned = intent.getIntExtra("CheckOutValidScannedProductsNumber", 0)

        val util = JalaliDate()
        fileName += util.currentShamsidate

        getDestinationLists()
        loadMemory()
        Log.e("error", source.toString())
    }

    private fun loadMemory() {
        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        source = memory.getInt("userWarehouseCode", 0)

        scannedEpcTable = Gson().fromJson(
            memory.getString("CheckOutEPCTable", ""),
            scannedEpcTable.javaClass
        ) ?: mutableListOf()

        scannedBarcodeTable = Gson().fromJson(
            memory.getString("CheckOutBarcodeTable", ""),
            scannedBarcodeTable.javaClass
        ) ?: mutableListOf()
    }

    private fun getDestinationLists() {

        val url = "https://rfid-api.avakatan.ir/department-infos"
        val request = object : JsonArrayRequest(Method.GET, url, null, {

            for (i in 0 until it.length()) {
                try {
                    val warehouses = it.getJSONObject(i).getJSONArray("wareHouses")

                    for (j in 0 until warehouses.length()) {
                        val warehouse = warehouses.getJSONObject(j)
                        if (warehouse.getString("WareHouseTypes_ID") == "2") {
                            destinations[warehouse.getString("WareHouseTitle")] =
                                warehouse.getInt("WareHouse_ID")
                        }
                    }
                } catch (e: Exception) {

                }
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
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }
        }

        queue.add(request)
    }

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("ارسالی به فروشگاه")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("کد جست و جو")
        headerRow.createCell(1).setCellValue("تعداد")

        uiList.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.KBarCode)
            row.createCell(1).setCellValue((it.scannedEPCNumber + it.scannedBarcodeNumber).toDouble())
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
        applicationContext.startActivity(shareIntent)
    }

    private fun sendToDestination() {

        if (destination == "انتخاب مقصد") {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "لطفا مقصد را انتخاب کنید",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }

        isSubmitting = true

        val url = "https://rfid-api.avakatan.ir/stock-draft"
        val request = object : JsonObjectRequest(Method.POST, url, null, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "حواله با موفقیت ثبت شد",
                    null,
                    SnackbarDuration.Long
                )
            }
            isSubmitting = false
        }, {
            if (it is NoConnectionError) {

                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                        null,
                        SnackbarDuration.Long
                    )
                }
            } else {
                CoroutineScope(Dispatchers.Default).launch {
                    state.showSnackbar(
                        it.toString(),
                        null,
                        SnackbarDuration.Long
                    )
                }
            }
            isSubmitting = false
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] =
                    "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {

                val body = JSONObject()
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
                val barcodeArray = JSONArray()
                val epcArray = JSONArray()

                uiList.forEach {
                    repeat(it.scannedEPCNumber) { i ->
                        val productJson = JSONObject()
                        productJson.put("BarcodeMain_ID", it.primaryKey)
                        productJson.put("kbarcode", it.KBarCode)
                        productJson.put("K_Name", it.kName)
                        productJson.put("epc", it.scannedEPCs[i])
                        epcArray.put(productJson)
                    }
                    repeat(it.scannedBarcodeNumber) { _ ->
                        val productJson = JSONObject()
                        productJson.put("BarcodeMain_ID", it.primaryKey)
                        productJson.put("kbarcode", it.KBarCode)
                        productJson.put("K_Name", it.kName)
                        barcodeArray.put(productJson)
                    }
                }

                body.put("desc", "برای تست")
                body.put("createDate", sdf.format(Date()))
                body.put("fromWarehouseId", source)
                body.put("toWarehouseId", destinations[destination])
                body.put("kbarcodes", barcodeArray)
                body.put("epcs", epcArray)

                return body.toString().toByteArray()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            apiTimeout,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 4) {
            back()
        }
        return true
    }

    private fun back() {
        queue.stop()
        finish()
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                    bottomBar = { BottomAppBar() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun BottomAppBar() {
        BottomAppBar(backgroundColor = MaterialTheme.colors.background) {

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "مجموع: $numberOfScanned",
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )

                CategoryFilterDropDownList(modifier = Modifier.align(Alignment.CenterVertically))

                Row(
                    modifier = Modifier
                        .wrapContentHeight()
                ) {
                    Button(
                        onClick = {
                            if(!isSubmitting) {
                                sendToDestination()
                            }
                        },
                    ) {
                        if(!isSubmitting) {
                            Text(text = "ثبت حواله")
                        } else {
                            Text(text = "در حال ثبت ...")
                        }
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

            actions = {
                IconButton(onClick = { openFileDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_share_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.checkOut),
                    modifier = Modifier
                        .padding(end = 15.dp)
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

        Column {

            if (openFileDialog) {
                FileAlertDialog()
            }

            LazyColumn(modifier = Modifier.padding(top = 8.dp, bottom = 56.dp)) {

                items(uiList.size) { i ->
                    Item(i, uiList,
                        text1 = "اسکن: " + uiList[i].scannedNumber,
                        text2 = "انبار: " + uiList[i].wareHouseNumber.toString())
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
                            openFileDialog = false
                            exportFile()
                        }) {
                        Text(text = "ذخیره")
                    }
                }
            },

            onDismissRequest = {
                openFileDialog = false
            }
        )
    }

    @Composable
    fun CategoryFilterDropDownList(modifier: Modifier) {

        var expanded by rememberSaveable {
            mutableStateOf(false)
        }

        Box(modifier = modifier) {
            Row(modifier = modifier.clickable { expanded = true }) {
                Text(
                    text = destination, Modifier
                        .align(Alignment.CenterVertically)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown, "", Modifier
                        .align(Alignment.CenterVertically)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.wrapContentWidth()
            ) {

                destinations.forEach {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        destination = it.key
                    }) {
                        Text(text = it.key)
                    }
                }
            }
        }
    }
}