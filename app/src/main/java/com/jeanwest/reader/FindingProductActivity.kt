package com.jeanwest.reader

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
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class FindingProductActivity : AppCompatActivity(), IBarcodeResult {

    private lateinit var barcode2D: Barcode2D
    lateinit var result: Toast
    lateinit var list: ListView
    var listString = ArrayList<String>()
    var pictureURLList = ArrayList<String>()
    lateinit var kBarCode: EditText
    lateinit var nextActivityIntent: Intent
    private var product : JSONObject = JSONObject()
    lateinit var api: FindingProductAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finding)
        barcode2D = Barcode2D(this)
        result = Toast.makeText(this, "", Toast.LENGTH_LONG)
        list = findViewById(R.id.findingListView)
        kBarCode = findViewById(R.id.K_Bar_CodeView)
        nextActivityIntent = Intent(this, FindingProductSubActivity::class.java)

        list.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            product = api.similar.getJSONObject(i);
            nextActivityIntent.putExtra("product", product.toString());
            startActivity(nextActivityIntent)
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
                result.setText(api.response)
                result.show()
                return
            }

            val view = this.currentFocus
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view!!.windowToken, 0)
            listString.clear()
            pictureURLList.clear()
            try {
                kBarCode.setText(api.similar.getJSONObject(1).getString("K_Bar_Code"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            for (i in 0 until api.similar.length()) {
                try {
                    json = api.similar.getJSONObject(i)
                    listString.add("""
                    سایز: ${json.getString("Size")}
                    
                    رنگ: ${json.getString("Color")}
                    """.trimIndent())
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
            close()
            finish()
        }
        return true
    }

    fun start() {
        barcode2D.startScan(this)
    }

    fun stop() {
        barcode2D.stopScan(this)
    }

    fun open() {
        barcode2D.open(this, this)
    }

    fun close() {
        barcode2D.stopScan(this)
        barcode2D.close(this)
    }

    fun receive(view: View) {

        var json: JSONObject
        val findingListAdapter: MyListAdapterFind

        api = FindingProductAPI()
        api.barcode = "K_Bar_Code=" + kBarCode.editableText.toString()
        api.start()

        while (api.run) {}

        if (!api.status) {
            result.setText(api.response)
            result.show()
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
                    listString.add("""
                    سایز: ${json.getString("Size")}
                    
                    رنگ: ${json.getString("Color")}
                    """.trimIndent())
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
            result.setText(api.response)
            result.show()
            return
        }
        listString.clear()
        pictureURLList.clear()
        for (i in 0 until api.similar.length()) {
            try {
                json = api.similar.getJSONObject(i)
                listString.add("""
                سایز: ${json.getString("Size")}
                
                رنگ: ${json.getString("Color")}
                """.trimIndent())
                pictureURLList.add(json.getString("ImgUrl"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        findingListAdapter = MyListAdapterFind(this, listString, pictureURLList)
        list.adapter = findingListAdapter
    }
}