package com.jeanwest.reader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class APIReadingInformation extends Thread {

    Integer departmentInfoID = 68;
    Integer wareHouseID = 1706;
    public String Response;
    public boolean status = false;
    public volatile boolean run = false;
    String GetCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/informations/v2";
    public boolean stop = false;

    public void run() {

        while (!stop) {

            if(run) {

                status = false;

                try {

                    JSONObject body = new JSONObject();
                    body.put("DepartmentInfo_ID", departmentInfoID);
                    body.put("WareHouseTypes_ID", wareHouseID);

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.UK);
                    String formattedDate = sdf.format(new Date());

                    body.put("ReviewControlDate", formattedDate);
                    body.put("StartDate", formattedDate);

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

