package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class MainActivity extends AppCompatActivity {

    public RFIDWithUHF RF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        RF.init();
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