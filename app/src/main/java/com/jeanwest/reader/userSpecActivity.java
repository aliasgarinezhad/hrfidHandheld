package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.text.BreakIterator;

public class userSpecActivity extends AppCompatActivity {

    public static APIReading API = new APIReading();
    private Toast status;
    EditText departmentInfoIDView;
    EditText wareHouseIDView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_spec);
        departmentInfoIDView = (EditText) findViewById(R.id.DepartmentInfoIDView);
        wareHouseIDView = (EditText) findViewById(R.id.WareHouseIDView);

        if(!API.isAlive()) {
            API.start();
        }
        status = Toast.makeText(this, "", Toast.LENGTH_LONG);
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

        reading.ID = Integer.parseInt(API.Response);

        Intent intent = new Intent(this, reading.class);
        startActivity(intent);
    }
}