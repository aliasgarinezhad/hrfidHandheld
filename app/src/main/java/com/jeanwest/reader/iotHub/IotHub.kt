package com.jeanwest.reader.iotHub

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.preference.PreferenceManager
import com.azure.storage.blob.BlobClientBuilder
import com.jeanwest.reader.updateActivity.UpdateActivity
import com.jeanwest.reader.write.WriteRecord
import com.microsoft.azure.sdk.iot.deps.serializer.FileUploadCompletionNotification
import com.microsoft.azure.sdk.iot.deps.serializer.FileUploadSasUriRequest
import com.microsoft.azure.sdk.iot.device.*
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Device
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Property
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class IotHub : Service() {

    private var deviceId = ""
    private var iotToken = ""
    private var serial = ""
    private var serialNumber = 0L
    private var serialNumberMax = 0L
    private var tagPassword = "00000000"
    private var serialNumberMin = 0L
    private var filterNumber = 0
    private var partitionNumber = 0
    private var headerNumber = 0
    private var companyNumber = 0
    private lateinit var client: DeviceClient
    private val binder: IBinder = LocalBinder()
    private var sendLogFileSuccess = false

    private var deviceLocationCode = 0
    private var deviceLocation = ""

    inner class LocalBinder : Binder() {
        val service: IotHub
            get() = this@IotHub
    }

    private var dataCollector = object : Device() {
        override fun PropertyCall(propertyKey: String, propertyValue: Any, context: Any) {
            if (propertyKey == "appVersion") {
                appVersion = propertyValue.toString()
            }
            if (propertyKey == "epcGenerationProps") {
                val epcGenerationProps = JSONObject(propertyValue.toString())
                if (epcGenerationProps.getInt("header") != headerNumber) {
                    headerNumber = epcGenerationProps.getInt("header")
                    saveToMemory()
                }
                if (epcGenerationProps.getInt("filter") != filterNumber) {
                    filterNumber = epcGenerationProps.getInt("filter")
                    saveToMemory()
                }
                if (epcGenerationProps.getInt("partition") != partitionNumber) {
                    partitionNumber = epcGenerationProps.getInt("partition")
                    saveToMemory()
                }
                if (epcGenerationProps.getString("tagPassword") != tagPassword) {
                    tagPassword = epcGenerationProps.getString("tagPassword")
                    saveToMemory()
                }
                if (epcGenerationProps.getInt("companyName") != companyNumber) {
                    companyNumber = epcGenerationProps.getInt("companyName")
                    saveToMemory()
                }
                val tagSerialNumberRange =
                    epcGenerationProps.getJSONObject("tagSerialNumberRange")
                if (tagSerialNumberRange.getLong("min") != serialNumberMin ||
                    tagSerialNumberRange.getLong("max") != serialNumberMax
                ) {
                    serialNumberMin = tagSerialNumberRange.getLong("min")
                    serialNumberMax = tagSerialNumberRange.getLong("max")
                    serialNumber = serialNumberMin
                    saveToMemory()
                }
            }

            if (packageManager.getPackageInfo(packageName, 0).versionName != appVersion &&
                appVersion.isNotEmpty()
            ) {
                val intent = Intent(this@IotHub, UpdateActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }

            Log.e("error", "$propertyKey changed to $propertyValue")
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        loadMemory()
        CoroutineScope(IO).launch {
            initClient()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun sendWriteLog(writeRecords: List<WriteRecord>): Boolean {
        val workbook = XSSFWorkbook()
        val sheet: Sheet = workbook.createSheet("کالا های رایت شده")
        val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
        headerRow.createCell(0).setCellValue("barcode")
        headerRow.createCell(1).setCellValue("EPC")
        headerRow.createCell(2).setCellValue("date and time")
        headerRow.createCell(3).setCellValue("username")
        headerRow.createCell(4).setCellValue("device serial number")
        headerRow.createCell(5).setCellValue("wrote on raw tag")
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ssZ", Locale.ENGLISH)
        val dir = File(getExternalFilesDir(null), "/")
        val outFile = File(dir, "log" + sdf.format(Date()) + ".xlsx")
        for ((epc, barcode, dateAndTime, username, deviceSerialNumber, wroteOnRawTag) in writeRecords) {
            val row = sheet.createRow(sheet.physicalNumberOfRows)
            row.createCell(0).setCellValue(barcode)
            row.createCell(1).setCellValue(epc)
            row.createCell(2).setCellValue(dateAndTime)
            row.createCell(3).setCellValue(username)
            row.createCell(4).setCellValue(deviceSerialNumber)
            row.createCell(5).setCellValue(wroteOnRawTag)
            val outputStream = FileOutputStream(outFile.absolutePath)
            workbook.write(outputStream)
            outputStream.flush()
            outputStream.close()
        }
        return sendFile(outFile)
    }

    fun sendFile(outFile: File) : Boolean {

        sendLogFileSuccess = false
        val thread = Thread {
            try {
                Log.e("error", outFile.name)
                val sasUriResponse = client.getFileUploadSasUri(FileUploadSasUriRequest(outFile.name))
                Log.e("send file", "Correlation Id: " + sasUriResponse.correlationId)
                Log.e("send file", "Container name: " + sasUriResponse.containerName)
                Log.e("send file", "Blob name: " + sasUriResponse.blobName)
                Log.e("send file", "Blob Uri: " + sasUriResponse.blobUri)
                try {
                    val blobClient = BlobClientBuilder()
                        .endpoint(sasUriResponse.blobUri.toString())
                        .buildClient()
                    blobClient.uploadFromFile(outFile.absolutePath)
                    sendLogFileSuccess = true
                    val json = JSONObject()
                    sendMessage(json.toString())
                } catch (e: Exception) {
                    Log.e(
                        "error in sending file",
                        "Exception encountered while uploading file to blob: " + e.message
                    )
                    val completionNotification =
                        FileUploadCompletionNotification(sasUriResponse.correlationId, false)
                    client.completeFileUpload(completionNotification)
                    client.closeNow()
                }
                Log.e("error in sending file", "Successfully uploaded file to Azure Storage.")
                val completionNotification =
                    FileUploadCompletionNotification(sasUriResponse.correlationId, true)
                client.completeFileUpload(completionNotification)
            } catch (e: Exception) {
                Log.e(
                    "error in sending file",
                    "On exception, shutting down Cause: ${e.cause} ERROR: ${e.message}$e"
                )
            }
        }
        thread.start()
        thread.join()
        Log.e("is sending file successfully", sendLogFileSuccess.toString())
        return sendLogFileSuccess
    }

    private fun loadMemory() {
        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        serialNumber = memory.getLong("value", -1L)
        serialNumberMax = memory.getLong("max", -1L)
        serialNumberMin = memory.getLong("min", -1L)
        headerNumber = memory.getInt("header", -1)
        filterNumber = memory.getInt("filter", -1)
        partitionNumber = memory.getInt("partition", -1)
        companyNumber = memory.getInt("company", -1)
        tagPassword = memory.getString("password", "") ?: ""
        deviceId = memory.getString("deviceId", "") ?: ""
        iotToken = memory.getString("iotToken", "") ?: ""
        serial = memory.getString("deviceSerialNumber", "") ?: ""
        deviceLocationCode = memory.getInt("deviceLocationCode", 0)
        deviceLocation = memory.getString("deviceLocation", "") ?: ""
    }

    private fun saveToMemory() {
        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val memoryEditor = memory.edit()
        memoryEditor.putLong("value", serialNumber)
        memoryEditor.putLong("max", serialNumberMax)
        memoryEditor.putLong("min", serialNumberMin)
        memoryEditor.putInt("header", headerNumber)
        memoryEditor.putInt("filter", filterNumber)
        memoryEditor.putInt("partition", partitionNumber)
        memoryEditor.putInt("company", companyNumber)
        memoryEditor.putLong("counterModified", 0L)
        memoryEditor.putString("password", tagPassword)
        memoryEditor.putString("deviceId", deviceId)
        memoryEditor.putString("iotToken", iotToken)
        memoryEditor.putString("deviceSerialNumber", serial)
        memoryEditor.apply()
    }

    private fun initClient() {

        if(deviceId.isEmpty() || iotToken.isEmpty()) {
            return
        }

        val connString = "HostName=rfid-frce.azure-devices.net;DeviceId=" + deviceId +
                ";SharedAccessKey=" + iotToken
        client = DeviceClient(connString, IotHubClientProtocol.MQTT)
        try {
            client.open()
            client.startDeviceTwin(DeviceTwinStatusCallBack(), null, dataCollector, null)
            val location = JSONObject()
            location.put("deviceLocationCode", deviceLocationCode)
            location.put("deviceLocation", deviceLocation)
            dataCollector.setReportedProp(Property("connectivityType", null))
            dataCollector.setReportedProp(
                Property(
                    "installedAppVersion", packageManager.getPackageInfo(
                        packageName, 0
                    ).versionName
                )
            )
            dataCollector.setReportedProp(Property("Serial", serial))
            dataCollector.setReportedProp(Property("nextTagSerialNumber", serialNumber))
            dataCollector.setReportedProp(Property("location", location))
            dataCollector.setReportedProp(Property("username", ""))
            client.sendReportedProperties(dataCollector.reportedProp)
        } catch (e: Exception) {
            Log.e(
                "error in sending file",
                "On exception, shutting down Cause: ${e.cause} ${e.message}"
            )
            dataCollector.clean()
            client.closeNow()
            Log.e("error in sending file", "Shutting down...")
        }
    }

    private fun sendMessage(message: String) {
        val sendMessage = Message(message)
        sendMessage.setProperty("message-type", "writeTagsFileUploadNotification")
        sendMessage.messageId = UUID.randomUUID().toString()
        sendMessage.setContentTypeFinal("application/json")
        sendMessage.contentEncoding = "utf-8"
        println("Message Sent: $message")
        val eventCallback = EventCallback()
        client.sendEventAsync(sendMessage, eventCallback, 0)
    }

    private class DeviceTwinStatusCallBack : IotHubEventCallback {
        override fun execute(responseStatus: IotHubStatusCode?, callbackContext: Any?) {
            Log.e(
                "error",
                "IoT Hub responded to device twin operation with status " + responseStatus?.name
            )
        }
    }

    internal class EventCallback : IotHubEventCallback {
        override fun execute(responseStatus: IotHubStatusCode?, callbackContext: Any?) {
            if (responseStatus == IotHubStatusCode.OK || responseStatus == IotHubStatusCode.OK_EMPTY) {
                Log.e("error", "event call back received ok")
            } else {
                Log.e("error", responseStatus.toString())
            }
        }
    }

    companion object {
        var appVersion = ""
    }
}