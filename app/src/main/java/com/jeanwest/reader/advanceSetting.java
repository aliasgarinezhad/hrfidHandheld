package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class advanceSetting extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advance_setting);
    }

    public void addNewSetting(View view) {
        Intent intent = new Intent(this, addNewSetting.class);
        startActivity(intent);
    }

    public void readingSetting(View view) {
        /*Intent intent = new Intent(this, readingSetting.class);
        startActivity(intent);*/
    }

    public void findingSetting(View view) {
        /*Intent intent = new Intent(this, findingSetting.class);
        startActivity(intent);*/
    }

    public void filterChangingSetting(View view) {
        /*Intent intent = new Intent(this, filterChangingSetting.class);
        startActivity(intent);*/
    }
}