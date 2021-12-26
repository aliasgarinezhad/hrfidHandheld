package com.jeanwest.reader.iotHub;


import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.jeanwest.reader.aboutUs.AboutUsActivity;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Device;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Property;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;

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

    IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;

    Device dataCollector = new Device() {
        // Print details when a property value changes
        @Override
        public void PropertyCall(String propertyKey, Object propertyValue, Object context) {

            if(propertyKey.equals("appVersion")) {
                appVersion = propertyValue.toString();
                Log.e("error", propertyValue.toString());
            }
            if(propertyKey.equals("epcGenerationProps")) {
                try {
                    JSONObject epcGenerationProps = new JSONObject(propertyValue.toString());
                    if (epcGenerationProps.getInt("header") != headerNumber) {
                        headerNumber = epcGenerationProps.getInt("header");
                        Log.e("error", "header changed");
                        saveToMemory();
                    }
                    if (epcGenerationProps.getInt("filter") != filterNumber) {
                        filterNumber = epcGenerationProps.getInt("filter");
                        Log.e("error", "filter changed");
                        saveToMemory();
                    }
                    if (epcGenerationProps.getInt("partition") != partitionNumber) {
                        partitionNumber = epcGenerationProps.getInt("partition");
                        Log.e("error", "partition changed");
                        saveToMemory();
                    }
                    if (!epcGenerationProps.getString("tagPassword").equals(tagPassword)) {
                        tagPassword = epcGenerationProps.getString("tagPassword");
                        Log.e("error", "tag password changed");
                        saveToMemory();
                    }
                    if (epcGenerationProps.getInt("companyName") != companyNumber) {
                        companyNumber = epcGenerationProps.getInt("companyName");
                        Log.e("error", "company number changed");
                        saveToMemory();
                    }
                    JSONObject tagSerialNumberRange = epcGenerationProps.getJSONObject("tagSerialNumberRange");
                    if (tagSerialNumberRange.getLong("min") != serialNumberMin ||
                            tagSerialNumberRange.getLong("max") != serialNumberMax) {

                        serialNumberMin = epcGenerationProps.getLong("min");
                        serialNumberMax = epcGenerationProps.getLong("max");
                        serialNumber = serialNumberMin;
                        Log.e("error", "tag serial number range changed");
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
        Thread sendThread = new Thread(new Runnable() {
            public void run() {
                try {
                    initClient();
                } catch (Exception e) {
                    Log.e("error", "Exception while opening IoTHub connection: " + e);
                    //handler.post(exceptionRunnable);
                }
            }
        });

        sendThread.start();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
            serialNumberMin = memory.getLong("value", -1L);
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

    private void initClient() throws URISyntaxException, IOException
    {

        final String connString = "HostName=WavecountIoTHub-eastus2.azure-devices.net;DeviceId=" + deviceId +
                ";SharedAccessKey=" + iotToken;

        DeviceClient client = new DeviceClient(connString, protocol);

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
        }
        catch (Exception e) {
            System.out.println("On exception, shutting down \n" + " Cause: " + e.getCause() + " \n" + e.getMessage());
            dataCollector.clean();
            client.closeNow();
            System.out.println("Shutting down...");
        }
    }

    protected static class DeviceTwinStatusCallBack implements IotHubEventCallback {
        @Override
        public void execute(IotHubStatusCode status, Object context) {
            Log.e("error", "IoT Hub responded to device twin operation with status " + status.name());
        }
    }
}
