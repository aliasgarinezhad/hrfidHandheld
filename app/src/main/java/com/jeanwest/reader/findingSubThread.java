package com.jeanwest.reader;

import com.rscja.deviceapi.entity.UHFTAGInfo;

public class findingSubThread extends  Thread {

    public volatile boolean readEnable = false;

    UHFTAGInfo tagBuffer = new UHFTAGInfo();
    public volatile boolean finished = false;
    public boolean stop;

    public void run() {

        while(!stop) {

            if(readEnable) {

                findingResultSubActivity.RF.startInventoryTag(0,0 ,0);
                finished = false;

                while(readEnable) {

                    tagBuffer = findingResultSubActivity.RF.readTagFromBuffer();

                    if(tagBuffer != null) {
                        if(!findingResultSubActivity.isProcessing) {
                            findingResultSubActivity.EPCTableFinding.put(tagBuffer.getEPC(), 1);
                        }
                    }
                }
                findingResultSubActivity.RF.stopInventory();

                finished = true;
            }
        }

    }
}
