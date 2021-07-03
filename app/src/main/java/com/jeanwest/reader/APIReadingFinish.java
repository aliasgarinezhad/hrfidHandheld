package com.jeanwest.reader;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class APIReadingFinish extends Thread {

    public String Response;
    public boolean status = false;
    public volatile boolean run = true;
    public boolean stop = false;
    public Integer StoreMojodiReviewInfo_ID;
    public Integer DepoMojodiReviewInfo_ID;

    public void run() {

        status = false;

        try {

            String getCommand = "http://rfid-api-0-1.avakatan.ir/stock-taking/reports?StoreMojodiReviewInfo_ID=" +
                    StoreMojodiReviewInfo_ID + "&DepoMojodiReviewInfo_ID=" + DepoMojodiReviewInfo_ID;
            URL server = new URL(getCommand);
            HttpURLConnection connection = (HttpURLConnection) server.openConnection();

            if (connection.getResponseCode() == 200) {

                Response = "با موفقیت ارسال شد";
                status = true;
            } else {

                Response = connection.getResponseCode() + "Error: " + connection.getResponseMessage();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        run = false;
    }
}