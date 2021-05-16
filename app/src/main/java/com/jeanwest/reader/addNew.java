package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class addNew extends AppCompatActivity implements IBarcodeResult{

    public static Barcode2D barcode2D;
    public String BarcodeID;
    public static RFIDWithUHF RF;
    APIAddNew DataBase = new APIAddNew();
    public ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    public String EPC;
    public String TID;
    public static boolean step2 = false;
    public static int RFPower = 5;
    public static boolean oneStepActive = false;
    public static long counterMaxValue = 1000;
    public static long counterMinValue = 0;
    public static String tagPassword = "00000000";
    public databaseHelperClass2 databaseHelper2 = new databaseHelperClass2(this);
    public static SQLiteDatabase counter;
    public static long counterValue = 0;
    public long counterValueModified = 0;
    public Cursor counterCursor;
    public static boolean isAddNewOK = true;
    public static Toast warning;
    public TextView status;
    public TextView numberOfWritten;
    public TextView numberOfWrittenModified;
    public TextView filterText;
    public static int filterNumber = 0;  // 3bit
    public static int partitionNumber = 6; // 3bit
    public static int headerNumber = 48; // 8bit
    public static int companyNumber = 101; // 12bit
    String headerStr;
    String filterStr;
    String positionStr;
    String companynumberStr;
    String CONumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new);
        status = (TextView) findViewById(R.id.section_label);
        warning = Toast.makeText(this, "", Toast.LENGTH_LONG);
        numberOfWritten = (TextView) findViewById(R.id.numberOfWrittenView);
        numberOfWrittenModified = (TextView) findViewById(R.id.numberOfWrittenModifiedView);
        filterText = (TextView) findViewById(R.id.filterTextView);
        barcode2D = new Barcode2D(this);
    }

    @Override
    protected void onResume() {

        super.onResume();

        open();

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
        RF.setPower(5);
        RF.setFrequencyMode((byte) 4);
        RF.setRFLink(0);

        if(!DataBase.isAlive()) {
            DataBase.start();
        }
        counter = databaseHelper2.getWritableDatabase();
        counterCursor = counter.rawQuery("select * from counterDatabase", null);

        if(counterCursor.getCount() <= 0) {
            warning.setText("دیتای برنامه پاک شده است. جهت کسب اطلاعات بیشتر با توسعه دهنده تماس بگیرید");
            warning.show();
            isAddNewOK = false;
        }
        else {
            counterCursor.moveToFirst();
            counterCursor.moveToFirst();
            counterValue = counterCursor.getLong(0);
            counterMaxValue = counterCursor.getLong(1);
            counterMinValue = counterCursor.getLong(2);
            headerNumber = counterCursor.getInt(3);
            filterNumber = counterCursor.getInt(4);
            partitionNumber = counterCursor.getInt(5);
            companyNumber = counterCursor.getInt(6);
            RFPower = counterCursor.getInt(7);
            tagPassword = counterCursor.getString(8);
            oneStepActive = (counterCursor.getInt(9) == 1);
            counterValueModified = counterCursor.getLong(10);

        }
        counterCursor.close();

        String tempStr;

        tempStr = Long.toBinaryString(headerNumber);
        headerStr = String.format("%8s", tempStr).replaceAll(" ", "0");
        tempStr = Long.toBinaryString(filterNumber);
        filterStr = String.format("%3s", tempStr).replaceAll(" ", "0");
        tempStr = Long.toBinaryString(partitionNumber);
        positionStr = String.format("%3s", tempStr).replaceAll(" ", "0");
        tempStr = Long.toBinaryString(companyNumber);
        companynumberStr = String.format("%12s", tempStr).replaceAll(" ", "0");
        String temp = Integer.toHexString(headerNumber);
        CONumber = String.format("%2s", temp).replaceAll(" ", "0");

        numberOfWritten.setText("تعداد تگ های برنامه ریزی شده: " + String.valueOf(counterValue - counterMinValue));
        numberOfWrittenModified.setText(String.valueOf(counterValueModified));
        numberOfWrittenModified.setText("مقدار شمارنده: " + String.valueOf(counterValueModified));

        if(filterNumber == 0) {
            filterText.setText("در سطح انبار");
        }
        else if(filterNumber == 1) {
            filterText.setText("در سطح فروشگاه");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        close();
        RF.free();
        counter.close();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void getBarcode(String barcode) throws InterruptedException {

        if (barcode.length() > 2) {
            BarcodeID = barcode;
            status.setText("Barcode scanned successfully\nID: " + BarcodeID + "\n");
            status.setBackgroundColor(Color.GREEN);
            beep.startTone(ToneGenerator.TONE_CDMA_PIP,150);
            if(oneStepActive) {
                addNewTag();
            }
            else {
                step2 = true;
            }

        } else {
            status.setText("Barcode not found");
            status.setBackgroundColor(Color.RED);
            beep.startTone(ToneGenerator.TONE_CDMA_PIP,500);
        }
    }

    public void start() {
        if(!isAddNewOK) {
            warning.setText("دیتای برنامه پاک شده است. جهت کسب اطلاعات بیشتر با توسعه دهنده تماس بگیرید");
            warning.show();
            return;
        }
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
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {

                if (step2) {
                    try {
                        addNewTag();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    step2 = false;
                }
                else {
                    start();
                }

        } else if (keyCode == 4) {
            close();
            RF.free();
            counter.close();
            finish();
        }
        return true;
    }

    @SuppressLint("SetTextI18n")
    public void addNewTag() throws InterruptedException {

        String[][] TIDBuffer = new String[20][10];
        String tempStr;
        int LoopVariable;
        boolean Collision;
        Integer tempByte;

        RF.setPower(RFPower);
        RF.startInventoryTag(0, 0);

        Thread.sleep(1000);

        for(LoopVariable=0; LoopVariable<10; LoopVariable++) {
            TIDBuffer[LoopVariable] = RF.readTagFromBuffer();
        }

        if (TIDBuffer[9] == null) {

            status.setText(status.getText() + "no tags detected");
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

            status.setText(status.getText() + "too many tags detected");

            beep.startTone(ToneGenerator.TONE_CDMA_PIP,500);
            status.setBackgroundColor(Color.RED);
            return;
        }

        TID = TIDBuffer[0][0];
        EPC = TIDBuffer[0][1].substring(4);

        status.setText(status.getText() + "Read successfully\nTID: " + TID + "\nEPC: " + EPC);
        RF.stopInventory();

        DataBase.Barcode = BarcodeID;
        DataBase.run = true;

        while (DataBase.run) {}

        if (!DataBase.status) {

            status.setText(status.getText() + "\nError in database process\n" + DataBase.Response + "\n");
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            status.setBackgroundColor(Color.RED);
            return;
        }

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

        if (RF.writeData("00000000", RFIDWithUHF.BankEnum.TID, 0, 6, TID, RFIDWithUHF.BankEnum.UII, 2, 6, New)) {

            Thread.sleep(100);

            if(!New.equals(RF.inventorySingleTag().substring(4).toLowerCase())) {
                status.setText(status.getText() + "\nConfirmation fail");
                status.setText(status.getText() + RF.inventorySingleTag().substring(4));
                status.setText(status.getText() + New);

                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                status.setBackgroundColor(Color.RED);
                return;
            }

            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            status.setBackgroundColor(Color.GREEN);
            status.setText(status.getText() + "\nWrite successfully");
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

            status.setText(status.getText() + "\nWrite fail");
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            status.setBackgroundColor(Color.RED);
        }
        return;
    }

    public void CounterClearButton(View view) {
        counterValueModified = 0;
        numberOfWrittenModified.setText("مقدار شمارنده: " + String.valueOf(counterValueModified));

        ContentValues val = new ContentValues();
        val.put("counterModified", counterValueModified);
        counter.update("counterDatabase", val, null, null);
    }
}