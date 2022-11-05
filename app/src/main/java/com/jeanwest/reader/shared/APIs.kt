package com.jeanwest.reader.shared

import android.util.Log
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.toMutableStateList
import com.android.volley.*
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.jeanwest.reader.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*


fun getProductsV4(
    queue: RequestQueue,
    state: SnackbarHostState,
    epcs: MutableList<String> = mutableListOf(),
    barcodes: MutableList<String> = mutableListOf(),
    onSuccess: (epcs: MutableList<Product>, barcodes: MutableList<Product>, invalidEpcs: JSONArray, invalidBarcodes: JSONArray) -> Unit,
    onError: (it: VolleyError?) -> Unit,
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
        val invalidBarcodesJsonArray = it.getJSONArray("invalidBarCodes")
        val invalidEpcsJsonArray = it.getJSONArray("invalidEpcs")

        if (invalidEpcsJsonArray.length() > 0) {
            CoroutineScope(Dispatchers.Main).launch {
                state.showSnackbar(
                    "مشخصات برخی ای پی سی ها یافت نشد",
                    null,
                    SnackbarDuration.Long
                )
            }
        }
        if (invalidBarcodesJsonArray.length() > 0) {
            CoroutineScope(Dispatchers.Main).launch {
                state.showSnackbar(
                    "مشخصات بارکد " + invalidBarcodesJsonArray[0] + " یافت نشد.",
                    null,
                    SnackbarDuration.Long
                )
            }
        }

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
                storeNumber = epcsJsonArray.getJSONObject(i).getInt("storeCount"),
                wareHouseNumber = epcsJsonArray.getJSONObject(i).getInt("depoCount"),
                scannedEPCs = mutableListOf(epcsJsonArray.getJSONObject(i).getString("epc")),
                kName = epcsJsonArray.getJSONObject(i).getString("K_Name"),
                countedStoreNumber = epcsJsonArray.getJSONObject(i).getInt("diffRidStoreCount"),
                countedWarehouseNumber = epcsJsonArray.getJSONObject(i)
                    .getInt("diffRfidDepoCount"),
                brandName = epcsJsonArray.getJSONObject(i).getString("BrandGroupName"),
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
                storeNumber = barcodesJsonArray.getJSONObject(i).getInt("storeCount"),
                scannedBarcode = barcodesJsonArray.getJSONObject(i).getString("kbarcode"),
                kName = barcodesJsonArray.getJSONObject(i).getString("K_Name"),
                brandName = barcodesJsonArray.getJSONObject(i).getString("BrandGroupName"),
                countedStoreNumber = barcodesJsonArray.getJSONObject(i)
                    .getInt("diffRidStoreCount"),
                countedWarehouseNumber = barcodesJsonArray.getJSONObject(i)
                    .getInt("diffRfidDepoCount"),
            )
            responseBarcodes.add(product)
        }
        onSuccess(responseEpcs, responseBarcodes, invalidEpcsJsonArray, invalidBarcodesJsonArray)

    }, {
        apiErrorProcess(state, it)
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

fun createLocalStockDraft(
    queue: RequestQueue,
    state: SnackbarHostState,
    products: MutableList<Product> = mutableListOf(),
    desc: String,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {

    if (products.filter { it1 ->
            it1.scannedNumber > 0
        }.toMutableStateList().isEmpty()) {
        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "کالایی برای ارسال وجود ندارد",
                null,
                SnackbarDuration.Long
            )
        }
        return
    }

    val url = "https://rfid-api.avakatan.ir/stock-draft/refill/v2"
    val request = object : JsonObjectRequest(Method.POST, url, null, {

        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "حواله با موفقیت ایجاد شد.",
                null,
                SnackbarDuration.Long
            )
        }
        onSuccess()

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] =
                "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {

            val body = JSONObject()
            val productsJsonArray = JSONArray()

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            Log.e("time", sdf.format(Date()))

            products.forEach {
                repeat(it.scannedBarcodeNumber + it.scannedEPCNumber) { _ ->
                    val productJson = JSONObject()
                    productJson.put("BarcodeMain_ID", it.primaryKey)
                    productJson.put("kbarcode", it.KBarCode)
                    productJson.put("K_Name", it.kName)
                    productsJsonArray.put(productJson)
                }
            }

            body.put("desc", desc)
            body.put("createDate", sdf.format(Date()))
            body.put("products", productsJsonArray)

            return body.toString().toByteArray()
        }
    }

    val apiTimeout = 30000
    request.retryPolicy = DefaultRetryPolicy(
        apiTimeout,
        0,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    )

    queue.add(request)
}

fun syncServerToLocalWarehouse(
    queue: RequestQueue,
    state: SnackbarHostState,
) {

    val url = "https://rfid-api.avakatan.ir/department-infos/sync"
    val request = object : StringRequest(Method.GET, url, {

        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "ارسال و دریافت با موفقیت انجام شد",
                null,
                SnackbarDuration.Long
            )
        }

    }, {
        apiErrorProcess(state, it)
    }) {

        override fun getHeaders(): MutableMap<String, String> {
            val header = mutableMapOf<String, String>()
            header["accept"] = "application/json"
            header["Content-Type"] = "application/json"
            header["Authorization"] = "Bearer ${MainActivity.token}"
            return header
        }
    }

    queue.add(request)
}

fun getProductsSimilar(
    queue: RequestQueue,
    state: SnackbarHostState,
    storeCode: Int,
    barcode: String,
    onSuccess: (products: MutableList<Product>) -> Unit,
    onError: () -> Unit
) {
    val url =
        "https://rfid-api.avakatan.ir/products/similars?DepartmentInfo_ID=$storeCode&kbarcode=$barcode"

    val request = JsonObjectRequest(url, {

        val products = mutableListOf<Product>()
        val productsJsonArray = it.getJSONArray("products")
        if (productsJsonArray.length() > 0) {

            for (i in 0 until productsJsonArray.length()) {

                val json = productsJsonArray.getJSONObject(i)

                products.add(
                    Product(
                        name = json.getString("productName"),
                        KBarCode = json.getString("KBarCode"),
                        imageUrl = json.getString("ImgUrl"),
                        storeNumber = json.getInt("dbCountStore"),
                        wareHouseNumber = json.getInt("dbCountDepo"),
                        productCode = json.getString("K_Bar_Code"),
                        size = json.getString("Size"),
                        color = json.getString("Color"),
                        originalPrice = json.getString("OrigPrice"),
                        salePrice = json.getString("SalePrice"),
                        primaryKey = json.getLong("BarcodeMain_ID"),
                        rfidKey = json.getLong("RFID"),
                        kName = json.getString("K_Name"),
                    )
                )
            }
            onSuccess(products)
        } else {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "این کد فرعی هیچ موجودی در فروشگاه شما ندارد.",
                    null,
                    SnackbarDuration.Long
                )
            }
        }
    }, {
        apiErrorProcess(state, it)
        onError()
    })
    queue.add(request)
}

fun getRefill(
    queue: RequestQueue,
    state: SnackbarHostState,
    onSuccess: (barcodes: MutableList<String>) -> Unit,
    onError: () -> Unit
) {

    val url = "https://rfid-api.avakatan.ir/refill"

    val request = object : JsonArrayRequest(Method.GET, url, null, {

        val barcodes = mutableListOf<String>()

        for (i in 0 until it.length()) {

            barcodes.add(it.getJSONObject(i).getString("KBarCode"))
        }

        if (barcodes.isEmpty()) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "خطی صفر است!",
                    null,
                    SnackbarDuration.Long
                )
            }
        }
        onSuccess(barcodes)

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + MainActivity.token
            return params
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

fun getManualRefill(
    queue: RequestQueue,
    state: SnackbarHostState,
    onSuccess: (barcodes: MutableList<String>) -> Unit,
    onError: () -> Unit
) {

    val url = "https://rfid-api.avakatan.ir/charge-requests"

    val request = object : JsonArrayRequest(Method.GET, url, null, {

        val barcodes = mutableListOf<String>()

        for (i in 0 until it.length()) {

            barcodes.add(it.getJSONObject(i).getString("KBarCode"))
        }

        if (barcodes.isEmpty()) {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "خطی دستی صفر است!",
                    null,
                    SnackbarDuration.Long
                )
            }
        }
        onSuccess(barcodes)

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + MainActivity.token
            return params
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

fun getStockDraftDetails(
    queue: RequestQueue,
    state: SnackbarHostState,
    code: String,
    onSuccess: (draftProperties: DraftProperties) -> Unit,
    onError: () -> Unit,
) {

    val url = "https://rfid-api.avakatan.ir/stock-draft/$code/details"
    val request = object : JsonArrayRequest(url, fun(it) {

        val draftBarcodes = mutableListOf<String>()
        val draftEpcs = mutableListOf<String>()

        var numberOfItems = 0
        for (i in 0 until it.length()) {
            numberOfItems += it.getJSONObject(i).getInt("Qty")

            repeat(it.getJSONObject(i).getInt("Qty")) { _ ->
                draftBarcodes.add(it.getJSONObject(i).getString("kbarcode"))
            }
            draftEpcs.add(it.getJSONObject(i).getString("EPC"))
        }

        val draftProperties = DraftProperties(
            number = code.toLong(),
            numberOfItems = numberOfItems,
            barcodeTable = draftBarcodes,
            epcTable = draftEpcs,
        )

        onSuccess(draftProperties)

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + MainActivity.token
            return params
        }
    }
    queue.add(request)
}

fun editStockDraft(
    queue: RequestQueue,
    state: SnackbarHostState,
    code: Long,
    products: MutableList<Product>,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {

    val url = "https://rfid-api.avakatan.ir/stock-draft/$code"

    val request = object : StringRequest(Method.PATCH, url, {

        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "حواله " + code + "با موفقیت تایید شد.",
                null,
                SnackbarDuration.Long
            )
        }
        onSuccess()
    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {
            val body = JSONArray()

            products.forEach {

                val item = JSONObject()
                item.put("BarcodeMain_ID", it.primaryKey)
                item.put("epcs", JSONArray(it.scannedEPCs))
                body.put(item)
            }

            Log.e("error", body.toString())

            return body.toString().toByteArray()
        }
    }
    queue.add(request)
}

fun confirmStockDraft(
    queue: RequestQueue,
    state: SnackbarHostState,
    code: Long,
    products: MutableList<Product>,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {

    val url =
        "https://rfid-api.avakatan.ir/stock-draft/$code/confirm-via-erp"
    val request = object : JsonObjectRequest(Method.POST, url, null, {

        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                it.getString("Message"),
                null,
                SnackbarDuration.Long
            )
        }
        onSuccess()

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {
            val body = JSONObject()
            val productsJsonArray = JSONArray()

            products.forEach {
                repeat(it.scannedBarcodeNumber + it.scannedEPCNumber) { _ ->
                    val productJson = JSONObject()
                    productJson.put("BarcodeMain_ID", it.primaryKey)
                    productJson.put("kbarcode", it.KBarCode)
                    productJson.put("K_Name", it.kName)
                    productsJsonArray.put(productJson)
                }
            }
            body.put("kbarcodes", productsJsonArray)

            return body.toString().toByteArray()
        }
    }
    queue.add(request)
}

fun getWarehousesLists(
    queue: RequestQueue,
    state: SnackbarHostState,
    onSuccess: (destinations: MutableMap<String, Int>) -> Unit,
    onError: () -> Unit,
    token: String = ""
) {

    val url = "https://rfid-api.avakatan.ir/department-infos"
    val request = object : JsonArrayRequest(Method.GET, url, null, {

        val destinations = mutableMapOf<String, Int>()

        for (i in 0 until it.length()) {

            try {
                val warehouses = it.getJSONObject(i).getJSONArray("wareHouses")
                for (j in 0 until warehouses.length()) {
                    val warehouse = warehouses.getJSONObject(j)
                    destinations[warehouse.getString("WareHouseTitle")] =
                        warehouse.getInt("WareHouse_ID")
                }
            } catch (e: Exception) {

            }
        }

        onSuccess(destinations)

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + token.ifEmpty { MainActivity.token }
            return params
        }
    }

    queue.add(request)
}

fun createStockDraft(
    queue: RequestQueue,
    state: SnackbarHostState,
    products: MutableList<Product> = mutableListOf(),
    desc: String,
    source: Int,
    destination: Int,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {

    val url = "https://rfid-api.avakatan.ir/stock-draft"
    val request = object : JsonObjectRequest(Method.POST, url, null, {

        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "حواله با موفقیت ایجاد شد.",
                null,
                SnackbarDuration.Long
            )
        }
        onSuccess()
    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] =
                "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {

            val body = JSONObject()
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            val barcodeArray = JSONArray()
            val epcArray = JSONArray()

            products.forEach {
                repeat(it.scannedEPCNumber) { i ->
                    val productJson = JSONObject()
                    productJson.put("BarcodeMain_ID", it.primaryKey)
                    productJson.put("kbarcode", it.KBarCode)
                    productJson.put("K_Name", it.kName)
                    productJson.put("epc", it.scannedEPCs[i])
                    epcArray.put(productJson)
                }
                repeat(it.scannedBarcodeNumber) { _ ->
                    val productJson = JSONObject()
                    productJson.put("BarcodeMain_ID", it.primaryKey)
                    productJson.put("kbarcode", it.KBarCode)
                    productJson.put("K_Name", it.kName)
                    barcodeArray.put(productJson)
                }
            }

            body.put("desc", desc)
            body.put("createDate", sdf.format(Date()))
            body.put("fromWarehouseId", source)
            body.put("toWarehouseId", destination)
            body.put("kbarcodes", barcodeArray)
            body.put("epcs", epcArray)

            return body.toString().toByteArray()
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

fun sendBrokenEpcs(
    queue: RequestQueue,
    state: SnackbarHostState,
    code: Long,
    inputEpcs: MutableList<String>,
    scannedEpcs: MutableList<String>,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {

    val url =
        "http://rfid-api.avakatan.ir/stock-draft/not-found-epc"
    val request = object : JsonObjectRequest(Method.POST, url, null, {
        onSuccess()
    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] = "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {
            val body = JSONObject()
            val brokenEpcs = JSONArray()

            inputEpcs.forEach {
                if (it !in scannedEpcs) {
                    brokenEpcs.put(it)
                }
            }

            body.put("EPCs", brokenEpcs)
            body.put("StockDraftId", code)

            return body.toString().toByteArray()
        }
    }
    queue.add(request)
}

fun getWarehouseProducts(
    queue: RequestQueue,
    state: SnackbarHostState,
    warehouseCode: String,
    onSuccess: (isInProgress: Boolean, barcodes: MutableList<String>) -> Unit,
    onError: () -> Unit,
) {

    val url = "http://rfid-api.avakatan.ir/products/$warehouseCode"

    val request = object : JsonObjectRequest(url, {

        val products = it.getJSONArray("products")
        val mojodiReviewPackage = it.getJSONObject("mojodiReviewPackage")

        val isInProgress = mojodiReviewPackage.getBoolean("IsInProgress")
        val barcodes = mutableListOf<String>()

        barcodes.clear()
        for (i in 0 until products.length()) {
            barcodes.add(products.getJSONObject(i).getString("KBarCode"))
        }
        onSuccess(isInProgress, barcodes)
    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val header = mutableMapOf<String, String>()
            header["Content-Type"] = "application/json;charset=UTF-8"
            header["Authorization"] = "Bearer " + MainActivity.token
            return header
        }
    }

    val apiTimeout = 60000
    request.retryPolicy = DefaultRetryPolicy(
        apiTimeout,
        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    )

    queue.add(request)
}

fun operatorLogin(
    queue: RequestQueue,
    state: SnackbarHostState,
    username: String,
    password: String,
    onSuccess: (token: String) -> Unit,
    onError: () -> Unit,
) {

    val url = "https://rfid-api.avakatan.ir/login/operators"
    val request = object : JsonObjectRequest(Method.POST, url, null, fun(it) {
        val token = it.getString("accessToken")
        onSuccess(token)
    }, {
        apiErrorProcess(state, it)
        onError()
    }) {

        override fun getHeaders(): MutableMap<String, String> {
            val header = mutableMapOf<String, String>()
            header["accept"] = "application/json"
            header["Content-Type"] = "application/json"
            return header
        }

        override fun getBody(): ByteArray {
            val body = JSONObject()
            body.put("username", username)
            body.put("password", password)
            return body.toString().toByteArray()
        }
    }

    queue.add(request)
}

fun registerDevice(
    queue: RequestQueue,
    state: SnackbarHostState,
    token: String,
    deviceSerialNumber: String,
    onSuccess: (deviceId: String, iotToken: String) -> Unit,
    onError: () -> Unit
) {
    val url = "https://rfid-api.avakatan.ir/devices/handheld"
    val request = object : JsonObjectRequest(Method.POST, url, null, {
        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "دستگاه با موفقیت رجیستر شد",
                null,
                SnackbarDuration.Long
            )
        }
        val deviceId = it.getString("deviceId")
        val iotToken = it.getJSONObject("authentication").getJSONObject("symmetricKey")
            .getString("primaryKey")
        onSuccess(deviceId, iotToken)

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {

        override fun getHeaders(): MutableMap<String, String> {
            val header = mutableMapOf<String, String>()
            header["accept"] = "application/json"
            header["Content-Type"] = "application/json"
            header["Authorization"] = "Bearer $token"
            return header
        }

        override fun getBody(): ByteArray {
            val body = JSONObject()
            body.put("serialNumber", deviceSerialNumber)
            return body.toString().toByteArray()
        }
    }

    val apiTimeout = 30000
    request.retryPolicy = DefaultRetryPolicy(
        apiTimeout,
        0,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    )

    queue.add(request)
}

fun userLogin(
    queue: RequestQueue,
    state: SnackbarHostState,
    username: String,
    password: String,
    onSuccess: (token: String, fullName: String, assignedLocation: Int, assignedWarehouse: Int, warehouses: MutableMap<String, String>) -> Unit,
    onError: () -> Unit,
) {
    val url = "https://rfid-api.avakatan.ir/login"

    val jsonRequest = object : JsonObjectRequest(Method.POST, url, null, { response ->

        val token = response.getString("accessToken")
        val fullName = response.getString("fullName")
        val assignedLocation = response.getInt("locationCode")
        val assignedWarehouse = response.getJSONObject("location").getInt("warehouseCode")

        val warehouses = mutableMapOf<String, String>()
        val warehousesJsonArray = response.getJSONArray("warehouses")
        for (i in 0 until warehousesJsonArray.length()) {
            warehouses[warehousesJsonArray.getJSONObject(i).getString("WareHouse_ID")] =
                warehousesJsonArray.getJSONObject(i).getString("WareHouseTitle")
        }

        onSuccess(token, fullName, assignedLocation, assignedWarehouse, warehouses)

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {

        override fun getBody(): ByteArray {
            val body = JSONObject()
            body.put("username", username)
            body.put("password", password)
            return body.toString().toByteArray()
        }

        override fun getHeaders(): MutableMap<String, String> {
            val params = HashMap<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            return params
        }
    }
    queue.add(jsonRequest)
}

fun finishInventoryPackage(
    queue: RequestQueue,
    state: SnackbarHostState,
    warehouseCode: String,
    onSuccess: () -> Unit,
    onError: () -> Unit
) {

    val url = "http://rfid-api.avakatan.ir/mojodi-review/package/submit"
    val request = object : StringRequest(Method.POST, url, {
        onSuccess()
    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] =
                "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {
            val body = JSONObject()
            body.put("Warehouse_ID", warehouseCode.toInt())
            return body.toString().toByteArray()
        }
    }

    request.retryPolicy = DefaultRetryPolicy(
        5000,
        0,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    )

    queue.add(request)
}

fun saveInventoryDataGetId(
    queue: RequestQueue,
    state: SnackbarHostState,
    warehouseCode: String,
    onSuccess: (id: String) -> Unit,
    onError: () -> Unit
) {
    val url = "https://rfid-api.avakatan.ir/mojodi-review/header"
    val request = object : JsonObjectRequest(Method.POST, url, null, {

        val id = it.getString("MojodiReviewInfo_ID")
        onSuccess(id)

    }, {
        apiErrorProcess(state, it)
        onError()
    }) {
        override fun getHeaders(): MutableMap<String, String> {
            val params = mutableMapOf<String, String>()
            params["Content-Type"] = "application/json;charset=UTF-8"
            params["Authorization"] =
                "Bearer " + MainActivity.token
            return params
        }

        override fun getBody(): ByteArray {

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

            val body = JSONObject()
            body.put("desc", "انبارگردانی با RFID")
            body.put("createDate", sdf.format(Date()))
            body.put("Warehouse_ID", warehouseCode.toInt())

            return body.toString().toByteArray()
        }
    }

    val apiTimeout = 60000
    request.retryPolicy = DefaultRetryPolicy(
        apiTimeout,
        0,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    )

    queue.add(request)
}

private fun apiErrorProcess(state: SnackbarHostState, it: VolleyError?) {

    if (it is NoConnectionError) {
        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                null,
                SnackbarDuration.Long
            )
        }
    } else {

        val error = it?.networkResponse?.data?.decodeToString()?.let { it1 ->
            try {
                JSONObject(it1).getJSONObject("error").getString("message")
            } catch (e: Exception) {
                it1
            }
        } ?: "مشکلی در ارتباط با سرور به وجود آمده است."

        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                error,
                null,
                SnackbarDuration.Long
            )
        }
    }
}
