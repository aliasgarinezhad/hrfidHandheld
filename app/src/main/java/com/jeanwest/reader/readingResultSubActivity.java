package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
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

    public static Integer indexNumberSub;

    int[] index;
    public static int subIndex;

    ArrayList<String> titles;
    ArrayList<String> specs;
    ArrayList<String> scanned;
    ArrayList<String> notScanned;
    ArrayList<String> all;
    ArrayList<String> pictureURL;

    MyListAdapterSub listAdapter;
    JSONObject stuff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_result_sub);
        subResult = findViewById(R.id.subResultView);
        intent = new Intent(this, readingResultSubSubActivity.class);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        numberOfNotStatusNotScanned = 0;
        numberOfStatusExtras = 0;
        numberOfStatusScanned = 0;
        numberOfNotStatusScanned = 0;
        numberOfNotStatusAll = 0;
        numberOfStatusAll = 0;
        j = 0;

        titles = new ArrayList<>();
        specs = new ArrayList<>();
        scanned = new ArrayList<>();
        notScanned = new ArrayList<>();
        all = new ArrayList<>();
        pictureURL = new ArrayList<>();

        try {
            subStuffs = reading.API2.conflicts.getJSONArray(ReadingResultActivity.index);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
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
                return;
            }
        }

        titles.add("کالاهای اسکن نشده");
        specs.add("ناموجود در انبار، موجود در سرور");
        notScanned.add("تعداد اسکن نشده: " + numberOfNotStatusNotScanned);
        scanned.add("تعداد اسکن شده: " + numberOfNotStatusScanned);
        all.add("تعداد کل: " + numberOfNotStatusAll);
        pictureURL.add("a");


        index[j] = -1;
        j++;

        for (int i = 0; i<subStuffs.length(); i++) {

            try {
                stuff = subStuffs.getJSONObject(i);
                if (!stuff.getBoolean("status") && stuff.getInt("diffCount") != 0) {
                    index[j] = i;
                    j++;

                    titles.add(stuff.getString("productName"));
                    specs.add("کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                            "بارکد: " + stuff.getString("KBarCode"));
                    notScanned.add("تعداد اسکن نشده: " + stuff.getString("diffCount"));
                    scanned.add("تعداد اسکن شده: " + stuff.getString("handheldCount"));
                    all.add("تعداد کل: " + stuff.getString("dbCount"));
                    pictureURL.add(stuff.getString("ImgUrl"));

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

        titles.add("کالاهای اضافی");
        specs.add("ناموجود در سرور، موجود در انبار");
        notScanned.add("تعداد اسکن نشده: " + numberOfStatusExtras);
        scanned.add("تعداد اسکن شده: " + numberOfStatusScanned);
        all.add("تعداد کل: " + numberOfStatusAll);
        pictureURL.add("a");

        index[j] = -1;
        j++;

        for (int i = 0; i<subStuffs.length(); i++) {

            try {
                stuff = subStuffs.getJSONObject(i);
                if (stuff.getBoolean("status")) {
                    index[j] = i;
                    j++;

                    titles.add(stuff.getString("productName"));
                    specs.add("کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                            "بارکد: " + stuff.getString("KBarCode"));
                    notScanned.add("تعداد اضافی: " + stuff.getString("diffCount"));
                    scanned.add("تعداد اسکن شده: " + stuff.getString("handheldCount"));
                    all.add("تعداد کل: " + stuff.getString("dbCount"));
                    pictureURL.add(stuff.getString("ImgUrl"));

                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        listAdapter = new MyListAdapterSub(this, titles, specs, scanned, all, notScanned, pictureURL);

        subResult.setAdapter(listAdapter);

        subResult.setSelection(indexNumberSub);

        subResult.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(index[i] != -1) {
                    indexNumberSub = i;
                    subIndex = index[i];
                    startActivity(intent);
                }
            }
        });
    }
}