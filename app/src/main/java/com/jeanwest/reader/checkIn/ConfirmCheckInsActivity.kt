package com.jeanwest.reader.checkIn

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.JalaliDate.JalaliDate
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream


@ExperimentalCoilApi
class ConfirmCheckInsActivity : ComponentActivity() {

    private var conflictResultProducts by mutableStateOf(mutableListOf<CheckInConflictResultProduct>())
    private var uiList by mutableStateOf(mutableListOf<CheckInConflictResultProduct>())
    private var openDialog by mutableStateOf(false)
    private var fileName by mutableStateOf("حواله تایید شده تاریخ ")
    private var shortagesNumber by mutableStateOf(0)
    private var additionalNumber by mutableStateOf(0)
    private var numberOfScanned by mutableStateOf(0)
    private var checkInProperties = mutableListOf<CheckInProperties>()

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }

        val type = object : TypeToken<List<CheckInConflictResultProduct>>() {}.type

        conflictResultProducts = Gson().fromJson(
            intent.getStringExtra("additionalAndShortageProducts"), type
        ) ?: mutableListOf()

        uiList = conflictResultProducts.filter { it1 ->
            it1.scan == "کسری" || it1.scan == "اضافی" || it1.scan == "اضافی فایل"
        }.toMutableList()

        numberOfScanned = intent.getIntExtra("numberOfScanned", 0)
        shortagesNumber = intent.getIntExtra("shortagesNumber", 0)
        additionalNumber = intent.getIntExtra("additionalNumber", 0)

        loadMemory()

        val util = JalaliDate()
        fileName += util.currentShamsidate
    }

    private fun loadMemory() {

        val type = object : TypeToken<List<CheckInProperties>>() {}.type

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        checkInProperties = Gson().fromJson(
            memory.getString("GetBarcodesByCheckInNumberActivityUiList", ""),
            type
        ) ?: mutableListOf()
    }

    private fun exportFile() {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("تروفالس")

        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("کد جست و جو")
        headerRow.createCell(1).setCellValue("تعداد")
        headerRow.createCell(2).setCellValue("کسری")
        headerRow.createCell(3).setCellValue("اضافی")
        headerRow.createCell(4).setCellValue("نشانه")

        conflictResultProducts.forEach {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(it.KBarCode)
            row.createCell(1).setCellValue(it.scannedNumber.toDouble())

            if (it.scan == "کسری") {
                row.createCell(2).setCellValue(it.matchedNumber.toDouble())
            } else if (it.scan == "اضافی" || it.scan == "اضافی فایل") {
                row.createCell(3).setCellValue(it.matchedNumber.toDouble())
            }
        }

        val row = sheet.createRow(sheet.physicalNumberOfRows)
        row.createCell(0).setCellValue("مجموع")
        row.createCell(1).setCellValue(numberOfScanned.toDouble())
        row.createCell(2).setCellValue(shortagesNumber.toDouble())
        row.createCell(3).setCellValue(additionalNumber.toDouble())

        val sheet2 = workbook.createSheet("کسری")

        val header2Row = sheet2.createRow(sheet2.physicalNumberOfRows)
        header2Row.createCell(0).setCellValue("کد جست و جو")
        header2Row.createCell(1).setCellValue("موجودی")
        header2Row.createCell(2).setCellValue("کسری")
        header2Row.createCell(3).setCellValue("نشانه")

        conflictResultProducts.forEach {

            if (it.scan == "کسری") {
                val shortageRow = sheet2.createRow(sheet2.physicalNumberOfRows)
                shortageRow.createCell(0).setCellValue(it.KBarCode)
                shortageRow.createCell(1)
                    .setCellValue(it.scannedNumber.toDouble() + it.matchedNumber.toDouble())
                shortageRow.createCell(2).setCellValue(it.matchedNumber.toDouble())
            }
        }

        val sheet3 = workbook.createSheet("اضافی")

        val header3Row = sheet3.createRow(sheet3.physicalNumberOfRows)
        header3Row.createCell(0).setCellValue("کد جست و جو")
        header3Row.createCell(1).setCellValue("موجودی")
        header3Row.createCell(2).setCellValue("اضافی")
        header3Row.createCell(3).setCellValue("نشانه")

        conflictResultProducts.forEach {

            if (it.scan == "اضافی") {
                val additionalRow = sheet3.createRow(sheet3.physicalNumberOfRows)
                additionalRow.createCell(0).setCellValue(it.KBarCode)
                additionalRow.createCell(1)
                    .setCellValue(it.matchedNumber - it.scannedNumber.toDouble())
                additionalRow.createCell(2).setCellValue(it.matchedNumber.toDouble())
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
        applicationContext.startActivity(shareIntent)
    }

    private fun confirmCheckIns() {

        if(checkInProperties.size == 0) {
            Toast.makeText(this, "حواله ای برای تایید وجود ندارد", Toast.LENGTH_LONG).show()
            return
        }

        val url = "http://rfid-api.avakatan.ir/stock-draft/confirm"
        val request = object : JsonArrayRequest(Method.POST, url, null, {
            Toast.makeText(this, "حواله ها با موفقیت تایید شدند", Toast.LENGTH_LONG).show()
        }, {
            if (it is NoConnectionError) {
                Toast.makeText(
                    this,
                    "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, it.toString(), Toast.LENGTH_LONG).show()
            }
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json;charset=UTF-8"
                params["Authorization"] = "Bearer " + MainActivity.token
                return params
            }

            override fun getBody(): ByteArray {
                val body = JSONArray()
                checkInProperties.forEach {
                    body.put(it.number)
                }
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

            actions = {
                IconButton(onClick = { openDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_share_24),
                        contentDescription = ""
                    )
                }
            },

            title = {
                Text(
                    text = stringResource(id = R.string.checkInText),
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

        val modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .wrapContentWidth()

        Column {

            if (openDialog) {
                FileAlertDialog()
            }

            LazyColumn(modifier = Modifier.padding(top = 5.dp)) {

                items(uiList.size) { i ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .background(
                                color = MaterialTheme.colors.onPrimary,
                                shape = MaterialTheme.shapes.small,
                            )
                            .fillMaxWidth(),
                    ) {
                        Column {
                            Text(
                                text = uiList[i].name,
                                style = MaterialTheme.typography.h1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                            )

                            Text(
                                text = uiList[i].KBarCode,
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                            )

                            Text(
                                text = if (uiList[i].scan == "کسری") {
                                    "تعداد کسری: " + uiList[i].matchedNumber.toString()
                                } else {
                                    "تعداد اضافی: " + uiList[i].matchedNumber.toString()
                                },
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                            )
                        }

                        Image(
                            painter = rememberImagePainter(
                                uiList[i].imageUrl,
                            ),
                            contentDescription = "",
                            modifier = Modifier
                                .height(100.dp)
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }

                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {

                        Text(
                            text = "مجموع کسری: ${shortagesNumber}",
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .padding(top = 5.dp, bottom = 5.dp, start = 5.dp)
                                .align(Alignment.CenterVertically)
                                .weight(1F)
                        )

                        Text(
                            text = "مجموع اضافی: ${additionalNumber}",
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .padding(top = 5.dp, bottom = 5.dp, start = 5.dp)
                                .align(Alignment.CenterVertically)
                                .weight(1F)
                        )

                        Button(
                            onClick = {
                                confirmCheckIns()
                            }, modifier = Modifier
                                .padding(top = 5.dp, bottom = 5.dp, start = 5.dp, end = 5.dp)
                                .weight(1F)
                        ) {
                            Text(text = "تایید حواله ها")
                        }
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
}