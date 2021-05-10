package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

public class settingActivity extends AppCompatActivity {

    Spinner filterSpinner;
    public databaseHelperClass2 databaseHelper2 = new databaseHelperClass2(this);
    public static SQLiteDatabase counter;
    private boolean dataExist;
    Toast response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        filterSpinner = (Spinner) findViewById(R.id.filterSpinner2);
        response = Toast.makeText(this, " ", Toast.LENGTH_LONG);

        if(addNew.filterNumber > 1) {
            return;
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

        String[] items = {"انبار (0)", "فروشگاه (1)"};

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        filterSpinner.setAdapter(spinnerAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                addNew.filterNumber = position;
                ContentValues val = new ContentValues();
                val.put("value", addNew.counterValue);
                val.put("max", addNew.counterMaxValue);
                val.put("min", addNew.counterMinValue);
                val.put("header", addNew.headerNumber);
                val.put("filter", addNew.filterNumber);
                val.put("partition", addNew.partitionNumber);
                val.put("company", addNew.companyNumber);
                val.put("power", addNew.RFPower);
                val.put("password", addNew.tagPassword);
                if(addNew.oneStepActive) {
                    val.put("step", 1);
                }
                else {
                    val.put("step", 0);
                }
                val.put("counterModified", 0L);

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
                Uri uri = Uri.parse("https://avakatan-my.sharepoint.com/:u:/p/a_askarinejad/EbX316y3o3lMuPHNNrGPEcwBQX5hzSYE-9-oYyMhxViyxw?e=ZzXMBu");
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
}