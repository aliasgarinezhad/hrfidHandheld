package com.jeanwest.reader

import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class AddProductAPI : Thread() {

    var barcode: String = ""
    var response: String = ""
    var status = false

    @Volatile
    var run = true

    override fun run() {

        try {
            var getCommand = "http://rfid-api-0-1.avakatan.ir/products/v2?KBarCode=$barcode"
            getCommand = getCommand.replace(" ", "%20")
            val server = URL(getCommand)
            val connection = server.openConnection() as HttpURLConnection
            if (connection.responseCode == 200) {
                val input: InputStream = BufferedInputStream(connection.inputStream)
                val isr = InputStreamReader(input, "UTF-8")
                val reader = BufferedReader(isr)
                val receive = reader.readLine()
                val json = JSONObject(receive)
                response = json.getString("RFID")
                status = true
            } else {
                response =
                    connection.responseCode.toString() + "Error: " + connection.responseMessage
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        run = false
    }

}