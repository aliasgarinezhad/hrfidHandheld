package com.jeanwest.reader

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import android.annotation.SuppressLint
import android.os.Bundle
import org.json.JSONException
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import java.util.ArrayList

class WarehouseScanningResultActivity : AppCompatActivity() {
    lateinit var result: ListView
    var titles = ArrayList<String>()
    var allNumber = ArrayList<String>()
    var scannedNumber = ArrayList<String>()
    var extraNumber = ArrayList<String>()
    lateinit var nextActivityIntent: Intent
    var temp = ""
    var subStuffs = JSONArray()
    var temp2 = JSONObject()
    var NotScanned = 0
    var scanned = 0
    var Extra = 0
    var sumNotScanned = 0
    var sumScanned = 0
    var sumExtra = 0
    private lateinit var listAdapter: MyListAdapter

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading_result)
        result = findViewById(R.id.readingResult)
        nextActivityIntent = Intent(this, WarehouseScanningSubResultActivity::class.java)
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        sumNotScanned = 0
        sumScanned = 0
        sumExtra = 0
        titles.clear()
        scannedNumber.clear()
        allNumber.clear()
        extraNumber.clear()

        val stuffs = WarehouseScanningActivity.conflicts.names()!!
        for (i in 0 until stuffs.length()) {
            try {
                temp = stuffs.getString(i)
                subStuffs = WarehouseScanningActivity.conflicts.getJSONArray(temp)
                titles.add(temp)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        titles = sortArray(titles)
        for (i in titles.indices) {
            try {
                temp = titles[i]
                subStuffs = WarehouseScanningActivity.conflicts.getJSONArray(temp)
                NotScanned = 0
                scanned = 0
                Extra = 0
                for (j in 0 until subStuffs.length()) {
                    temp2 = subStuffs.getJSONObject(j)
                    if (!temp2.getBoolean("status")) {
                        scanned += temp2.getInt("handheldCount")
                        NotScanned += temp2.getInt("diffCount")
                    } else {
                        scanned += temp2.getInt("dbCount")
                        Extra += temp2.getInt("diffCount")
                    }
                }
            } catch (ignored: JSONException) {
            }
            sumScanned += scanned
            sumNotScanned += NotScanned
            sumExtra += Extra
            allNumber.add(NotScanned.toString())
            extraNumber.add(Extra.toString())
            scannedNumber.add(scanned.toString())
        }
        titles.add(0, "دسته")
        allNumber.add(0, "اسکن نشده")
        scannedNumber.add(0, "اسکن شده")
        extraNumber.add(0, "اضافی")
        titles.add(1, "مجموع")
        allNumber.add(1, sumNotScanned.toString())
        scannedNumber.add(1, sumScanned.toString())
        extraNumber.add(1, sumExtra.toString())
        listAdapter = MyListAdapter(this, titles, scannedNumber, allNumber, extraNumber)
        result.adapter = listAdapter
        result.setSelection(indexNumber)
        result.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            if (i == 0 || i == 1) {
                return@OnItemClickListener
            }
            indexNumber = i
            index = titles[i]
            WarehouseScanningSubResultActivity.indexNumberSub = 0
            startActivity(nextActivityIntent)
        }
    }

    private fun sortArray(array: ArrayList<String>): ArrayList<String> {
        val outputArray = ArrayList<String>()
        var template = ""
        val comparator = charArrayOf(
            'ا',
            'ب',
            'پ',
            'ت',
            'ث',
            'ج',
            'چ',
            'ح',
            'خ',
            'د',
            'ذ',
            'ر',
            'ز',
            'ژ',
            'س',
            'ش',
            'ص',
            'ض',
            'ط',
            'ظ',
            'ع',
            'غ',
            'ف',
            'ق',
            'ك',
            'گ',
            'ل',
            'م',
            'ن',
            'و',
            'ه',
            'ی'
        )
        for (j in comparator.indices) {
            for (i in array.indices) {
                template = array[i]
                if (template.startsWith(comparator[j].toString())) {
                    outputArray.add(template)
                }
            }
        }
        return outputArray
    }

    companion object {
        @JvmField
        var index = ""
        var indexNumber = 0
    }
}