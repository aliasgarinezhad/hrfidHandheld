package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

public class findingActivity extends AppCompatActivity implements IBarcodeResult {

    Barcode2D barcode2D;
    static APIFindingSimilar API;
    Toast result;
    ListView list;
    ArrayList<String> listString = new ArrayList<>();
    ArrayList<String> pictureURLList = new ArrayList<>();
    EditText K_Bar_Code;
    public static int index = 0;
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding);
        barcode2D = new Barcode2D(this);
        result = Toast.makeText(this, "", Toast.LENGTH_LONG);
        list = findViewById(R.id.findingListView);
        K_Bar_Code = findViewById(R.id.K_Bar_CodeView);
        intent = new Intent(this, findingResultSubActivity.class);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                index = i;
                startActivity(intent);
            }
        });

        API = new APIFindingSimilar();
        API.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        open();

        MyListAdapterFind findingListAdapter = new MyListAdapterFind(this, listString, pictureURLList);

        list.setAdapter(findingListAdapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        close();
        API.stop = true;
    }

    @Override
    public void getBarcode(String barcode) throws InterruptedException {

        JSONObject json;

        if(barcode.length() > 0) {

            API.barcode = "kbarcode=" + barcode;
            API.run = true;
            while (API.run) {}

            if(!API.status) {
                result.setText(API.response);
                result.show();
                return;
            }

            View view = this.getCurrentFocus();
            InputMethodManager imm =(InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

            listString.clear();

            for(int i=0; i< API.similar.length(); i++) {
                try {
                    json = API.similar.getJSONObject(i);

                    listString.add(json.getString("KBarCode"));
                    pictureURLList.add(json.getString("ImgUrl"));

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            //ArrayAdapter<String> findingListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listString);
            MyListAdapterFind findingListAdapter = new MyListAdapterFind(this, listString, pictureURLList);

            list.setAdapter(findingListAdapter);

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if(keyCode == 280 || keyCode == 139 || keyCode == 293) {

            start();
        }
        else if(keyCode == 4) {

            close();
            finish();
            API.stop = true;
        }

        return true;
    }

    public void start() {
        barcode2D.startScan(this);
    }
    public void stop() {
        barcode2D.stopScan(this);
    }
    public void open() {
        barcode2D.open(this,this);
    }
    public void close() {
        barcode2D.stopScan(this);
        barcode2D.close(this);
    }

    public void receive(View view) {

        JSONObject json;
        API.barcode = "K_Bar_Code=" + K_Bar_Code.getEditableText().toString();
        API.run = true;
        while (API.run) {}

        if(!API.status) {
            result.setText(API.response);
            result.show();
            return;
        }

        InputMethodManager imm =(InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        listString.clear();

        for(int i=0; i< API.similar.length(); i++) {

            try {
                json = API.similar.getJSONObject(i);
                listString.add(json.getString("KBarCode"));
                pictureURLList.add(json.getString("ImgUrl"));

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //ArrayAdapter<String> findingListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listString);
        MyListAdapterFind findingListAdapter = new MyListAdapterFind(this, listString, pictureURLList);

        list.setAdapter(findingListAdapter);
    }
}