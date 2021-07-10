package com.jeanwest.reader;

import com.rscja.deviceapi.entity.UHFTAGInfo;

public class findingThread extends  Thread {

    public volatile boolean readEnable = false;

    UHFTAGInfo tagBuffer = new UHFTAGInfo();
    public volatile boolean finished = false;
    public boolean stop;

    public void run() {

        while(!stop) {

            if(readEnable) {

                readingResultSubSubActivity.RF.startInventoryTag(0, 0, 0);
                finished = false;

                while(readEnable) {

                    tagBuffer = readingResultSubSubActivity.RF.readTagFromBuffer();

                    if(tagBuffer != null) {
                        if(!readingResultSubSubActivity.isProcessing) {
                            readingResultSubSubActivity.EPCTableFinding.put(tagBuffer.getEPC(), 1);
                        }
                    }
                }
                readingResultSubSubActivity.RF.stopInventory();

                finished = true;
            }
        }
    }
}