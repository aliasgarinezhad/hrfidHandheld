package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;

public class userSpecActivity extends AppCompatActivity {

    APIReadingInformation API;
    private Toast status;
    EditText departmentInfoIDView;
    Spinner wareHouseIDView;
    Intent intent;
    public static Integer departmentInfoID = 68;
    public static Integer wareHouseID;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_spec);
        departmentInfoIDView = (EditText) findViewById(R.id.DepartmentInfoIDView);
        wareHouseIDView = findViewById(R.id.WareHouseIDViewSpinner);
        intent = new Intent(this, reading.class);

        status = Toast.makeText(this, "", Toast.LENGTH_LONG);

        ArrayList<String> spinnerString = new ArrayList<>();
        spinnerString.add("فروشگاه");
        spinnerString.add("انبار");

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, spinnerString);
        wareHouseIDView.setAdapter(spinnerAdapter);
    }

    @SuppressLint("SetTextI18n")
    protected void onResume() {
        super.onResume();
        departmentInfoIDView.setText(departmentInfoID.toString());

        API = new APIReadingInformation();

        API.stop = false;
        API.start();
    }

    public void startReading(View view) {

        if(departmentInfoIDView.getEditableText().toString().length() == 0) {
            status.setText("\nلطفا کد شعبه را وارد کنید\n");
            status.show();
            return;
        }

        API.departmentInfoID = Integer.parseInt(departmentInfoIDView.getEditableText().toString());
        API.wareHouseID = wareHouseIDView.getSelectedItemPosition() + 1;

        API.run = true;
        while (API.run) {}

        if (!API.status) {

            status.setText("خطا در دیتابیس");
            status.show();

            return;
        }

        wareHouseID = API.wareHouseID;
        departmentInfoID = API.departmentInfoID;

        reading.ID = Integer.parseInt(API.Response);

        reading.fromLogin = true;
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        API.stop = true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if(keyCode == 4) {

            API.stop = true;
            finish();
        }

        return true;
    }
}