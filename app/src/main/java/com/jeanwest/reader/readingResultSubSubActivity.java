package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class readingResultSubSubActivity extends AppCompatActivity {

    JSONArray subStuffs;
    TextView status;
    WebView picture;
    JSONObject stuff;
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_result_sub_sub);
        status = (TextView) findViewById(R.id.status);
        picture = (WebView) findViewById(R.id.pictureWebView);

        intent = new Intent(this, finding.class);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        try {
            subStuffs = reading.API2.conflicts.getJSONArray(reading.API2.stuffs.getString(readingResultActivity.index));
            stuff = subStuffs.getJSONObject(readingResultSubActivity.subIndex);

            status.setText(stuff.getString("productName") + "\n" +
                    "کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                    "بارکد: " + stuff.getString("KBarCode") + "\n" +
                    "تعداد اسکن نشده: " + stuff.getString("diffCount") + "\n" +
                    "تعداد اسکن شده: " + stuff.getString("handheldCount") + "\n" +
                    "تعداد کل: " + stuff.getString("dbCount"));

            picture.loadUrl(stuff.getString("ImgUrl"));
            WebSettings settings = picture.getSettings();
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            picture.setFocusable(false);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if(keyCode == 280 || keyCode == 139) {

                finding.fromReadingResultSubSubActivity = true;
                try {
                    finding.ReadingResultSubSubString = stuff.getString("productName") + "\n" +
                            "کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                            "بارکد: " + stuff.getString("KBarCode") + "\n" +
                            "تعداد اسکن نشده: " + stuff.getString("diffCount") + "\n" +
                            "تعداد اسکن شده: " + stuff.getString("handheldCount") + "\n" +
                            "تعداد کل: " + stuff.getString("dbCount");
                    finding.stuffImgUrl = stuff.getString("ImgUrl");
                    finding.stuffPrimaryCode = stuff.getString("BarcodeMain_ID");
                    finding.stuffRFIDCode = stuff.getString("RFID");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                startActivity(intent);
        }
        else if(keyCode == 4) {
            finish();
        }

        return true;
    }

}