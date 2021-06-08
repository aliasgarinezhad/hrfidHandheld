package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class findingActivity extends AppCompatActivity {

    RFIDWithUHF RF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding);

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        if(RF.getPower() != 5) {

            while(!RF.setPower(5)) {}
        }

        while(!RF.setEPCTIDMode(true)) {}

    }
}