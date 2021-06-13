package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.graphics.Color;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class filterChanging extends AppCompatActivity /*implements IBarcodeResult*/ {

    //Barcode2D barcode2D;
    RFIDWithUHF RF;
    TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_changing);
        status = findViewById(R.id.statusView);
        //barcode2D = new Barcode2D(this);

        //open();

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        RF.setPower(30);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {

            try {
                addNewTag();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /*@Override
    public void getBarcode(String barcode) throws InterruptedException {

    }


    public void start() {
        barcode2D.startScan(this);
    }
    public void stop() {
        barcode2D.stopScan(this);
    }
    public void open() {
        barcode2D.open(this,this);
    }
    public void close() {
        barcode2D.stopScan(this);
        barcode2D.close(this);
    }*/

    @SuppressLint("SetTextI18n")
    public void addNewTag() throws InterruptedException {

        String temp = RF.readData("00000000", RFIDWithUHF.BankEnum.TID, 0, 96, "E28011702000145517650A17", RFIDWithUHF.BankEnum.UII, 2, 6);
        status.setText(temp);
        /*String[][] TIDBuffer = new String[20][10];
        String tempStr;
        int LoopVariable;
        boolean Collision;
        int tempByte;

        RF.startInventoryTag(0, 0);

        Thread.sleep(1000);

        for(LoopVariable=0; LoopVariable<10; LoopVariable++) {
            TIDBuffer[LoopVariable] = RF.readTagFromBuffer();
        }

        if (TIDBuffer[9] == null) {

            status.setText(status.getText() + "هیچ تگی یافت نشد");
            beep.startTone(ToneGenerator.TONE_CDMA_PIP,500);
            status.setBackgroundColor(Color.RED);
            return;
        }

        Collision = false;
        for(LoopVariable=0;LoopVariable<10;LoopVariable++) {

            if(!(TIDBuffer[LoopVariable][0].equals(TIDBuffer[0][0]))) {
                Collision = true;
                break;
            }
        }

        if(Collision) {

            status.setText(status.getText() + "تعداد تگ های یافت شده بیشتر از یک عدد است");

            beep.startTone(ToneGenerator.TONE_CDMA_PIP,500);
            status.setBackgroundColor(Color.RED);
            return;
        }

        TID = TIDBuffer[0][0];
        EPC = TIDBuffer[0][1].substring(4);

        status.setText(status.getText() + "اسکن با موفقیت انجام شد" + "\nTID: " + TID + "\nEPC: " + EPC);
        //RF.stopInventory();

        DataBase.Barcode = BarcodeID;
        DataBase.run = true;

        while (DataBase.run) {}

        if (!DataBase.status) {

            status.setText(status.getText() + "\nخطا در دیتابیس\n" + DataBase.Response + "\n");
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            status.setBackgroundColor(Color.RED);
            return;
        }

        //RF.setPower(30);
        //EPC values
        Long itemNumber = Long.parseLong(DataBase.Response); // 32 bit
        Long serialNumber = counterValue; // 38 bit

        tempStr = Long.toBinaryString(itemNumber);
        String itemNumberStr = String.format("%32s", tempStr).replaceAll(" ", "0");
        tempStr = Long.toBinaryString(serialNumber);
        String serialNumberStr = String.format("%38s", tempStr).replaceAll(" ", "0");

        String EPCStr = headerStr + positionStr + filterStr + companynumberStr + itemNumberStr + serialNumberStr; // binary string of EPC (96 bit)

        tempByte = Integer.parseInt(EPCStr.substring(0,8), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC0 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(8,16), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC1 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(16,24), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC2 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(24,32), 2);
        tempStr = Integer.toString(tempByte , 16);

        String EPC3 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(32,40), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC4 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(40,48), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC5 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(48,56), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC6 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(56,64), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC7 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(64,72), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC8 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(72, 80));
        tempStr = Integer.toString(tempByte , 16);
        String EPC9 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(80,88), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC10 = String.format("%2s", tempStr).replaceAll(" ", "0");

        tempByte = Integer.parseInt(EPCStr.substring(88,96), 2);
        tempStr = Integer.toString(tempByte , 16);
        String EPC11 = String.format("%2s", tempStr).replaceAll(" ", "0");

        String New = EPC0 + EPC1 + EPC2 + EPC3 + EPC4 + EPC5 + EPC6 + EPC7 + EPC8 + EPC9 + EPC10 + EPC11;

        int k;
        for(k = 0; k < 15; k++) {

            if(RF.writeData("00000000", RFIDWithUHF.BankEnum.TID, 0, 6, TID, RFIDWithUHF.BankEnum.UII, 2, 6, New)) {
                break;
            }
        }

        if (k < 15) {

            String EPCVerify = null;

            int o;

            for(o=0; o < 15; o++) {

                try {
                    EPCVerify = RF.inventorySingleTag().substring(4).toLowerCase();
                    break;
                }
                catch (NullPointerException e) {

                    status.setText(status.getText() + "\n" + "سریال نوشته شده با سریال واقعی تطابق ندارد");
                    status.setText(status.getText() + "EPCVerify");
                    status.setText(status.getText() + New);

                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                    status.setBackgroundColor(Color.RED);
                }
            }

            if(o >= 15) {
                status.setText(status.getText() + "\n" + "سریال نوشته شده با سریال واقعی تطابق ندارد");
                status.setText(status.getText() + "EPCVerify");
                status.setText(status.getText() + New);

                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                status.setBackgroundColor(Color.RED);
                return;
            }

            if(!New.equals(EPCVerify)) {
                status.setText(status.getText() + "\n" + "سریال نوشته شده با سریال واقعی تطابق ندارد");
                status.setText(status.getText() + "\n" + EPCVerify);
                status.setText(status.getText() + "\n" + New);

                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                status.setBackgroundColor(Color.RED);
                return;
            }

            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            status.setBackgroundColor(Color.GREEN);
            status.setText(status.getText() + "\nبا موفقیت اضافه شد");
            status.setText(status.getText() + "\nnumber of try in writing: " + k);
            status.setText(status.getText() + "\nnumber of try in confirming: " + o);
            status.setText(status.getText() + "\nHeader: " + String.valueOf(headerNumber));
            status.setText(status.getText() + "\nFilter: " + String.valueOf(filterNumber));
            status.setText(status.getText() + "\nPartition: " + String.valueOf(partitionNumber));
            status.setText(status.getText() + "\nCompany number: " + String.valueOf(companyNumber));
            status.setText(status.getText() + "\nItem number: " + String.valueOf(itemNumber));
            status.setText(status.getText() + "\nSerial number: " + String.valueOf(serialNumber));
            status.setText(status.getText() + "\nNew EPC: " + String.valueOf(New));
            counterValue++;
            counterValueModified++;
            numberOfWritten.setText("تعداد تگ های برنامه ریزی شده: " + String.valueOf(counterValue - counterMinValue));
            numberOfWrittenModified.setText("مقدار شمارنده: " + String.valueOf(counterValueModified));

            if(counterValue >= counterMaxValue) {
                isAddNewOK = false;
            }

            ContentValues val = new ContentValues();
            val.put("value", counterValue);
            val.put("counterModified", counterValueModified);
            counter.update("counterDatabase", val, null, null);

        } else {

            status.setText(status.getText() + "\nخطا در نوشتن");
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            status.setBackgroundColor(Color.RED);
        }*/
    }

}