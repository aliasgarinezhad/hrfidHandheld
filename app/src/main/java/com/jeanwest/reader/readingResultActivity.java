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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class readingResultActivity extends AppCompatActivity {

    ListView result;
    ArrayList<String> titles = new ArrayList<String>();
    Intent intent;
    public static int index = 0;


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_result);
        result = (ListView) findViewById(R.id.readingResult);
        intent = new Intent(this, readingResultSubActivity.class);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {

        super.onResume();

        int sumNotScanned = 0;
        int sumScanned = 0;
        int sumExtra = 0;

        titles.clear();

        titles.add("دسته     تعداد اسکن شده   تعداد اسکن نشده   تعداد اضافی");

        for (int i = 0; i<reading.API2.stuffs.length(); i++) {

            String temp = null;
            JSONArray subStuffs = null;

            try {
                temp = reading.API2.stuffs.getString(i);
                subStuffs = reading.API2.conflicts.getJSONArray(temp);

                if(temp.equals("null")) {
                    temp = "متفرقه";
                }

                //temp = String.format("%10s", temp);

                int NotScanned = 0;
                int scanned = 0;
                int Extra = 0;
                for (int j = 0; j < subStuffs.length(); j++) {

                    JSONObject temp2 = subStuffs.getJSONObject(j);

                    if (!temp2.getBoolean("status")) {
                        scanned += temp2.getInt("handheldCount");
                        NotScanned += temp2.getInt("diffCount");
                    }
                    else {
                        scanned += temp2.getInt("dbCount");
                        Extra += temp2.getInt("diffCount");
                    }
                }

                temp += "          " + scanned;
                temp += "          " + NotScanned;
                temp += "          " + Extra;
                sumScanned += scanned;
                sumNotScanned += NotScanned;
                sumExtra += Extra;

                titles.add(temp);

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        titles.add(1, "مجموع         " + sumScanned +  "      " + sumNotScanned + "      " + sumExtra);

        ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, titles);

        result.setAdapter(listAdapter);

        result.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                if(i==0 || i==1) {
                    return;
                }
                index = i-2;
                startActivity(intent);
            }
        });
    }

}