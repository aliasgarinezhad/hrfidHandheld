package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
    public static Integer departmentInfoID;
    public static Integer wareHouseID;
    SharedPreferences memory;
    SharedPreferences.Editor editor;

    @SuppressLint({"SetTextI18n", "CommitPrefEdits"})
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

        memory = PreferenceManager.getDefaultSharedPreferences(this);
        editor = memory.edit();
    }

    @SuppressLint("SetTextI18n")
    protected void onResume() {
        super.onResume();

        departmentInfoID = memory.getInt("ID", 1); // value to store
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

        departmentInfoID = API.departmentInfoID;
        wareHouseID = API.wareHouseID;

        editor.putInt("ID", departmentInfoID); // value to store
        editor.commit();

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

    public void sendFileWithClearing(View view) {
        reading.EPCTable.clear();
        reading.EPCTableValid.clear();

        editor.putString("1", "");
        editor.putString("2", "");
        editor.commit();

        startReading(view);
    }

    public void finishReading(View view) {

        APIReadingFinish APIFinish = new APIReadingFinish();

        APIFinish.DepoMojodiReviewInfo_ID = memory.getInt(String.valueOf(departmentInfoID) + 2, 0);
        APIFinish.StoreMojodiReviewInfo_ID = memory.getInt(String.valueOf(departmentInfoID) + 1, 0);
        APIFinish.start();
        while(APIFinish.run) {}

        status.setText(APIFinish.Response);
        status.show();

    }
}