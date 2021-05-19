package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class readingResultSubActivity extends AppCompatActivity {

    ListView subResult;
    JSONArray subStuffs;
    int numberOfNotStatusNotScanned = 0;
    int numberOfNotStatusScanned = 0;
    int j;
    int numberOfStatusExtras = 0;
    int numberOfStatusScanned = 0;
    int numberOfNotStatusAll = 0;
    int numberOfStatusAll = 0;
    Intent intent;

    int[] index;
    public static int subIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_result_sub);
        subResult = (ListView) findViewById(R.id.subResultView);
        intent = new Intent(this, readingResultSubSubActivity.class);
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
        j = 0;

        ArrayList<String> items = new ArrayList<>();

        ArrayAdapter<String> listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1 ,items);

        try {
            subStuffs = reading.API2.conflicts.getJSONArray(reading.API2.stuffs.getString(readingResultActivity.index));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        int max = subStuffs.length();

        if (max > 200) {
            max = 200;
        }
        index = new int[subStuffs.length() + 100];

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

        items.add("کالاهای اسکن نشده " + "\n"
                + "تعداد اسکن نشده " + numberOfNotStatusNotScanned + "\n"
                + "تعداد اسکن شده " + numberOfNotStatusScanned + "\n"
                + "تعداد کل کالاها " + numberOfNotStatusAll);

        index[j] = -1;
        j++;

        for (int i = 0; i<max; i++) {

            try {
                JSONObject temp = subStuffs.getJSONObject(i);
                if (!temp.getBoolean("status") && temp.getInt("diffCount") > 0) {
                    index[j] = i;
                    j++;
                    items.add(temp.getString("productName") + "\n" +
                    "کد محصول: " + temp.getString("K_Bar_Code") + "\n" +
                    "تعداد اسکن نشده: " + temp.getString("diffCount") + "\n" +
                    "تعداد اسکن شده: " + temp.getString("handheldCount") + "\n" +
                    "تعداد کل: " + temp.getString("dbCount"));
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

        items.add("کالاهای اضافی " + "\n"
                + "تعداد اضافی " + numberOfStatusExtras + "\n"
                + "تعداد اسکن شده " + numberOfStatusScanned + "\n"
                + "تعداد کل کالاها " + numberOfStatusAll);

        index[j] = -1;
        j++;

        for (int i = 0; i<max; i++) {

            try {
                JSONObject temp = subStuffs.getJSONObject(i);
                if (temp.getBoolean("status")) {
                    index[j] = i;
                    j++;
                    items.add(temp.getString("productName") + "\n" +
                    "کد محصول: " + temp.getString("K_Bar_Code") + "\n" +
                    "تعداد اضافی: " + temp.getString("diffCount") + "\n" +
                    "تعداد اسکن شده: " + temp.getString("handheldCount") + "\n" +
                    "تعداد کل: " + temp.getString("dbCount"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        subResult.setAdapter(listAdapter);

        subResult.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(index[i] != -1) {
                    subIndex = index[i];
                    startActivity(intent);
                }
            }
        });
    }
}