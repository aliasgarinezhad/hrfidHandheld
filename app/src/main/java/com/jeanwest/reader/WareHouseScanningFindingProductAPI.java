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

public class WareHouseScanningFindingProductAPI extends Thread {

    public String Response;
    public boolean status = false;
    public volatile boolean run = false;
    JSONObject Json;
    public boolean stop;

    public void run(){

        while (!stop) {

            if(run) {

                status = false;

                try {

                    String GetCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/" + WarehouseScanning.ID + "/epcs?BarcodeMain_ID=" + readingResultSubSubActivity.stuffPrimaryCode + "&RFID=" + readingResultSubSubActivity.stuffRFIDCode;
                    URL server = new URL(GetCommand);
                    HttpURLConnection Connection = (HttpURLConnection) server.openConnection();

                    if (Connection.getResponseCode() == 200) {
                        InputStream input = new BufferedInputStream(Connection.getInputStream());
                        InputStreamReader isr = new InputStreamReader(input, "UTF-8");
                        BufferedReader reader = new BufferedReader(isr);
                        String Receive = reader.readLine();

                        Json = new JSONObject(Receive);

                        Response = "";
                        for(int g=0; g<Json.length(); g++) {
                            Response += "\n" + Json.getString(String.valueOf(g));
                        }
                        //Response = Receive;
                        status = true;
                    }
                    else {

                        Response = Connection.getResponseCode() + "Error: " + Connection.getResponseMessage();
                    }

                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
                run = false;
            }
        }
    }
}