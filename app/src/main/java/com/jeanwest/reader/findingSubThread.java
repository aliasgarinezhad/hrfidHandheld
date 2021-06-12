package com.jeanwest.reader;

public class findingSubThread extends  Thread {

    public volatile boolean readEnable = false;

    String[] tagBuffer = new String[10];
    public volatile boolean finished = false;
    public boolean stop;

    public void run() {

        while(!stop) {

            if(readEnable) {

                findingResultSubActivity.RF.startInventoryTag(0,0);
                finished = false;

                while(readEnable) {

                    tagBuffer = findingResultSubActivity.RF.readTagFromBuffer();

                    if(tagBuffer != null) {
                        if(!findingResultSubActivity.isProcessing) {
                            findingResultSubActivity.EPCTableFinding.put(tagBuffer[1].substring(4), 1);
                        }
                    }
                }
                findingResultSubActivity.RF.stopInventory();

                finished = true;
            }
        }

    }
}
