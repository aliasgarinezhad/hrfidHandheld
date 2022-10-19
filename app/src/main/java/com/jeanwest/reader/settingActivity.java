package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
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
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Map;

public class settingActivity extends AppCompatActivity {

    Toast response;
    TextView versionName;
    Toast debug;
    AlertDialog alert;
    APIUpdate API = new APIUpdate();
    Handler handler = new Handler();
    Runnable thread = new Runnable() {

        @Override
        public void run() {

            if (API.status) {

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(API.outputFile), "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            else {

                if(!alert.isShowing()) {

                    alert.show();
                }
                handler.postDelayed(thread, 500);
            }
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        response = Toast.makeText(this, " ", Toast.LENGTH_LONG);
        versionName = findViewById(R.id.versionNameView);

        try {
            versionName.setText("ورژن: " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        debug = Toast.makeText(this, "", Toast.LENGTH_LONG);

        API.context = this;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        AlertDialog.Builder AlertBuilder = new AlertDialog.Builder(API.context);
        AlertBuilder.setMessage("لطفا منتظر بمانید ...");
        AlertBuilder.setTitle("در حال دانلود نسخه به روز");

        alert = AlertBuilder.create();
        alert.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dlg) {
                alert.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // set title and message direction to RTL
            }
        });
    }

    public void update(View view) {

        AlertDialog.Builder AlertBuilder = new AlertDialog.Builder(this);
        AlertBuilder.setMessage("نرم افزار به روز رسانی شود؟");
        AlertBuilder.setTitle("به روز رسانی نرم افزار");
        AlertBuilder.setPositiveButton("بله", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                API.start();
                handler.postDelayed(thread, 1000);
            }
        });
        AlertBuilder.setNegativeButton("خیر", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        AlertDialog alertUpdatePermit = AlertBuilder.create();
        alertUpdatePermit.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dlg) {
                alertUpdatePermit.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // set title and message direction to RTL
            }
        });
        alertUpdatePermit.show();
    }
}