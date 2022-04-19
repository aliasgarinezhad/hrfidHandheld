package com.jeanwest.reader.checkOut

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.JalaliDate.JalaliDate
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.refill.RefillProduct
import com.jeanwest.reader.theme.ErrorSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
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


@ExperimentalCoilApi
class SendToDestinationActivity : ComponentActivity() {

    private var uiList by mutableStateOf(mutableListOf<CheckOutProduct>())
    private var fileName by mutableStateOf("حواله ارسالی تاریخ ")
    private var openFileDialog by mutableStateOf(false)
    private var numberOfScanned by mutableStateOf(0)
    private var destination by mutableStateOf("انتخاب مقصد")
    private var destinations = mutableStateMapOf<String, Int>()
    private var source = 0
    private var scannedBarcodeTable = mutableListOf<String>()
    private var scannedEpcTable = mutableListOf<String>()
    private var state = SnackbarHostState()


    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }

        val type = object : TypeToken<List<CheckOutProduct>>() {}.type

        uiList = Gson().fromJson(
            intent.getStringExtra("CheckOutProducts"), type
        ) ?: mutableListOf()

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

        val queue = Volley.newRequestQueue(this)
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
            row.createCell(1).setCellValue(it.scannedNumber.toDouble())
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

        val url = "https://rfid-api.avakatan.ir/test/stock-draft"
        val request = object : JsonObjectRequest(Method.POST, url, null, {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "حواله با موفقیت ثبت شد",
                    null,
                    SnackbarDuration.Long
                )
            }
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
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] =
                    "Bearer " + "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MDE2IiwibmFtZSI6Itiq2LPYqiBSRklEINiq2LPYqiBSRklEIiwicm9sZXMiOlsidXNlciJdLCJzY29wZXMiOlsiZXJwIl0sImlhdCI6MTY0NzQzMDM1NywiZXhwIjoxNzA1NDkxMTU3LCJhdWQiOiJlcnAifQ.ai8CAS5qWTUIKsrEni6HeJcVPxP4k07LQ4Tl0-VbgHs"
                return params
            }

            override fun getBody(): ByteArray {

                val body = JSONObject()

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:s.SSS'Z'", Locale.ENGLISH)

                val epcArray = JSONArray()

                scannedEpcTable.forEach {
                    epcArray.put(it)
                }

                val barcodeArray = JSONArray()

                scannedBarcodeTable.forEach {
                    barcodeArray.put(it)
                }

                body.put("desc", "برای تست")
                body.put("createDate", sdf.format(Date()))
                body.put("fromWarehouseId", source)
                body.put("toWarehouseId", destinations[destination])
                body.put("kbarcodes", barcodeArray)
                body.put("epcs", epcArray)

                Log.e("error", body.toString())

                return body.toString().toByteArray()
            }
        }
        val queue = Volley.newRequestQueue(this)
        queue.add(request)
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
                            sendToDestination()
                        },
                    ) {
                        Text(text = "ثبت حواله")
                    }
                }
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
                    LazyColumnItem(i)
                }
            }
        }
    }

    @Composable
    fun LazyColumnItem(i: Int) {

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .background(
                    color = MaterialTheme.colors.onPrimary,
                    shape = MaterialTheme.shapes.small,
                )
                .fillMaxWidth()
                .height(80.dp),
        ) {

            Image(
                painter = rememberImagePainter(
                    uiList[i].imageUrl,
                ),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .padding(vertical = 8.dp, horizontal = 8.dp)
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