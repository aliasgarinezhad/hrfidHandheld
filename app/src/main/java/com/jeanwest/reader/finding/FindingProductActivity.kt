package com.jeanwest.reader.finding

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.jeanwest.reader.R
import com.jeanwest.reader.hardware.Barcode2D
import com.jeanwest.reader.hardware.IBarcodeResult
import com.jeanwest.reader.theme.MyApplicationTheme
import kotlinx.android.synthetic.main.activity_finding.*
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class FindingProductActivity : AppCompatActivity(),
    IBarcodeResult {

    private var productCode by mutableStateOf("")
    private var barcode2D = Barcode2D(this)
    lateinit var list: ListView
    var listString = ArrayList<String>()
    var pictureURLList = ArrayList<String>()
    lateinit var kBarCode: EditText
    private lateinit var nextActivityIntent: Intent
    private var product: JSONObject = JSONObject()
    lateinit var api: FindingProductAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finding)
        list = findViewById(R.id.findingListView)
        kBarCode = findViewById(R.id.finding_k_bar_code_text)
        nextActivityIntent = Intent(this, FindingProductSubActivity::class.java)

        list.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            product = api.similar.getJSONObject(i);
            nextActivityIntent.putExtra("product", product.toString())
            startActivity(nextActivityIntent)
        }

        kBarCode.setOnEditorActionListener { _, _, _ ->

            receive(kBarCode)
            true
        }
        finding_toolbar.setNavigationOnClickListener {
            back()
        }
    }

    override fun onResume() {
        super.onResume()
        open()
        val findingListAdapter = MyListAdapterFind(this, listString, pictureURLList)
        list.adapter = findingListAdapter
    }

    override fun onPause() {
        super.onPause()
        close()
    }

    @Throws(InterruptedException::class)
    override fun getBarcode(barcode: String) {

        var json: JSONObject

        if (barcode.isNotEmpty()) {

            api = FindingProductAPI()
            api.barcode = "kbarcode=$barcode"
            api.start()

            while (api.run) {
            }
            if (!api.status) {
                Toast.makeText(this, api.response, Toast.LENGTH_LONG).show()
                return
            }

            val view = this.currentFocus
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view!!.windowToken, 0)
            listString.clear()
            pictureURLList.clear()
            try {
                kBarCode.setText(
                    api.similar.getJSONObject(
                        0
                    ).getString("K_Bar_Code")
                )
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            for (i in 0 until api.similar.length()) {
                try {
                    json = api.similar.getJSONObject(i)
                    listString.add(
                        """
                    سایز: ${json.getString("Size")}
                    
                    رنگ: ${json.getString("Color")}
                    """.trimIndent()
                    )
                    pictureURLList.add(json.getString("ImgUrl"))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            val findingListAdapter = MyListAdapterFind(this, listString, pictureURLList)
            list.adapter = findingListAdapter
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

    fun receive(view: View) {

        var json: JSONObject
        val findingListAdapter: MyListAdapterFind

        api = FindingProductAPI()
        api.barcode = "K_Bar_Code=" + kBarCode.editableText.toString()
        api.start()

        while (api.run) {
        }

        if (!api.status) {
            Toast.makeText(this, api.response, Toast.LENGTH_LONG).show()
            return
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        if (api.similar.length() > 0) {
            listString.clear()
            pictureURLList.clear()
            for (i in 0 until api.similar.length()) {
                try {
                    json = api.similar.getJSONObject(i)
                    listString.add(
                        """
                    سایز: ${json.getString("Size")}
                    
                    رنگ: ${json.getString("Color")}
                    """.trimIndent()
                    )
                    pictureURLList.add(json.getString("ImgUrl"))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            findingListAdapter = MyListAdapterFind(this, listString, pictureURLList)
            list.adapter = findingListAdapter
            return
        }

        api = FindingProductAPI()
        api.barcode = "kbarcode=" + kBarCode.editableText.toString()
        api.start()
        while (api.run) {
        }
        if (!api.status) {
            Toast.makeText(this, api.response, Toast.LENGTH_LONG).show()
            return
        }
        listString.clear()
        pictureURLList.clear()

        try {
            kBarCode.setText(api.similar.getJSONObject(0).getString("K_Bar_Code"))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        for (i in 0 until api.similar.length()) {
            try {
                json = api.similar.getJSONObject(i)
                listString.add(
                    """
                سایز: ${json.getString("Size")}
                
                رنگ: ${json.getString("Color")}
                """.trimIndent()
                )
                pictureURLList.add(json.getString("ImgUrl"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        findingListAdapter = MyListAdapterFind(this, listString, pictureURLList)
        list.adapter = findingListAdapter
    }

    private fun back() {
        close()
        finish()
    }

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
                        .padding(start = 0.dp, end = 60.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "جست و جو", textAlign = TextAlign.Center,
                    )
                }
            },
            navigationIcon = {
                Box(
                    modifier = Modifier.width(60.dp)
                ) {
                    IconButton(onClick = { back() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = ""
                        )
                    }
                }
            }
        )
    }

    @Composable
    fun Content() {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = MaterialTheme.shapes.medium
                    )
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ProductCodeTextField()
                IconButton(onClick = { receive(View(this@FindingProductActivity)) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_search_24),
                        contentDescription = ""
                    )
                }
            }

            /*LazyColumn(modifier = Modifier.padding(top = 2.dp)) {

                items(uiList.size) { i ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .background(
                                color = if (uiList[i].KBarCode !in signedProductCodes) {
                                    MaterialTheme.colors.onPrimary
                                } else {
                                    MaterialTheme.colors.primary
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {

                                },
                                onLongClick = {
                                    if (uiList[i].KBarCode !in signedProductCodes) {
                                        signedProductCodes.add(uiList[i].KBarCode)
                                    } else {
                                        signedProductCodes.remove(uiList[i].KBarCode)
                                    }
                                    uiList = filterResult(conflictResultProducts)
                                },
                            ),
                    ) {
                        Column {
                            Text(
                                text = uiList[i].name,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Brown)
                            )

                            Text(
                                text = uiList[i].KBarCode,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.DarkGreen)
                            )

                            Text(
                                text = uiList[i].result,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                                color = colorResource(id = R.color.Goldenrod)
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
            }*/
        }
    }

    @Composable
    fun ProductCodeTextField() {

        OutlinedTextField(
            value = productCode, onValueChange = {
                productCode = it
            },
            modifier = Modifier
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth(),
            label = { Text(text = "کد محصول") }
        )
    }
}