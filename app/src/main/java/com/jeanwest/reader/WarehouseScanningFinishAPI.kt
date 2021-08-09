package com.jeanwest.reader

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class WarehouseScanningFinishAPI : Thread() {

    var response = ""
    var status = false

    @Volatile
    var run = true
    var storeMojodiReviewInfoID = 0
    var depoMojodiReviewInfoID = 0

    override fun run() {
        try {
            val getCommand =
                "http://rfid-api-0-1.avakatan.ir/stock-taking/reports?StoreMojodiReviewInfo_ID=" +
                        storeMojodiReviewInfoID + "&DepoMojodiReviewInfo_ID=" + depoMojodiReviewInfoID
            val server = URL(getCommand)
            val connection = server.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", ("Bearer " + MainActivity.token))

            if (connection.responseCode == 200 || connection.responseCode == 204) {
                response = "با موفقیت ارسال شد"
                status = true
            } else {
                response =
                    connection.responseCode.toString() + "Error: " + connection.responseMessage
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        run = false
    }
}