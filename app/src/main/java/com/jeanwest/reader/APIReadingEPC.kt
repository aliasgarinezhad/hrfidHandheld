package com.jeanwest.reader;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

public class APIReadingEPC extends Thread {

    public String Response;
    public boolean status = false;
    public volatile boolean run = false;
    public boolean stop = false;

    public void run(){

        while (!stop) {

            if(run) {

                status = false;

                try {

                    String GetCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/" + WarehouseScanningActivity.ID + "/epcs/v2";
                    URL server = new URL(GetCommand);
                    HttpURLConnection Connection = (HttpURLConnection) server.openConnection();
                    Connection.setDoOutput(true);
                    Connection.setRequestMethod("POST");
                    Connection.setRequestProperty("Content-Type", "application/json");
                    Connection.setRequestProperty("Accept","application/json");
                    Connection.setDoInput(true);

                    OutputStreamWriter out = new OutputStreamWriter(Connection.getOutputStream());
                    ArrayList<String> temp = new ArrayList<>();

                    for(Map.Entry<String, Integer> valid: WarehouseScanningActivity.EPCTableValid.entrySet()) {
                        temp.add('"' + valid.getKey() + '"');
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
                        WarehouseScanningActivity.databaseInProgress = false;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                run = false;
                if(status) {
                    WarehouseScanningActivity.API2.run = true;
                }
            }
        }
    }
}

