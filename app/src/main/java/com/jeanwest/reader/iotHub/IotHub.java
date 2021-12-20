package com.jeanwest.reader.iotHub;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Device;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Property;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;

import java.io.IOException;
import java.net.URISyntaxException;

public class IotHub extends Service {

    private final String connString = "HostName=WavecountIoTHub-eastus2.azure-devices.net;DeviceId=android-dev-1;SharedAccessKey=yg7UUtD8WF7KQg+7TTVk45He3g/HR1gj9fxpfPYeJFA=";
    public static final String deviceId = "android-dev-1";
    public static final String region = "US";
    public static final String plant = "Redmond43";

    public String sendString;
    private String lastException;

    IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;

    private DeviceClient client;
    private Context context;

    int msgSentCount = 0;
    int receiptsConfirmedCount = 0;
    int sendFailuresCount = 0;
    int msgReceivedCount = 0;
    int sendMessagesInterval = 5000;

    private Thread sendThread;

    private static final int METHOD_SUCCESS = 200;
    public static final int METHOD_THROWS = 403;
    private static final int METHOD_NOT_DEFINED = 404;

    Device dataCollector = new Device() {
        // Print details when a property value changes
        @Override
        public void PropertyCall(String propertyKey, Object propertyValue, Object context) {

            if(propertyKey.equals("appVersion")) {
                appVersion = propertyValue.toString();
            }

            Log.e("error", propertyKey + " changed to " + propertyValue);
        }
    };
    @org.jetbrains.annotations.Nullable
    public static String appVersion = "";

    public IotHub() {

    }

    public IotHub(Context activityContext) {
        context = activityContext;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        sendThread = new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    initClient();
                    for(;;)
                    {
                        //sendMessages();
                        Thread.sleep(sendMessagesInterval);
                    }
                }

                catch (Exception e)
                {
                    lastException = "Exception while opening IoTHub connection: " + e;
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

    private void initClient() throws URISyntaxException, IOException
    {
        client = new DeviceClient(connString, protocol);

        try {
            // Open the DeviceClient and start the device twin services.
            client.open();
            client.startDeviceTwin(new DeviceTwinStatusCallBack(), null, dataCollector, null);

            // Create a reported property and send it to your IoT hub.
            dataCollector.setReportedProp(new Property("connectivityType", "cellular"));
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
