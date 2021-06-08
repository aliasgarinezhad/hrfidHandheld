package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class userSpecActivity extends AppCompatActivity {

    APIReadingInformation API;
    private Toast status;
    EditText departmentInfoIDView;
    EditText wareHouseIDView;
    Intent intent;
    public static Integer departmentInfoID;
    public static Integer wareHouseID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_spec);
        departmentInfoIDView = (EditText) findViewById(R.id.DepartmentInfoIDView);
        wareHouseIDView = (EditText) findViewById(R.id.WareHouseIDView);
        intent = new Intent(this, reading.class);

        status = Toast.makeText(this, "", Toast.LENGTH_LONG);
    }

    protected void onResume() {
        super.onResume();

        API = new APIReadingInformation();

        API.stop = false;
        API.start();
    }

    public void startReading(View view) {

        if(departmentInfoIDView.getEditableText().toString().length() == 0 || wareHouseIDView.getEditableText().toString().length() == 0) {
            status.setText("\nلطفا کد شعبه و انبار را وارد کنید\n");
            status.show();
            return;
        }

        API.departmentInfoID = Integer.parseInt(departmentInfoIDView.getEditableText().toString());
        API.wareHouseID = Integer.parseInt(wareHouseIDView.getEditableText().toString());

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