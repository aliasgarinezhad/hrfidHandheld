package com.jeanwest.reader

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class APIReadingInformation : Thread() {
    var departmentInfoID = 68
    var wareHouseID = 1706
    var response: String = ""
    var status = false

    @Volatile
    var run = true
    var getCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/informations/v2"
    var context: Context? = null
    override fun run() {
        try {
            val body = JSONObject()
            body.put("DepartmentInfo_ID", departmentInfoID)
            body.put("WareHouseTypes_ID", wareHouseID)
            body.put("CreateUserID", System.currentTimeMillis())
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.UK)
            val formattedDate = sdf.format(Date())
            body.put("ReviewControlDate", formattedDate)
            body.put("StartDate", formattedDate)
            val server = URL(getCommand)
            val connection = server.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doInput = true
            val out = OutputStreamWriter(connection.outputStream)
            out.write(body.toString())
            out.close()
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val receive = reader.readLine()
                val json = JSONObject(receive)
                response = json.getString("MojodiReviewInfo_ID")
                status = true
            } else {
                response =
                    connection.responseCode.toString() + "Error: " + connection.responseMessage
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