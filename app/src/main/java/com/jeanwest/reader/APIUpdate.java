package com.jeanwest.reader;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class APIUpdate extends Thread {

    public String Response;
    public boolean status = false;
    String serverAddress = "http://rfid-api-0-1.avakatan.ir/apk/app-debug.apk";
    public Context context;
    public File outputFile;

    public void run() {

        try {

            status = false;
            URL server = new URL(serverAddress);
            HttpURLConnection Connection = (HttpURLConnection) server.openConnection();

            if (Connection.getResponseCode() == 200) {

                outputFile = new File(Environment.getExternalStorageDirectory().getPath(), "app.apk");
                FileOutputStream fos = new FileOutputStream(outputFile);

                InputStream is = Connection.getInputStream();

                byte[] buffer = new byte[1024];
                int len1 = 0;
                while ((len1 = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len1);
                }
                fos.close();
                is.close();//till here, it works fine - .apk is download to my sdcard in download file
                Response = "ok";

            } else {

                Response = Connection.getResponseCode() + " error: " + Connection.getResponseMessage();
                reading.databaseInProgress = false;
            }

            Connection.disconnect();
            Thread.sleep(1000);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Response = e.toString();
        }

        status = true;

    }

}

