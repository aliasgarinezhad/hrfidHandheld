package com.jeanwest.reader;

import android.content.ContentValues;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class readingThread extends  Thread {

    public volatile boolean readEnable = false;

    String[] tagBuffer = new String[10];
    public volatile boolean finished = false;
    public boolean stop;

    public void run() {

        while(!stop) {

            if(readEnable) {

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
