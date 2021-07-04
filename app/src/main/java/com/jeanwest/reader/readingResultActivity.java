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

public class readingResultActivity extends AppCompatActivity {

    ListView result;
    ArrayList<String> titles = new ArrayList<>();
    ArrayList<String> allNumber = new ArrayList<>();
    ArrayList<String> scannedNumber = new ArrayList<>();
    ArrayList<String> extraNumber = new ArrayList<>();
    Intent intent;
    public static String index;
    public static Integer indexNumber;
    String temp;
    JSONArray subStuffs;
    JSONObject temp2;
    int NotScanned = 0;
    int scanned = 0;
    int Extra = 0;
    int sumNotScanned = 0;
    int sumScanned = 0;
    int sumExtra = 0;
    MyListAdapter listAdapter;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_result);
        result = findViewById(R.id.readingResult);
        intent = new Intent(this, readingResultSubActivity.class);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {

        super.onResume();

        sumNotScanned = 0;
        sumScanned = 0;
        sumExtra = 0;

        titles.clear();
        scannedNumber.clear();
        allNumber.clear();
        extraNumber.clear();

        for (int i = 0; i < reading.API2.stuffs.length(); i++) {

            try {
                temp = reading.API2.stuffs.getString(i);
                subStuffs = reading.API2.conflicts.getJSONArray(temp);
                titles.add(temp);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        titles = sortArray(titles);

        for (int i = 0; i < titles.size(); i++) {

            try {
                temp = titles.get(i);
                subStuffs = reading.API2.conflicts.getJSONArray(temp);

                NotScanned = 0;
                scanned = 0;
                Extra = 0;
                for (int j = 0; j < subStuffs.length(); j++) {

                    temp2 = subStuffs.getJSONObject(j);

                    if (!temp2.getBoolean("status")) {
                        scanned += temp2.getInt("handheldCount");
                        NotScanned += temp2.getInt("diffCount");
                    } else {
                        scanned += temp2.getInt("dbCount");
                        Extra += temp2.getInt("diffCount");
                    }
                }
            } catch (JSONException ignored) {

            }

            sumScanned += scanned;
            sumNotScanned += NotScanned;
            sumExtra += Extra;

            allNumber.add(String.valueOf(NotScanned));
            extraNumber.add(String.valueOf(Extra));
            scannedNumber.add(String.valueOf(scanned));
        }

        titles.add(0, "دسته");
        allNumber.add(0, "اسکن نشده");
        scannedNumber.add(0, "اسکن شده");
        extraNumber.add(0, "اضافی");

        titles.add(1, "مجموع");
        allNumber.add(1, String.valueOf(sumNotScanned));
        scannedNumber.add(1, String.valueOf(sumScanned));
        extraNumber.add(1, String.valueOf(sumExtra));

        listAdapter = new MyListAdapter(this, titles, scannedNumber, allNumber, extraNumber);

        result.setAdapter(listAdapter);

        result.setSelection(indexNumber);

        result.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                if (i == 0 || i == 1) {
                    return;
                }
                indexNumber = i;
                index = titles.get(i);
                readingResultSubActivity.indexNumberSub = 0;
                startActivity(intent);
            }
        });
    }

    ArrayList<String> sortArray(ArrayList<String> array) {

        ArrayList<String> outputArray = new ArrayList<String>();
        String template = "";

        char[] comparator = {'ا', 'ب', 'پ', 'ت', 'ث', 'ج', 'چ', 'ح', 'خ', 'د', 'ذ', 'ر', 'ز', 'ژ', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق'
                , 'ك', 'گ', 'ل', 'م', 'ن', 'و', 'ه', 'ی'};

        for (int j = 0; j < comparator.length; j++) {

            for (int i = 0; i < array.size(); i++) {

                template = array.get(i);
                if (template.startsWith(String.valueOf(comparator[j]))) {
                    outputArray.add(template);
                }
            }
        }

        return outputArray;
    }

}