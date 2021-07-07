package com.jeanwest.reader;

import com.rscja.deviceapi.entity.UHFTAGInfo;

public class readingThread extends  Thread {

    public volatile boolean readEnable = false;

    UHFTAGInfo tagBuffer = new UHFTAGInfo();
    public volatile boolean finished = false;
    public boolean stop;

    public void run() {

        while(!stop) {

            if(readEnable) {

                reading.RF.startInventoryTag(0, 0, 0);
                finished = false;

                while(readEnable) {

                    tagBuffer = reading.RF.readTagFromBuffer();

                    if(tagBuffer != null) {
                        reading.EPCTable.put(tagBuffer.getEPC(), 1);
                    }
                }
                reading.RF.stopInventory();

                finished = true;
            }
        }

    }
}
