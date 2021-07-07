package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.exception.ConfigurationException;

public class MainActivity extends AppCompatActivity {

    RFIDWithUHFUART RF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            RF = RFIDWithUHFUART.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        while(!RF.init()) {
            RF.free();
        }

        if(Build.MODEL.equals("EXARKXK650")) {
            //Toast.makeText(this, "4", Toast.LENGTH_LONG).show();
            while(!RF.setFrequencyMode((byte) 4)) {
                RF.free();
            }
        } else if(Build.MODEL.equals("c72")) {
            //Toast.makeText(this, "0x04", Toast.LENGTH_LONG).show();
            while(!RF.setFrequencyMode((byte) 0x04)) {
                RF.free();
            }
        }

        while(!RF.setRFLink(2)) {
            RF.free();
        }
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

        Intent intent = new Intent(this, findingActivity.class);
        startActivity(intent);
    }

    public void settingActivity(View view) {
        Intent intent = new Intent(this, settingActivity.class);
        startActivity(intent);
    }

    public void advanceSettingButton(View view) {
        Intent intent = new Intent(this, loginActivity.class);
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