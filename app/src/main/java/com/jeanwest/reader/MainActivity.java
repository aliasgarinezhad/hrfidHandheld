package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class MainActivity extends AppCompatActivity {

    RFIDWithUHF RF;

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
        while(!RF.setFrequencyMode((byte) 4)) {}
        while(!RF.setRFLink(2)) {}
    }

    public void addNewActivity(View view) {

        Intent intent = new Intent(this, addNew.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        //finishAffinity();
        startActivity(intent);
    }
    public void readingActivity(View view) {

        Intent intent = new Intent(this, userSpecActivity.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
    public void findingActivity(View view) {

        Intent intent = new Intent(this, findingActivity.class);
        startActivity(intent);
    }
    public void filterchangingActivity(View view) {

        /*Intent intent = new Intent(this, filterChanging.class);
        startActivity(intent);*/
    }

    public void settingActivity(View view) {
        Intent intent = new Intent(this, settingActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if(keyCode == 4) {

            RF.free();
            finish();
        }
        return true;
    }
}