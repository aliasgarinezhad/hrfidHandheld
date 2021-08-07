package com.jeanwest.reader

import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class GetProductEPCAPI : Thread() {
    @JvmField
    var response: String? = null
    @JvmField
    var status = false

    @Volatile @JvmField
    var run = true
    var json = JSONObject()
    @JvmField
    var rfidCode = ""
    @JvmField
    var primaryCode = ""
    @JvmField
    var id = ""

    override fun run() {
        try {
            val url = "http://rfid-api-0-1.avakatan.ir/stock-taking/$id/epcs?BarcodeMain_ID=$primaryCode&RFID=$rfidCode"
            val server = URL(url)
            val connection = server.openConnection() as HttpURLConnection
            if (connection.responseCode == 200) {
                val input: InputStream = BufferedInputStream(connection.inputStream)
                val isr = InputStreamReader(input, "UTF-8")
                val reader = BufferedReader(isr)
                val receive = reader.readLine()
                json = JSONObject(receive)
                response = ""

                for (g in 0 until json.length()) {
                    response += json.getString(g.toString())
                }

                status = true
            } else {
                response = connection.responseCode.toString() + "Error: " + connection.responseMessage
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        run = false
    }
}