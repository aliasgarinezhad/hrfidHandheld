package com.jeanwest.reader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class APIAddNew extends Thread {

    public String Barcode;
    public String Response;
    public boolean status = false;
    public volatile boolean run = false;
    public boolean stop = false;

    public void run() {

        while (!stop) {

            if (run) {

                status = false;

                try {

                    String getCommand = "http://rfid-api-0-1.avakatan.ir/products/v2?KBarCode=" + Barcode;
                    //getCommand = URLEncoder.encode(getCommand, "UTF-8");
                    getCommand = getCommand.replace(" ", "%20");
                    URL server = new URL(getCommand);
                    HttpURLConnection connection = (HttpURLConnection) server.openConnection();

                    if (connection.getResponseCode() == 200) {
                        InputStream input = new BufferedInputStream(connection.getInputStream());
                        InputStreamReader isr = new InputStreamReader(input, "UTF-8");
                        BufferedReader reader = new BufferedReader(isr);
                        String receive = reader.readLine();

                        JSONObject json = new JSONObject(receive);
                        Response = json.getString("RFID");
                        status = true;
                    } else {

                        Response = connection.getResponseCode() + "Error: " + connection.getResponseMessage();
                    }

                } catch (JSONException | IOException e) {
                    e.printStackTrace();
                }
                run = false;
            }
        }
    }
}