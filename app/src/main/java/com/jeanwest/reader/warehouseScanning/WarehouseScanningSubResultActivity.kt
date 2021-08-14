package com.jeanwest.reader.warehouseScanning

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class WarehouseScanningSubResultActivity : AppCompatActivity() {
    lateinit var subResult: ListView
    var subStuffs = JSONArray()
    var numberOfNotStatusNotScanned = 0
    var numberOfNotStatusScanned = 0
    var j = 0
    var numberOfStatusExtras = 0
    var numberOfStatusScanned = 0
    var numberOfNotStatusAll = 0
    var numberOfStatusAll = 0
    lateinit var nextActivityIntent: Intent
    private lateinit var index: IntArray
    var titles = ArrayList<String>()
    var specs = ArrayList<String>()
    var scanned = ArrayList<String>()
    var notScanned = ArrayList<String>()
    private var all = ArrayList<String>()
    private var pictureURL = ArrayList<String>()
    lateinit var listAdapter: MyListAdapterSub
    var stuff = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading_result_sub)
        subResult = findViewById(R.id.subResultView)
        nextActivityIntent = Intent(this, WareHouseScanningFindingProduct::class.java)
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        numberOfNotStatusNotScanned = 0
        numberOfStatusExtras = 0
        numberOfStatusScanned = 0
        numberOfNotStatusScanned = 0
        numberOfNotStatusAll = 0
        numberOfStatusAll = 0
        j = 0
        titles = ArrayList()
        specs = ArrayList()
        scanned = ArrayList()
        notScanned = ArrayList()
        all = ArrayList()
        pictureURL = ArrayList()
        subStuffs = try {
            WarehouseScanningActivity.conflicts.getJSONArray(WarehouseScanningResultActivity.index)
        } catch (e: JSONException) {
            e.printStackTrace()
            return
        }
        index = IntArray(subStuffs.length() + 100)
        for (i in 0 until subStuffs.length()) {
            try {
                stuff = subStuffs.getJSONObject(i)
                if (!stuff.getBoolean("status")) {
                    numberOfNotStatusNotScanned += stuff.getInt("diffCount")
                    numberOfNotStatusScanned += stuff.getInt("handheldCount")
                    numberOfNotStatusAll += stuff.getInt("dbCount")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                return
            }
        }
        titles.add("کالاهای اسکن نشده")
        specs.add("ناموجود در انبار، موجود در سرور")
        notScanned.add("تعداد اسکن نشده: $numberOfNotStatusNotScanned")
        scanned.add("تعداد اسکن شده: $numberOfNotStatusScanned")
        all.add("تعداد کل: $numberOfNotStatusAll")
        pictureURL.add("a")
        index[j] = -1
        j++
        for (i in 0 until subStuffs.length()) {
            try {
                stuff = subStuffs.getJSONObject(i)
                if (!stuff.getBoolean("status") && stuff.getInt("diffCount") != 0) {
                    index[j] = i
                    j++
                    titles.add(stuff.getString("productName"))
                    specs.add(
                        """
                            کد محصول: ${stuff.getString("K_Bar_Code")}
                            بارکد: ${stuff.getString("KBarCode")}
                            """.trimIndent()
                    )
                    notScanned.add("تعداد اسکن نشده: " + stuff.getString("diffCount"))
                    scanned.add("تعداد اسکن شده: " + stuff.getString("handheldCount"))
                    all.add("تعداد کل: " + stuff.getString("dbCount"))
                    pictureURL.add(stuff.getString("ImgUrl"))
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        for (i in 0 until subStuffs.length()) {
            try {
                stuff = subStuffs.getJSONObject(i)
                if (stuff.getBoolean("status")) {
                    numberOfStatusExtras += stuff.getInt("diffCount")
                    numberOfStatusScanned += stuff.getInt("handheldCount")
                    numberOfStatusAll += stuff.getInt("dbCount")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        titles.add("کالاهای اضافی")
        specs.add("ناموجود در سرور، موجود در انبار")
        notScanned.add("تعداد اسکن نشده: $numberOfStatusExtras")
        scanned.add("تعداد اسکن شده: $numberOfStatusScanned")
        all.add("تعداد کل: $numberOfStatusAll")
        pictureURL.add("a")
        index[j] = -1
        j++
        for (i in 0 until subStuffs.length()) {
            try {
                stuff = subStuffs.getJSONObject(i)
                if (stuff.getBoolean("status")) {
                    index[j] = i
                    j++
                    titles.add(stuff.getString("productName"))
                    specs.add(
                        """
                            کد محصول: ${stuff.getString("K_Bar_Code")}
                            بارکد: ${stuff.getString("KBarCode")}
                            """.trimIndent()
                    )
                    notScanned.add("تعداد اضافی: " + stuff.getString("diffCount"))
                    scanned.add("تعداد اسکن شده: " + stuff.getString("handheldCount"))
                    all.add("تعداد کل: " + stuff.getString("dbCount"))
                    pictureURL.add(stuff.getString("ImgUrl"))
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        listAdapter =
            MyListAdapterSub(this, titles, specs, scanned, all, notScanned, pictureURL)
        subResult.adapter = listAdapter
        subResult.setSelection(indexNumberSub)
        subResult.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            if (index[i] != -1) {
                indexNumberSub = i
                subIndex = index[i]
                startActivity(nextActivityIntent)
            }
        }
    }

    companion object {
        var indexNumberSub = 0
        @JvmField
        var subIndex = 0
    }
}