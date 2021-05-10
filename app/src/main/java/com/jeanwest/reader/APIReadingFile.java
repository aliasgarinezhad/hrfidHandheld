package com.jeanwest.reader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class APIReadingFile extends Thread {

    public String Response;
    public boolean status = false;
    public volatile boolean run = false;

    public void run(){

        while (true) {

            if(run) {

                status = false;

                try {

                    String GetCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/" + reading.ID + "/epc";

                    URL server = new URL(GetCommand);
                    HttpURLConnection Connection = (HttpURLConnection) server.openConnection();
                    Connection.setDoOutput(true);
                    Connection.setRequestMethod("POST");
                    Connection.setRequestProperty("Content-Type", "application/json");
                    Connection.setRequestProperty("Accept","application/json");
                    Connection.setDoInput(true);

                    OutputStreamWriter out = new OutputStreamWriter(Connection.getOutputStream());
                    ArrayList<String> temp = new ArrayList<String>();

                    if(userSpecActivity.API.wareHouseID == 1706) {
                        for(Map.Entry<String, Integer> filter0: reading.EPCTableFilter1.entrySet()) {
                            temp.add('"' + filter0.getKey() + '"');
                        }
                    }
                    else if(userSpecActivity.API.wareHouseID == 1708) {
                        for(Map.Entry<String, Integer> filter0: reading.EPCTableFilter0.entrySet()) {
                            temp.add('"' + filter0.getKey() + '"');
                        }
                    }
                    out.write(temp.toString());

                    out.close();

                    int statusCode = Connection.getResponseCode();

                    if (statusCode == 204) {
                        status = true;
                    }

                    else if(statusCode == 200) {
                        status = true;
                    }

                    else {
                        Response = Connection.getResponseCode() + " error: " + Connection.getResponseMessage();
                        reading.databaseInProgress = false;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                run = false;
                if(status) {
                    reading.API2.run = true;
                }
            }
        }
    }
}

