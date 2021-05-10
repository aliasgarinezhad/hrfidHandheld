package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class readingResultSubActivity extends AppCompatActivity {

    TextView subResult;
    JSONArray subStuffs;
    int numberOfNotStatusNotScanned = 0;
    int numberOfNotStatusScanned = 0;

    int numberOfStatusExtras = 0;
    int numberOfStatusScanned = 0;
    int numberOfNotStatusAll = 0;
    int numberOfStatusAll = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_result_sub);
        subResult = (TextView) findViewById(R.id.subResultView);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        if(reading.API2.stuffs == null) {
            return;
        }

        numberOfNotStatusNotScanned = 0;
        numberOfStatusExtras = 0;
        numberOfStatusScanned = 0;
        numberOfNotStatusScanned = 0;
        numberOfNotStatusAll = 0;
        numberOfStatusAll = 0;

        subResult.setText("");

        try {
            subStuffs = reading.API2.conflicts.getJSONArray(reading.API2.stuffs.getString(readingResultActivity.index));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        int max = subStuffs.length();

        if (max > 200) {
            max = 200;
        }

        for (int i = 0; i < subStuffs.length(); i++) {

            try {
                JSONObject temp = subStuffs.getJSONObject(i);

                if (!temp.getBoolean("status")) {
                    numberOfNotStatusNotScanned += temp.getInt("diffCount");
                    numberOfNotStatusScanned += temp.getInt("handheldCount");
                    numberOfNotStatusAll += temp.getInt("dbCount");
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        subResult.setText(subResult.getText() + "کالاهای اسکن نشده " + "\n"
                + "تعداد اسکن نشده " + numberOfNotStatusNotScanned + "\n"
                + "تعداد اسکن شده " + numberOfNotStatusScanned + "\n"
                + "تعداد کل کالاها " + numberOfNotStatusAll + "\n\n");

        for (int i = 0; i<max; i++) {

            try {
                JSONObject temp = subStuffs.getJSONObject(i);
                if (!temp.getBoolean("status")) {
                    subResult.setText(subResult.getText() + temp.getString("productName") + "\n");
                    subResult.setText(subResult.getText() + "کد محصول: " + temp.getString("K_Bar_Code") + "\n");
                    subResult.setText(subResult.getText() + "تعداد اسکن نشده: " + temp.getString("diffCount") + "\n");
                    subResult.setText(subResult.getText() + "تعداد اسکن شده: " + temp.getString("handheldCount") + "\n");
                    subResult.setText(subResult.getText() + "تعداد کل: " + temp.getString("dbCount") + "\n\n");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        for (int i = 0; i < subStuffs.length(); i++) {

            try {
                JSONObject temp = subStuffs.getJSONObject(i);

                if (temp.getBoolean("status")) {
                    numberOfStatusExtras += temp.getInt("diffCount");
                    numberOfStatusScanned += temp.getInt("handheldCount");
                    numberOfStatusAll += temp.getInt("dbCount");
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        subResult.setText(subResult.getText() + "کالاهای اضافی " + "\n"
                + "تعداد اضافی " + numberOfStatusExtras + "\n"
                + "تعداد اسکن شده " + numberOfStatusScanned + "\n"
                + "تعداد کل کالاها " + numberOfStatusAll + "\n\n");

        for (int i = 0; i<max; i++) {

            try {
                JSONObject temp = subStuffs.getJSONObject(i);
                if (temp.getBoolean("status")) {
                    subResult.setText(subResult.getText() + temp.getString("productName") + "\n");
                    subResult.setText(subResult.getText() + "کد محصول: " + temp.getString("K_Bar_Code") + "\n");
                    subResult.setText(subResult.getText() + "تعداد اضافی: " + temp.getString("diffCount") + "\n");
                    subResult.setText(subResult.getText() + "تعداد اسکن شده: " + temp.getString("handheldCount") + "\n");
                    subResult.setText(subResult.getText() + "تعداد کل: " + temp.getString("dbCount") + "\n\n");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

    }
}
