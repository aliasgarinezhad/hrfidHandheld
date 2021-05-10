package com.jeanwest.reader;

import android.content.ContentValues;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class readingThread extends  Thread {

    public volatile boolean readEnable = false;

    String[] tagBuffer = new String[10];
    int numberOfScans = 0;
    //ContentValues values = new ContentValues();
    //int i;
    public volatile boolean finished = false;

    public void run() {

        while(true) {

            if(readEnable) {

                try {
                    reading.RF = RFIDWithUHF.getInstance();
                } catch (ConfigurationException e) {
                    e.printStackTrace();
                }
                if (!reading.RF.init()) {
                    reading.RF.free();
                    reading.RF.init();
                }

                reading.RF.setEPCTIDMode(false);
                reading.RF.setPower(30);
                reading.RF.setFrequencyMode((byte) 4);
                reading.RF.setRFLink(2);

                reading.RF.startInventoryTag(0,0);
                finished = false;

                while(readEnable) {

                    tagBuffer = reading.RF.readTagFromBuffer();

                    if(tagBuffer != null) {
                        reading.EPCTable.put(tagBuffer[1].substring(4), 1);
                    }
                }
                reading.RF.stopInventory();

                finished = true;
            }
        }

    }
}
