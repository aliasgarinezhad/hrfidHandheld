package com.jeanwest.reader;

public class findingThread extends  Thread {

    public volatile boolean readEnable = false;

    String[] tagBuffer = new String[10];
    public volatile boolean finished = false;
    public boolean stop;

    public void run() {

        while(!stop) {

            if(readEnable) {

                readingResultSubSubActivity.RF.startInventoryTag(0,0);
                finished = false;

                while(readEnable) {

                    tagBuffer = readingResultSubSubActivity.RF.readTagFromBuffer();

                    if(tagBuffer != null) {
                        if(!readingResultSubSubActivity.isProcessing) {
                            readingResultSubSubActivity.EPCTableFinding.put(tagBuffer[1].substring(4), 1);
                        }
                    }
                }
                readingResultSubSubActivity.RF.stopInventory();

                finished = true;
            }
        }
    }
}
