package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

public class settingActivity extends AppCompatActivity {

    Spinner filterSpinner;
    databaseHelperClass2 databaseHelper2;
    SQLiteDatabase counter;
    private boolean dataExist;
    Toast response;
    TextView versionName;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        filterSpinner = (Spinner) findViewById(R.id.filterSpinner2);
        response = Toast.makeText(this, " ", Toast.LENGTH_LONG);
        versionName = (TextView) findViewById(R.id.versionNameView);
        databaseHelper2 = new databaseHelperClass2(this);

        try {
            versionName.setText("ورژن: " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        counter = databaseHelper2.getWritableDatabase();
        Cursor counterCursor = counter.rawQuery("select * from counterDatabase", null);
        if(counterCursor.getCount() <= 0) {
            response.setText("دیتای برنامه پاک شده است. جهت استفاده از دستگاه، پارامتر های این صفحه را تنظیم کنید");
            response.show();
            dataExist = false;
        }
        else {
            counterCursor.moveToFirst();
            addNew.filterNumber = counterCursor.getInt(4);
            dataExist = true;
        }
        counterCursor.close();

        if(addNew.filterNumber > 1) {
            return;
        }

        String[] items = {"انبار (0)", "فروشگاه (1)"};

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        filterSpinner.setAdapter(spinnerAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                addNew.filterNumber = position;
                ContentValues val = new ContentValues();
                val.put("filter", addNew.filterNumber);

                if(dataExist) {
                    counter.update("counterDatabase", val, null, null);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        filterSpinner.setSelection(addNew.filterNumber);
    }

    public void update(View view) {

        AlertDialog.Builder AlertBuilder = new AlertDialog.Builder(this);
        AlertBuilder.setMessage("نرم افزار به روز رسانی شود؟");
        AlertBuilder.setTitle("به روز رسانی نرم افزار");
        AlertBuilder.setPositiveButton("بله", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Uri uri = Uri.parse("http://rfid-api-0-1.avakatan.ir/apk/app-debug.apk");
                Intent browser = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(browser);
            }
        });
        AlertBuilder.setNegativeButton("خیر", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        AlertDialog alert = AlertBuilder.create();
        alert.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dlg) {
                alert.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // set title and message direction to RTL
            }
        });
        alert.show();
    }

    public void advanceSettingButton(View view) {
        Intent intent = new Intent(this, loginActivity.class);
        startActivity(intent);
    }
    @Override
    protected void onPause() {
        super.onPause();
        counter.close();
    }
}