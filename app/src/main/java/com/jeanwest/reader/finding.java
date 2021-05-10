package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.KeyEvent;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class finding extends AppCompatActivity {

    public RFIDWithUHF RF;
    public ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding);

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        if (!RF.init()) {
            RF.free();
            RF.init();
        }


        RF.setEPCTIDMode(true);
        RF.setPower(addNew.RFPower);
        RF.setFrequencyMode((byte) 4);
        RF.setRFLink(0);

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {

            String EPCHexString = "1";

            while(EPCHexString.length() < 4)
            {
                EPCHexString = RF.inventorySingleTag().substring(4).toLowerCase();
            }

            Long EPCInt1 = Long.parseLong(EPCHexString.substring(0, 8), 16);
            Long EPCInt2 = Long.parseLong(EPCHexString.substring(8, 16), 16);
            Long EPCInt3 = Long.parseLong(EPCHexString.substring(16, 24), 16);

            String EPCBinaryString1 = Long.toBinaryString(EPCInt1);
            EPCBinaryString1 = String.format("%32s", EPCBinaryString1).replaceAll(" ", "0");
            String EPCBinaryString2 = Long.toBinaryString(EPCInt2);
            EPCBinaryString2 = String.format("%32s", EPCBinaryString2).replaceAll(" ", "0");
            String EPCBinaryString3 = Long.toBinaryString(EPCInt3);
            EPCBinaryString3 = String.format("%32s", EPCBinaryString3).replaceAll(" ", "0");

            String EPCBinaryString = EPCBinaryString1 + EPCBinaryString2 + EPCBinaryString3;

            Integer header = Integer.parseInt(EPCBinaryString.substring(0, 8), 2);
            Long itemNumber = Long.parseLong(EPCBinaryString.substring(25, 57), 2);

            if (header == 48 && itemNumber == 2850000591L) {

                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            }

        } else if (keyCode == 4) {
            RF.free();
            finish();
        }
        return true;
    }
}