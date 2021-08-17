package com.jeanwest.reader

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UpdateAPI : Thread() {
    var response: String? = null
    var finished = false
    var serverAddress = "http://rfid-api-0-1.avakatan.ir/apk/app-debug.apk"
    var context: Context? = null
    var outputFile: File? = null
    override fun run() {
        try {
            finished = false
            val server = URL(serverAddress)
            val connection = server.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", ("Bearer " + MainActivity.token))

            if (connection.responseCode == 200) {
                outputFile = File(Environment.getExternalStorageDirectory().path, "app.apk")
                val fos = FileOutputStream(outputFile)
                val `is` = connection.inputStream
                val buffer = ByteArray(1024)
                var len1 = 0
                while (`is`.read(buffer).also { len1 = it } != -1) {
                    fos.write(buffer, 0, len1)
                }
                fos.close()
                `is`.close() //till here, it works fine - .apk is download to my sdcard in download file
                response = "ok"
            } else {
                response =
                    connection.responseCode.toString() + " error: " + connection.responseMessage
            }

            connection.disconnect()
            sleep(1000)
        } catch (e: IOException) {
            e.printStackTrace()
            response = e.toString()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            response = e.toString()
        }
        finished = true
    }
}