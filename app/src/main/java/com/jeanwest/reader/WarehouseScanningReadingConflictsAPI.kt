package com.jeanwest.reader

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class WarehouseScanningReadingConflictsAPI : Thread() {
    var response: String = ""

    @JvmField
    var status = false

    @Volatile
    @JvmField
    var run = true

    @JvmField
    var conflicts = JSONObject()

    @JvmField
    var id = 0

    override fun run() {

        try {

            val getCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/$id/conflicts/v2"
            val server = URL(getCommand)
            val connection = server.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", ("Bearer " + MainActivity.token))

            if (connection.responseCode == 200) {
                val input: InputStream = BufferedInputStream(connection.inputStream)
                val isr = InputStreamReader(input, StandardCharsets.UTF_8)
                val reader = BufferedReader(isr)
                val receive = reader.readLine()
                val json = JSONObject(receive)
                conflicts = json.getJSONObject("conflicts")
                status = true
            } else {
                response =
                    connection.responseCode.toString() + " error: " + connection.responseMessage
                status = false
            }
            connection.disconnect()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        run = false
    }
}
