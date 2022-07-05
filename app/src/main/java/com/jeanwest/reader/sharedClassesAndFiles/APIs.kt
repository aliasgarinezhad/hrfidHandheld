package com.jeanwest.reader.sharedClassesAndFiles

import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.jeanwest.reader.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


fun getProductsV4(
    queue: RequestQueue,
    state : SnackbarHostState,
    epcs: MutableList<String> = mutableListOf(),
    barcodes: MutableList<String> = mutableListOf(),
    onSuccess: (epcs : MutableList<Product>, barcodes : MutableList<Product>) -> Unit,
    onError: (it : VolleyError?) -> Unit,
) {

    val responseEpcs = mutableListOf<Product>()
    val responseBarcodes = mutableListOf<Product>()

    if ((epcs.size + barcodes.size) == 0) {
        return
    }

    val url = "https://rfid-api.avakatan.ir/products/v4"

    val request = object : JsonObjectRequest(Method.POST, url, null, {

        val epcsJsonArray = it.getJSONArray("epcs")
        val barcodesJsonArray = it.getJSONArray("KBarCodes")

        for (i in 0 until epcsJsonArray.length()) {
            val product = Product(
                name = epcsJsonArray.getJSONObject(i).getString("productName"),
                KBarCode = epcsJsonArray.getJSONObject(i).getString("KBarCode"),
                imageUrl = epcsJsonArray.getJSONObject(i).getString("ImgUrl"),
                primaryKey = epcsJsonArray.getJSONObject(i).getLong("BarcodeMain_ID"),
                productCode = epcsJsonArray.getJSONObject(i).getString("K_Bar_Code"),
                size = epcsJsonArray.getJSONObject(i).getString("Size"),
                color = epcsJsonArray.getJSONObject(i).getString("Color"),
                originalPrice = epcsJsonArray.getJSONObject(i).getString("OrgPrice"),
                salePrice = epcsJsonArray.getJSONObject(i).getString("SalePrice"),
                rfidKey = epcsJsonArray.getJSONObject(i).getLong("RFID"),
                wareHouseNumber = epcsJsonArray.getJSONObject(i).getInt("depoCount"),
                scannedBarcode = "",
                scannedEPCs = mutableListOf(epcsJsonArray.getJSONObject(i).getString("epc")),
                kName = epcsJsonArray.getJSONObject(i).getString("K_Name"),
                scannedEPCNumber = 1,
                scannedBarcodeNumber = 0,
                rfidStoreNumber = epcsJsonArray.getJSONObject(i).getInt("diffRidStoreCount"),
                rfidWareHouseNumber = epcsJsonArray.getJSONObject(i).getInt("diffRfidDepoCount"),
            )
            responseEpcs.add(product)
        }

        for (i in 0 until barcodesJsonArray.length()) {
            val product = Product(
                name = barcodesJsonArray.getJSONObject(i).getString("productName"),
                KBarCode = barcodesJsonArray.getJSONObject(i).getString("KBarCode"),
                imageUrl = barcodesJsonArray.getJSONObject(i).getString("ImgUrl"),
                primaryKey = barcodesJsonArray.getJSONObject(i).getLong("BarcodeMain_ID"),
                productCode = barcodesJsonArray.getJSONObject(i).getString("K_Bar_Code"),
                size = barcodesJsonArray.getJSONObject(i).getString("Size"),
                color = barcodesJsonArray.getJSONObject(i).getString("Color"),
                originalPrice = barcodesJsonArray.getJSONObject(i).getString("OrgPrice"),
                salePrice = barcodesJsonArray.getJSONObject(i).getString("SalePrice"),
                rfidKey = barcodesJsonArray.getJSONObject(i).getLong("RFID"),
                wareHouseNumber = barcodesJsonArray.getJSONObject(i).getInt("depoCount"),
                scannedBarcode = barcodesJsonArray.getJSONObject(i).getString("kbarcode"),
                scannedEPCs = mutableListOf(),
                kName = barcodesJsonArray.getJSONObject(i).getString("K_Name"),
                brandName = barcodesJsonArray.getJSONObject(i).getString("BrandGroupName"),
                scannedEPCNumber = 0,
                scannedBarcodeNumber = 1,
                rfidStoreNumber = barcodesJsonArray.getJSONObject(i).getInt("diffRidStoreCount"),
                rfidWareHouseNumber = barcodesJsonArray.getJSONObject(i).getInt("diffRfidDepoCount"),
            )
            responseBarcodes.add(product)
        }

        onSuccess(responseEpcs, responseBarcodes)
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
        onError(it)
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {
            val json = JSONObject()
            val epcArray = JSONArray()

            epcs.forEach {
                epcArray.put(it)
            }

            json.put("epcs", epcArray)

            val barcodeArray = JSONArray()

            barcodes.forEach {
                barcodeArray.put(it)
            }

            json.put("KBarCodes", barcodeArray)

            return json.toString().toByteArray()
        }
    }

    val apiTimeout = 30000
    request.retryPolicy = DefaultRetryPolicy(
        apiTimeout,
        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    )

    queue.add(request)
}