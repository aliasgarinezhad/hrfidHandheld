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
import java.nio.charset.StandardCharsets;

public class APIReadingConflicts extends Thread {

    public String Response;
    public boolean status = false;
    public volatile boolean run = false;
    public JSONArray stuffs;
    public JSONObject conflicts;
    public boolean stop = false;

    public void run(){

        while (!stop) {

            if(run) {

                status = false;

                try {

                    stuffs = null;
                    String GetCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/" + WarehouseScanningActivity.ID + "/conflicts/v2";
                    URL server = new URL(GetCommand);
                    HttpURLConnection Connection = (HttpURLConnection) server.openConnection();

                    if (Connection.getResponseCode() == 200) {
                        InputStream input = new BufferedInputStream(Connection.getInputStream());
                        InputStreamReader isr = new InputStreamReader(input, StandardCharsets.UTF_8);

                        BufferedReader reader = new BufferedReader(isr);
                        String Receive = reader.readLine();

                        JSONObject Json = new JSONObject(Receive);
                        conflicts = Json.getJSONObject("conflicts");
                        stuffs = conflicts.names();

                        status = true;
                    }
                    else {

                        Response = Connection.getResponseCode() + " error: " + Connection.getResponseMessage();
                        WarehouseScanningActivity.databaseInProgress = false;
                    }

                    Connection.disconnect();

                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
                run = false;
            }
        }
    }
}

