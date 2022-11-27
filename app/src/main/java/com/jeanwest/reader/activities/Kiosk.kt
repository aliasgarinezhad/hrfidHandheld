package com.jeanwest.reader.activities

//import com.jeanwest.reader.sharedClassesAndFiles.testClasses.Barcode2D
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jeanwest.reader.R
import com.jeanwest.reader.management.*
import com.jeanwest.reader.data.Product
import com.jeanwest.reader.data.getProductsSimilar
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.test.Barcode2D
import com.jeanwest.reader.ui.JeanswestBackground
import com.jeanwest.reader.ui.MyApplicationTheme
import com.jeanwest.reader.ui.Shapes
import com.jeanwest.reader.ui.Typography

class Kiosk : ComponentActivity(),
    IBarcodeResult {

    private var productCode by mutableStateOf("")
    var uiList = mutableStateListOf<Product>()
    private var storeCode = 0
    private var state = SnackbarHostState()
    var loading by mutableStateOf(false)

    private var barcode2D =
        Barcode2D(this)
    private lateinit var queue: RequestQueue
    val beep = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Page()
        }
        queue = Volley.newRequestQueue(this)
        loadMemory()
        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onResume() {
        super.onResume()
        barcodeInit()
    }

    override fun onPause() {
        super.onPause()
        stopBarcodeScan()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        storeCode = memory.getInt("userLocationCode", 0)
    }

    @Throws(InterruptedException::class)
    override fun getBarcode(barcode: String) {

        if (barcode.isNotEmpty()) {

            loading = true

            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            uiList.clear()
            uiList.clear()
            productCode = ""

            getProductsSimilar(queue, state, storeCode, barcode, {
                uiList.addAll(it)
                productCode = uiList[0].productCode
                loading = false
            }, {
                loading = false
            })
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {
            startBarcodeScan()
        } else if (keyCode == 4) {
            back()
        }
        return true
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

    private fun openSearchActivity(product: Product) {

        val intent = Intent(this, SearchSpecialProduct::class.java)
        intent.putExtra("product", Gson().toJson(product).toString())
        startActivity(intent)
    }

    private fun back() {
        stopBarcodeScan()
        queue.stop()
        beep.release()
        finish()
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
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
                        stringResource(R.string.search), textAlign = TextAlign.Center,
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

        if (loading) {
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .background(JeanswestBackground, Shapes.small)
                    .fillMaxWidth()
            ) {
                LoadingCircularProgressIndicator(false, loading)
            }
        } else if (uiList.isNotEmpty()) {

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
                    Row(
                        modifier = Modifier
                            .height(90.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1F)
                                .fillMaxHeight()
                                .padding(top = 16.dp, bottom = 16.dp, start = 16.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                uiList[0].name,
                                style = MaterialTheme.typography.h2,
                                textAlign = TextAlign.Right,
                            )

                            Text(
                                "کد فرعی: " + uiList[0].productCode,
                                style = MaterialTheme.typography.h2,
                                textAlign = TextAlign.Right,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1F)
                                .fillMaxHeight()
                                .padding(top = 16.dp, bottom = 16.dp, start = 16.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                "قیمت: " + uiList[0].originalPrice,
                                style = MaterialTheme.typography.h2,
                                textAlign = TextAlign.Right,
                            )
                            Text(
                                "قیمت فروش: " + uiList[0].salePrice,
                                style = MaterialTheme.typography.h2,
                                textAlign = TextAlign.Right,
                            )
                        }
                    }
                }
                LazyColumn {

                    items(uiList.size) { i ->
                        Item(
                            i,
                            uiList,
                            text1 = "رنگ: " + uiList[i].color,
                            text2 = "سایز: " + uiList[i].size,
                            text3 = "انبار: " + uiList[i].wareHouseNumber,
                            text4 = "فروشگاه: " + uiList[i].storeNumber,
                            clickable = true
                        ) {
                            openSearchActivity(uiList[i])
                        }
                    }
                }
            }

        } else {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(256.dp)
                ) {
                    Box(

                        modifier = Modifier
                            .background(color = Color.White, shape = Shapes.medium)
                            .size(256.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_big_barcode_scan),
                            contentDescription = "",
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clickable {
                                    startBarcodeScan()
                                }
                        )
                    }

                    Text(
                        "برای دیدن مشخصات کالا، ابتدا بارکد آن را اسکن کنید",
                        style = Typography.h1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}
