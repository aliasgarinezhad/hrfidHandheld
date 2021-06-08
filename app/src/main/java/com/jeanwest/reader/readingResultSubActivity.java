package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

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

    ArrayList<String> items;
    ArrayAdapter<String> listAdapter;
    JSONObject stuff;

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

        items = new ArrayList<>();

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1 ,items);

        try {
            subStuffs = reading.API2.conflicts.getJSONArray(reading.API2.stuffs.getString(readingResultActivity.index));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        index = new int[subStuffs.length() + 100];

        for (int i = 0; i < subStuffs.length(); i++) {

            try {
                stuff = subStuffs.getJSONObject(i);

                if (!stuff.getBoolean("status")) {
                    numberOfNotStatusNotScanned += stuff.getInt("diffCount");
                    numberOfNotStatusScanned += stuff.getInt("handheldCount");
                    numberOfNotStatusAll += stuff.getInt("dbCount");
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

        for (int i = 0; i<subStuffs.length(); i++) {

            try {
                stuff = subStuffs.getJSONObject(i);
                if (!stuff.getBoolean("status") && stuff.getInt("diffCount") != 0) {
                    index[j] = i;
                    j++;
                    items.add(stuff.getString("productName") + "\n" +
                    "کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                    "بارکد: " + stuff.getString("KBarCode") + "\n" +
                    "تعداد اسکن نشده: " + stuff.getString("diffCount") + "\n" +
                    "تعداد اسکن شده: " + stuff.getString("handheldCount") + "\n" +
                    "تعداد کل: " + stuff.getString("dbCount"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        for (int i = 0; i < subStuffs.length(); i++) {

            try {
                stuff = subStuffs.getJSONObject(i);

                if (stuff.getBoolean("status")) {
                    numberOfStatusExtras += stuff.getInt("diffCount");
                    numberOfStatusScanned += stuff.getInt("handheldCount");
                    numberOfStatusAll += stuff.getInt("dbCount");
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

        for (int i = 0; i<subStuffs.length(); i++) {

            try {
                stuff = subStuffs.getJSONObject(i);
                if (stuff.getBoolean("status")) {
                    index[j] = i;
                    j++;
                    items.add(stuff.getString("productName") + "\n" +
                    "کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                    "بارکد: " + stuff.getString("KBarCode") + "\n" +
                    "تعداد اضافی: " + stuff.getString("diffCount") + "\n" +
                    "تعداد اسکن شده: " + stuff.getString("handheldCount") + "\n" +
                    "تعداد کل: " + stuff.getString("dbCount"));
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