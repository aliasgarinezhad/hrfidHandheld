package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.rscja.deviceapi.RFIDWithUHF;

public class MainActivity extends AppCompatActivity {

    public static Barcode2D barcode2D;
    public static String BarcodeID;
    public static RFIDWithUHF RF;
    public static APIAddNew DataBase = new APIAddNew();
    public static ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    public static String EPC;
    public static String TID;
    public static String CONumber = "30";
    public static TabLayout tabs;
    public TabLayout.Tab AddNewTab;
    public TabLayout.Tab ReadingTab;
    public TabLayout.Tab FindingTab;
    public static boolean step2 = false;
    public databaseHelperClass databaseHelper = new databaseHelperClass(this);
    public static SQLiteDatabase table;
    public static SQLiteDatabase counter;
    public static Toast warning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        table = databaseHelper.getWritableDatabase();
    }

    public void addNewActivity(View view) {

        Intent intent = new Intent(this, addNew.class);
        startActivity(intent);
    }
    public void readingActivity(View view) {

        Intent intent = new Intent(this, userSpecActivity.class);
        startActivity(intent);
    }
    public void findingActivity(View view) {

        Intent intent = new Intent(this, finding.class);
        startActivity(intent);
    }
    public void filterchangingActivity(View view) {

        Intent intent = new Intent(this, filterChanging.class);
        startActivity(intent);
    }

    public void settingActivity(View view) {
        Intent intent = new Intent(this, settingActivity.class);
        startActivity(intent);
    }
}