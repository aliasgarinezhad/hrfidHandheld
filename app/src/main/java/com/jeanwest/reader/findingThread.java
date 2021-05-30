package com.jeanwest.reader;

public class findingThread extends  Thread {

    public volatile boolean readEnable = false;

    String[] tagBuffer = new String[10];
    public volatile boolean finished = false;

    public void run() {

        while(true) {

            if(readEnable) {

                finding.RF.startInventoryTag(0,0);
                finished = false;

                while(readEnable) {

                    tagBuffer = finding.RF.readTagFromBuffer();

                    if(tagBuffer != null) {
                        finding.EPCTableFinding.put(tagBuffer[1].substring(4), 1);
                    }
                }
                finding.RF.stopInventory();

                finished = true;
            }
        }

    }
}
