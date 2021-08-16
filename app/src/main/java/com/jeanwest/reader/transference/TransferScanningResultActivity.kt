package com.jeanwest.reader.transference

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.R
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class TransferScanningResultActivity : AppCompatActivity() {
    lateinit var subResult: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_result)
        subResult = findViewById(R.id.subResultView)
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        var shortageScanned = 0
        var additionalScanned = 0
        var shortageAll = 0
        var additionalAll = 0
        val titles = ArrayList<String>()
        val specs = ArrayList<String>()
        val scanned = ArrayList<String>()
        val notScanned = ArrayList<String>()
        val all = ArrayList<String>()
        val pictureURL = ArrayList<String>()

        var stuffs = TransferScanningActivity.conflicts.getJSONArray("shortage")
        var subStuffs: JSONObject

        for (i in 0 until stuffs.length()) {

            subStuffs = stuffs.getJSONObject(i)
            shortageScanned += subStuffs.getInt("handheldCount")
            shortageAll += subStuffs.getInt("dbCount")
        }

        (shortageAll-shortageScanned)

        titles.add("کالاهای اسکن نشده")
        notScanned.add("تعداد اسکن نشده: " + (shortageAll-shortageScanned))
        specs.add("ناموجود در انبار، موجود در سرور")
        scanned.add("تعداد اسکن شده: $shortageScanned")
        all.add("تعداد کل: $shortageAll")
        pictureURL.add("a")

        for (i in 0 until stuffs.length()) {
            try {
                subStuffs = stuffs.getJSONObject(i)

                titles.add(subStuffs.getString("productName"))
                specs.add(
                    """
                        کد محصول: ${subStuffs.getString("K_Bar_Code")}
                        بارکد: ${subStuffs.getString("KBarCode")}
                        """.trimIndent()
                )
                notScanned.add("تعداد اسکن نشده: " +
                        (subStuffs.getInt("dbCount") - subStuffs.getInt("handheldCount")))
                scanned.add("تعداد اسکن شده: " + subStuffs.getString("handheldCount"))
                all.add("تعداد کل: " + subStuffs.getString("dbCount"))
                pictureURL.add(subStuffs.getString("ImgUrl"))

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        stuffs = TransferScanningActivity.conflicts.getJSONArray("additional")
        for (i in 0 until stuffs.length()) {
            try {
                subStuffs = stuffs.getJSONObject(i)
                additionalScanned += subStuffs.getInt("handheldCount")
                additionalAll += subStuffs.getInt("dbCount")

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        titles.add("کالاهای اضافی")
        specs.add("ناموجود در سرور، موجود در انبار")
        notScanned.add("تعداد اضافی: " + (additionalScanned - additionalAll))
        scanned.add("تعداد اسکن شده: $additionalScanned")
        all.add("تعداد کل: $additionalAll")
        pictureURL.add("a")

        for (i in 0 until stuffs.length()) {
            try {
                subStuffs = stuffs.getJSONObject(i)

                titles.add(subStuffs.getString("productName"))
                specs.add(
                    """
                        کد محصول: ${subStuffs.getString("K_Bar_Code")}
                        بارکد: ${subStuffs.getString("KBarCode")}
                        """.trimIndent()
                )
                notScanned.add("تعداد اضافی: " +
                        (subStuffs.getInt("handheldCount") - subStuffs.getInt("dbCount")))
                scanned.add("تعداد اسکن شده: " + subStuffs.getString("handheldCount"))
                all.add("تعداد کل: " + subStuffs.getString("dbCount"))
                pictureURL.add(subStuffs.getString("ImgUrl"))

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val listAdapter =
            MyListAdapterSub(this, titles, specs, scanned, all, notScanned, pictureURL)
        subResult.adapter = listAdapter
        subResult.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            val nextActivityIntent = Intent(this, TransferScanningFindingProduct::class.java)
            nextActivityIntent.putExtra("arrayIndex", i)
            startActivity(nextActivityIntent)
        }
    }
}