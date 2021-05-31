package com.jeanwest.reader;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
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
    ArrayList<String> titles = new ArrayList<>();
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
        String title = String.format("%s%15s%15s%12s", "دسته        ", "اسکن شده", "اسکن نشده", "اضافی");
        titles.add(title);

        for (int i = 0; i<reading.API2.stuffs.length(); i++) {

            String temp;
            JSONArray subStuffs;

            try {
                temp = reading.API2.stuffs.getString(i);
                subStuffs = reading.API2.conflicts.getJSONArray(temp);

                if(temp.equals("null")) {
                    temp = "متفرقه";
                }

                if(temp.length() < 12)
                {
                    for(int o =0; o < (12 - temp.length()); o++) {
                        temp += ' ';
                    }
                }

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
                temp = String.format("%s%15s%15s%12s", temp, Integer.toString(scanned), Integer.toString(NotScanned), Integer.toString(Extra));
                sumScanned += scanned;
                sumNotScanned += NotScanned;
                sumExtra += Extra;

                titles.add(temp);

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        titles.add(1, String.format("%s%15s%15s%12s", "مجموع       ", Integer.toString(sumScanned), Integer.toString(sumNotScanned), Integer.toString(sumExtra)));

        ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1, titles);

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