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

public class APIAddNew extends Thread {

    public String Barcode;
    public String Response;
    public boolean status = false;
    public volatile boolean run = false;

    public void run(){

        while (true) {

            if(run) {

                status = false;

                try {

                    String GetCommand = "http://rfid-api-0-1.avakatan.ir/v2/products?KBarCode=" + Barcode;
                    URL server = new URL(GetCommand);
                    HttpURLConnection Connection = (HttpURLConnection) server.openConnection();

                    if (Connection.getResponseCode() == 200) {
                        InputStream input = new BufferedInputStream(Connection.getInputStream());
                        InputStreamReader isr = new InputStreamReader(input, "UTF-8");
                        BufferedReader reader = new BufferedReader(isr);
                        String Receive = reader.readLine();

                        JSONObject Json = new JSONObject(Receive);
                        Response = Json.getString("RFID");
                        status = true;
                    }
                    else {

                        Response = Connection.getResponseCode() + "Error: " + Connection.getResponseMessage();
                    }

                } catch (JSONException | IOException e) {
                    e.printStackTrace();
                }
                run = false;
            }
        }
    }
}