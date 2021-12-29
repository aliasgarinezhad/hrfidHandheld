package com.jeanwest.reader.iotHub;


import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.jeanwest.reader.aboutUs.AboutUsActivity;
import com.jeanwest.reader.write.WriteRecord;
import com.microsoft.azure.sdk.iot.deps.serializer.FileUploadCompletionNotification;
import com.microsoft.azure.sdk.iot.deps.serializer.FileUploadSasUriRequest;
import com.microsoft.azure.sdk.iot.deps.serializer.FileUploadSasUriResponse;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Device;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Property;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class IotHub extends Service {

    private String deviceId = "";
    private String iotToken = "";
    public static String appVersion = "";
    private String Serial = "";
    private long serialNumber;
    private long serialNumberMax;
    private String tagPassword;
    private long serialNumberMin;
    private int filterNumber;
    private int partitionNumber;
    private int headerNumber;
    private int companyNumber;
    DeviceClient client;
    private final IBinder binder = new LocalBinder();
    public boolean sendLogFileSuccess = false;

    public class LocalBinder extends Binder {
        public IotHub getService() {
            return IotHub.this;
        }
    }

    IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;

    Device dataCollector = new Device() {
        // Print details when a property value changes
        @Override
        public void PropertyCall(String propertyKey, Object propertyValue, Object context) {

            if (propertyKey.equals("appVersion")) {
                appVersion = propertyValue.toString();
                Log.e("error", propertyValue.toString());
            }
            if (propertyKey.equals("epcGenerationProps")) {
                try {
                    JSONObject epcGenerationProps = new JSONObject(propertyValue.toString());
                    if (epcGenerationProps.getInt("header") != headerNumber) {
                        headerNumber = epcGenerationProps.getInt("header");
                        Log.e("error", "header changed to " + headerNumber);
                        saveToMemory();
                    }
                    if (epcGenerationProps.getInt("filter") != filterNumber) {
                        filterNumber = epcGenerationProps.getInt("filter");
                        Log.e("error", "filter changed to " + filterNumber);
                        saveToMemory();
                    }
                    if (epcGenerationProps.getInt("partition") != partitionNumber) {
                        partitionNumber = epcGenerationProps.getInt("partition");
                        Log.e("error", "partition changed " + partitionNumber);
                        saveToMemory();
                    }
                    if (!epcGenerationProps.getString("tagPassword").equals(tagPassword)) {
                        tagPassword = epcGenerationProps.getString("tagPassword");
                        Log.e("error", "tag password changed to " + tagPassword);
                        saveToMemory();
                    }
                    if (epcGenerationProps.getInt("companyName") != companyNumber) {
                        companyNumber = epcGenerationProps.getInt("companyName");
                        Log.e("error", "company number changed to " + companyNumber);
                        saveToMemory();
                    }
                    JSONObject tagSerialNumberRange = epcGenerationProps.getJSONObject("tagSerialNumberRange");

                    if (tagSerialNumberRange.getLong("min") != serialNumberMin ||
                            tagSerialNumberRange.getLong("max") != serialNumberMax) {

                        serialNumberMin = tagSerialNumberRange.getLong("min");
                        serialNumberMax = tagSerialNumberRange.getLong("max");
                        serialNumber = serialNumberMin;
                        Log.e("error", "tag serial number range changed to " + serialNumberMin + ":" + serialNumberMax);
                        saveToMemory();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            try {
                if (!getPackageManager().getPackageInfo(getPackageName(), 0).versionName.equals(IotHub.appVersion) &&
                        !IotHub.appVersion.isEmpty()
                ) {
                    Intent intent = new Intent(IotHub.this, AboutUsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            Log.e("error", propertyKey + " changed to " + propertyValue);
        }
    };

    public IotHub() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        loadMemory();
        Thread sendThread = new Thread(() -> {
            try {
                initClient();
            } catch (Exception e) {
                Log.e("error", "Exception while opening IoTHub connection: " + e);
            }
        });

        sendThread.start();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public boolean sendLogFile(List<WriteRecord> writeRecords) throws IOException, InterruptedException {

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("کالا های رایت شده");
        Row headerRow = sheet.createRow(sheet.getPhysicalNumberOfRows());
        headerRow.createCell(0).setCellValue("barcode");
        headerRow.createCell(1).setCellValue("EPC");
        headerRow.createCell(2).setCellValue("date and time");
        headerRow.createCell(3).setCellValue("username");
        headerRow.createCell(4).setCellValue("device serial number");

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ssZ");
        File dir = new File(this.getExternalFilesDir(null), "/");
        File outFile = new File(dir, "log" + sdf.format(new Date()) + ".xlsx");

        for (WriteRecord it : writeRecords) {
            Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
            row.createCell(0).setCellValue(it.getBarcode());
            row.createCell(1).setCellValue(it.getEpc());
            row.createCell(2).setCellValue(it.getDateAndTime());
            row.createCell(3).setCellValue(it.getUsername());
            row.createCell(4).setCellValue(it.getDeviceSerialNumber());

            FileOutputStream outputStream = new FileOutputStream(outFile.getAbsolutePath());
            workbook.write(outputStream);
            outputStream.flush();
            outputStream.close();
        }
        sendLogFileSuccess = false;

        Thread thread = new Thread(() -> {
            try {
                if (outFile.isDirectory()) {
                    throw new IllegalArgumentException(outFile.getName() + " is a directory, please provide a single file name, or use the FileUploadSample to upload directories.");
                }

                Log.e("error", outFile.getName());
                Log.e("error in sending file", "Retrieving SAS URI from IoT Hub...");
                FileUploadSasUriResponse sasUriResponse = client.getFileUploadSasUri(new FileUploadSasUriRequest(outFile.getName()));

                Log.e("error in sending file", "Successfully got SAS URI from IoT Hub");
                Log.e("error in sending file", "Correlation Id: " + sasUriResponse.getCorrelationId());
                Log.e("error in sending file", "Container name: " + sasUriResponse.getContainerName());
                Log.e("error in sending file", "Blob name: " + sasUriResponse.getBlobName());
                Log.e("error in sending file", "Blob Uri: " + sasUriResponse.getBlobUri());

                Log.e("error in sending file", "Using the Azure Storage SDK to upload file to Azure Storage...");

                try {

                    BlobClient blobClient =
                            new BlobClientBuilder()
                                    .endpoint(sasUriResponse.getBlobUri().toString())
                                    .buildClient();
                    Log.e("error", blobClient.toString());


                    blobClient.uploadFromFile(outFile.getAbsolutePath());
                    sendLogFileSuccess = true;

                } catch (Exception e) {
                    Log.e("error in sending file", "Exception encountered while uploading file to blob: " + e.getMessage());

                    Log.e("error in sending file", "Failed to upload file to Azure Storage.");

                    Log.e("error in sending file", "Notifying IoT Hub that the SAS URI can be freed and that the file upload failed.");

                    // Note that this is done even when the file upload fails. IoT Hub has a fixed number of SAS URIs allowed active
                    // at any given time. Once you are done with the file upload, you should free your SAS URI so that other
                    // SAS URIs can be generated. If a SAS URI is not freed through this API, then it will free itself eventually
                    // based on how long SAS URIs are configured to live on your IoT Hub.
                    FileUploadCompletionNotification completionNotification = new FileUploadCompletionNotification(sasUriResponse.getCorrelationId(), false);
                    client.completeFileUpload(completionNotification);

                    Log.e("error in sending file", "Notified IoT Hub that the SAS URI can be freed and that the file upload was a failure.");

                    client.closeNow();
                }

                Log.e("error in sending file", "Successfully uploaded file to Azure Storage.");

                Log.e("error in sending file", "Notifying IoT Hub that the SAS URI can be freed and that the file upload was a success.");
                FileUploadCompletionNotification completionNotification = new FileUploadCompletionNotification(sasUriResponse.getCorrelationId(), true);
                client.completeFileUpload(completionNotification);
                Log.e("error in sending file", "Successfully notified IoT Hub that the SAS URI can be freed, and that the file upload was a success");
            } catch (Exception e) {
                Log.e("error in sending file", "On exception, shutting down \n" + " Cause: " + e.getCause() + " \nERROR: " + e.getMessage() + e.toString());
                Log.e("error in sending file", "Shutting down...");
            }
        });

        thread.start();
        thread.join();
        Log.e("thread error", String.valueOf(sendLogFileSuccess));
        return sendLogFileSuccess;
    }

    /*public void stop() {
        new Thread(() -> {
            try {
                sendThread.interrupt();
                dataCollector.clean();
                client.closeNow();
                Log.e("error", "Shutting down...");
            } catch (Exception e) {
                lastException = "Exception while closing IoTHub connection: " + e;
                Toast.makeText(context, lastException, Toast.LENGTH_LONG).show();
            }
        }).start();
    }*/

    private void loadMemory() {

        SharedPreferences memory = PreferenceManager.getDefaultSharedPreferences(this);

        serialNumber = memory.getLong("value", -1L);
        serialNumberMax = memory.getLong("max", -1L);
        serialNumberMin = memory.getLong("min", -1L);
        headerNumber = memory.getInt("header", -1);
        filterNumber = memory.getInt("filter", -1);
        partitionNumber = memory.getInt("partition", -1);
        companyNumber = memory.getInt("company", -1);
        tagPassword = memory.getString("password", "");
        deviceId = memory.getString("deviceId", "");
        iotToken = memory.getString("iotToken", "");
        Serial = memory.getString("deviceSerialNumber", "");
    }

    private void saveToMemory() {

        SharedPreferences memory = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor memoryEditor = memory.edit();

        memoryEditor.putLong("value", serialNumber);
        memoryEditor.putLong("max", serialNumberMax);
        memoryEditor.putLong("min", serialNumberMin);
        memoryEditor.putInt("header", headerNumber);
        memoryEditor.putInt("filter", filterNumber);
        memoryEditor.putInt("partition", partitionNumber);
        memoryEditor.putInt("company", companyNumber);
        memoryEditor.putLong("counterModified", 0L);
        memoryEditor.putString("password", tagPassword);
        memoryEditor.putString("deviceId", deviceId);
        memoryEditor.putString("iotToken", iotToken);
        memoryEditor.putString("deviceSerialNumber", Serial);
        memoryEditor.apply();
    }

    private void initClient() throws URISyntaxException, IOException {

        final String connString = "HostName=WavecountIoTHub-eastus2.azure-devices.net;DeviceId=" + deviceId +
                ";SharedAccessKey=" + iotToken;

        client = new DeviceClient(connString, protocol);

        try {
            // Open the DeviceClient and start the device twin services.
            client.open();
            client.startDeviceTwin(new DeviceTwinStatusCallBack(), null, dataCollector, null);

            // Create a reported property and send it to your IoT hub.
            dataCollector.setReportedProp(new Property("connectivityType", null));
            dataCollector.setReportedProp(new Property("installedAppVersion", getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
            dataCollector.setReportedProp(new Property("Serial", Serial));
            dataCollector.setReportedProp(new Property("nextTagSerialNumber", serialNumber));
            client.sendReportedProperties(dataCollector.getReportedProp());
        } catch (Exception e) {
            Log.e("error in sending file", "On exception, shutting down \n" + " Cause: " + e.getCause() + " \n" + e.getMessage());
            dataCollector.clean();
            client.closeNow();
            Log.e("error in sending file", "Shutting down...");
        }
    }

    protected static class DeviceTwinStatusCallBack implements IotHubEventCallback {
        @Override
        public void execute(IotHubStatusCode status, Object context) {
            Log.e("error", "IoT Hub responded to device twin operation with status " + status.name());
        }
    }
}
