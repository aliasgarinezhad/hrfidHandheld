package com.jeanwest.reader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class APIFindingSimilar extends Thread {

    public String response;
    public boolean status = false;
    public volatile boolean run = false;
    public JSONArray similar;
    public boolean stop = false;
    public String barcode;

    public void run(){

        while (!stop) {

            if(run) {

                status = false;

                try {

                    String GetCommand = "http://rfid-api-0-1.avakatan.ir/products/similars?DepartmentInfo_ID=68&" + barcode;
                    URL server = new URL(GetCommand);
                    HttpURLConnection connection = (HttpURLConnection) server.openConnection();

                    if (connection.getResponseCode() == 200) {
                        InputStream input = new BufferedInputStream(connection.getInputStream());
                        InputStreamReader isr = new InputStreamReader(input, "UTF-8");
                        BufferedReader reader = new BufferedReader(isr);
                        String receive = reader.readLine();

                        JSONObject Json = new JSONObject(receive);
                        similar = Json.getJSONArray("products");
                        status = true;
                    }
                    else {

                        response = connection.getResponseCode() + " error: " + connection.getResponseMessage();
                        reading.databaseInProgress = false;
                    }

                    connection.disconnect();

                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
                run = false;
            }
        }
    }
}

