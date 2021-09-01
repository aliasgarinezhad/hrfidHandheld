package com.jeanwest.reader.confirm

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.R
import kotlinx.android.synthetic.main.activity_confirm_result.*
import org.json.JSONException
import org.json.JSONObject
import kotlin.collections.ArrayList

class ConfirmScanningResultActivity : AppCompatActivity() {
    lateinit var subResult: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_result)
        subResult = findViewById(R.id.subResultView)
        confirm_result_toolbar.setNavigationOnClickListener {
            finish()
        }
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
        val productRFIDCodes = ArrayList<String>()

        var products = ConfirmScanningActivity.conflicts.getJSONObject("epcs").getJSONArray("shortage")

        var product: JSONObject

        for (i in 0 until products.length()) {

            product = products.getJSONObject(i)
            shortageScanned += product.getInt("handheldCount")
            shortageAll += product.getInt("dbCount")
        }

        products = ConfirmScanningActivity.conflicts.getJSONObject("KBarCodes").getJSONArray("shortage")

        for (i in 0 until products.length()) {

            product = products.getJSONObject(i)
            shortageScanned += product.getInt("handheldCount")
            shortageAll += product.getInt("dbCount")
        }

        titles.add("کالاهای اسکن نشده")
        notScanned.add("تعداد اسکن نشده: " + (shortageAll-shortageScanned))
        specs.add("ناموجود در انبار، موجود در سرور")
        scanned.add("تعداد اسکن شده: $shortageScanned")
        all.add("تعداد کل: $shortageAll")
        pictureURL.add("null")
        productRFIDCodes.add("null")

        products = ConfirmScanningActivity.conflicts.getJSONObject("epcs").getJSONArray("shortage")

        for (i in 0 until products.length()) {
            try {
                product = products.getJSONObject(i)

                titles.add(product.getString("productName"))
                specs.add(
                    """
                        کد محصول: ${product.getString("K_Bar_Code")}
                        بارکد: ${product.getString("KBarCode")}
                        اسکن شده با RF
                        """.trimIndent()
                )
                notScanned.add("تعداد اسکن نشده: " +
                        (product.getInt("dbCount") - product.getInt("handheldCount")))
                scanned.add("تعداد اسکن شده: " + product.getString("handheldCount"))
                all.add("تعداد کل: " + product.getString("dbCount"))
                pictureURL.add(product.getString("ImgUrl"))
                productRFIDCodes.add(product.getString("RFID"))

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        products = ConfirmScanningActivity.conflicts.getJSONObject("KBarCodes").getJSONArray("shortage")

        for (i in 0 until products.length()) {
            try {
                product = products.getJSONObject(i)

                titles.add(product.getString("productName"))
                specs.add(
                    """
                        کد محصول: ${product.getString("K_Bar_Code")}
                        بارکد: ${product.getString("KBarCode")}
                        اسکن شده با بارکد
                        """.trimIndent()
                )
                notScanned.add("تعداد اسکن نشده: " +
                        (product.getInt("dbCount") - product.getInt("handheldCount")))
                scanned.add("تعداد اسکن شده: " + product.getString("handheldCount"))
                all.add("تعداد کل: " + product.getString("dbCount"))
                pictureURL.add(product.getString("ImgUrl"))
                productRFIDCodes.add(product.getString("RFID"))

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        products = ConfirmScanningActivity.conflicts.getJSONObject("epcs").getJSONArray("additional")

        for (i in 0 until products.length()) {
            try {
                product = products.getJSONObject(i)
                additionalScanned += product.getInt("handheldCount")
                additionalAll += product.getInt("dbCount")

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        products = ConfirmScanningActivity.conflicts.getJSONObject("KBarCodes").getJSONArray("additional")

        for (i in 0 until products.length()) {
            try {
                product = products.getJSONObject(i)
                additionalScanned += product.getInt("handheldCount")
                additionalAll += product.getInt("dbCount")

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        titles.add("کالاهای اضافی")
        specs.add("ناموجود در سرور، موجود در انبار")
        notScanned.add("تعداد اضافی: " + (additionalScanned - additionalAll))
        scanned.add("تعداد اسکن شده: $additionalScanned")
        all.add("تعداد کل: $additionalAll")
        pictureURL.add("null")
        productRFIDCodes.add("null")

        products = ConfirmScanningActivity.conflicts.getJSONObject("epcs").getJSONArray("additional")

        for (i in 0 until products.length()) {
            try {
                product = products.getJSONObject(i)

                titles.add(product.getString("productName"))
                specs.add(
                    """
                        کد محصول: ${product.getString("K_Bar_Code")}
                        بارکد: ${product.getString("KBarCode")}
                        اسکن شده با RF
                        """.trimIndent()
                )
                notScanned.add("تعداد اضافی: " +
                        (product.getInt("handheldCount") - product.getInt("dbCount")))
                scanned.add("تعداد اسکن شده: " + product.getString("handheldCount"))
                all.add("تعداد کل: " + product.getString("dbCount"))
                pictureURL.add(product.getString("ImgUrl"))
                productRFIDCodes.add(product.getString("RFID"))

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        products = ConfirmScanningActivity.conflicts.getJSONObject("KBarCodes").getJSONArray("additional")

        for (i in 0 until products.length()) {
            try {
                product = products.getJSONObject(i)

                titles.add(product.getString("productName"))
                specs.add(
                    """
                        کد محصول: ${product.getString("K_Bar_Code")}
                        بارکد: ${product.getString("KBarCode")}
                        اسکن شده با بارکد
                        """.trimIndent()
                )
                notScanned.add("تعداد اضافی: " +
                        (product.getInt("handheldCount") - product.getInt("dbCount")))
                scanned.add("تعداد اسکن شده: " + product.getString("handheldCount"))
                all.add("تعداد کل: " + product.getString("dbCount"))
                pictureURL.add(product.getString("ImgUrl"))
                productRFIDCodes.add(product.getString("RFID"))

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        val listAdapter =
            MyListAdapterConfirm(this, titles, specs, scanned, all, notScanned, pictureURL)
        subResult.adapter = listAdapter

        subResult.onItemClickListener = OnItemClickListener { _, _, i, _ ->

            if(productRFIDCodes[i] != "null") {
                val nextActivityIntent = Intent(this, ConfirmScanningFindingProduct::class.java)
                nextActivityIntent.putExtra("productRFIDCode", productRFIDCodes[i])
                startActivity(nextActivityIntent)
            }
        }
    }
}