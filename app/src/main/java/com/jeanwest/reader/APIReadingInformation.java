package com.jeanwest.reader;

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

public class APIReadingInformation extends Thread {

    Integer departmentInfoID = 68;
    Integer wareHouseID = 1706;
    public String Response;
    public boolean status = false;
    public volatile boolean run = false;
    String GetCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/informations";

    public void run(){

        while (true) {

            if(run) {

                status = false;

                try {

                    JSONObject body = new JSONObject();
                    body.put("DepartmentInfo_ID", departmentInfoID);
                    body.put("WareHouse_ID", wareHouseID);
                    body.put("ReviewControlDate", "2021-05-02T16:04:39.0Z");
                    body.put("StartDate", "2021-05-02T16:04:39.0Z");

                    URL server = new URL(GetCommand);
                    HttpURLConnection Connection = (HttpURLConnection) server.openConnection();
                    Connection.setDoOutput(true);
                    Connection.setRequestMethod("POST");
                    Connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    Connection.setRequestProperty("Accept","application/json");
                    Connection.setDoInput(true);

                    OutputStreamWriter out = new OutputStreamWriter(Connection.getOutputStream());
                    out.write(body.toString());
                    out.close();

                    if (Connection.getResponseCode() == 200) {

                        BufferedReader reader = new BufferedReader(new InputStreamReader(Connection.getInputStream()));
                        String Receive = reader.readLine();

                        JSONObject Json = new JSONObject(Receive);
                        Response = Json.getString("MojodiReviewInfo_ID");
                        status = true;
                    }
                    else {
                        Response = Connection.getResponseCode() + "Error: " + Connection.getResponseMessage();
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

