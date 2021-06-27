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
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

import java.util.HashMap;
import java.util.Map;

public class addNew extends AppCompatActivity implements IBarcodeResult{

    Barcode2D barcode2D;
    String BarcodeID;
    RFIDWithUHF RF;
    APIAddNew DataBase = new APIAddNew();
    ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    String EPC;
    String TID;
    public static boolean step2 = false;
    public static int RFPower = 5;
    public static boolean oneStepActive = false;
    public static long counterMaxValue = 5;
    public static long counterMinValue = 0;
    public static String tagPassword = "00000000";
    databaseHelperClass databaseHelper2;
    SQLiteDatabase counter;
    public static long counterValue = 0;
    long counterValueModified = 0;
    Cursor counterCursor;
    public static boolean isAddNewOK = true;
    Toast warning;
    TextView status;
    TextView numberOfWritten;
    TextView numberOfWrittenModified;
    CheckBox editOption;
    public static int filterNumber = 0;  // 3bit
    public static int partitionNumber = 6; // 3bit
    public static int headerNumber = 48; // 8bit
    public static int companyNumber = 101; // 12bit
    String headerStr;
    String filterStr;
    String positionStr;
    String companynumberStr;
    String CONumber;
    boolean edit = false;
    private boolean barcodeIsScanning = false;
    private boolean RFIsScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new);
        status = findViewById(R.id.section_label);
        warning = Toast.makeText(this, "", Toast.LENGTH_LONG);
        numberOfWritten = findViewById(R.id.numberOfWrittenView);
        numberOfWrittenModified = findViewById(R.id.numberOfWrittenModifiedView);
        barcode2D = new Barcode2D(this);
        databaseHelper2 = new databaseHelperClass(this);
        editOption = findViewById(R.id.checkBox2);

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {

        super.onResume();

        open();

        if(RF.getPower() != RFPower) {

            while(!RF.setPower(RFPower)) {}
        }

        while(!RF.setEPCTIDMode(true)) {}

        DataBase.stop = false;
        DataBase.start();

        counter = databaseHelper2.getWritableDatabase();
        counterCursor = counter.rawQuery("select * from counterDatabase", null);

        if(counterCursor.getCount() <= 0) {
            warning.setText("دیتای برنامه پاک شده است. جهت کسب اطلاعات بیشتر با توسعه دهنده تماس بگیرید");
            warning.show();
            isAddNewOK = false;
        }
        else {
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

        numberOfWritten.setText("تعداد تگ های برنامه ریزی شده: " + (counterValue - counterMinValue));
        numberOfWrittenModified.setText(String.valueOf(counterValueModified));
        numberOfWrittenModified.setText("مقدار شمارنده: " + counterValueModified);
    }

    @Override
    protected void onPause() {
        super.onPause();
        counter.close();
        close();
        DataBase.stop = true;

        step2 = false;
        if(barcodeIsScanning || RFIsScanning) {
            barcodeIsScanning = false;
            RF.stopInventory();
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void getBarcode(String barcode) throws InterruptedException {

        if (barcode.length() > 2) {
            barcodeIsScanning = false;
            BarcodeID = barcode;
            status.setText("اسکن بارکد با موفقیت انجام شد" + "\nID: " + BarcodeID + "\n");
            status.setBackgroundColor(Color.GREEN);
            beep.startTone(ToneGenerator.TONE_CDMA_PIP,150);
            if(oneStepActive) {
                addNewTag();
            }
            else {
                step2 = true;
            }

        } else {
            barcodeIsScanning = false;
            RF.stopInventory();
            RFIsScanning = false;
            status.setText("بارکدی پیدا نشد");
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

            if (event.getRepeatCount() == 0) {

                if(barcodeIsScanning) {
                    return true;
                }

                if (step2) {
                    try {
                        RF.stopInventory();
                        RFIsScanning = false;
                        addNewTag();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    step2 = false;
                } else {
                    start();
                    RFIsScanning = true;
                    RF.startInventoryTag(0, 0);
                    barcodeIsScanning = true;
                }

            }
        } else if (keyCode == 4) {
            counter.close();
            close();
            DataBase.stop = true;

            step2 = false;
            if(barcodeIsScanning || RFIsScanning) {
                barcodeIsScanning = false;
                RF.stopInventory();
            }

            finish();
        }
        return true;
    }

    @SuppressLint("SetTextI18n")
    public void addNewTag() throws InterruptedException {

        String[][] TIDBuffer = new String[1000][10];
        int TIDBufferSize = 0;
        String tempStr;
        int LoopVariable;
        boolean Collision;
        int tempByte;
        Map<String, Integer> EPCs = new HashMap<>();

        boolean isOK = false;

        for (LoopVariable = 0; LoopVariable < 1000; LoopVariable++) {
            TIDBuffer[LoopVariable] = RF.readTagFromBuffer();
            if(TIDBuffer[LoopVariable] == null) {
                break;
            }
        }

        TIDBufferSize = LoopVariable;

        if(TIDBufferSize > 980) {
            Thread.sleep(100);
            RF.startInventoryTag(0, 0);
            start();
            barcodeIsScanning = true;
            RFIsScanning = true;
            return;
        }

        try {

            for (LoopVariable = 0; LoopVariable < TIDBufferSize; LoopVariable++) {
                if (edit) {
                    EPCs.put(TIDBuffer[LoopVariable][1], 1);
                    TID = TIDBuffer[LoopVariable][0];
                    EPC = TIDBuffer[LoopVariable][1].substring(4);
                } else {
                    if (!(TIDBuffer[LoopVariable][1].startsWith("30", 4))) {
                        EPCs.put(TIDBuffer[LoopVariable][1], 1);
                        TID = TIDBuffer[LoopVariable][0];
                        EPC = TIDBuffer[LoopVariable][1].substring(4);
                    }
                }
            }

            Collision = EPCs.size() != 1;

            if (Collision) {

                if (edit) {
                    status.setText(status.getText() + "تعداد تگ های یافت شده بیشتر از یک عدد است");
                } else {
                    if (EPCs.size() == 0) {
                        status.setText(status.getText() + "هیچ تگ جدیدی یافت نشد");
                    } else {
                        status.setText(status.getText() + "تعداد تگ های جدید یافت شده بیشتر از یک عدد است");
                    }
                }

                //beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                status.setBackgroundColor(Color.RED);
            } else {
                status.setText(status.getText() + "اسکن اول با موفقیت انجام شد" + "\nTID: " + TID + "\nEPC: " + EPC);
                isOK = true;
            }
        }
        catch (NullPointerException e) {
            status.setText(status.getText() + "هیچ تگی یافت نشد");
            //beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            status.setBackgroundColor(Color.RED);
        }

        status.setText(status.getText() + "\n" + String.valueOf(TIDBufferSize) + "\n");

        if(!isOK) {

            TIDBuffer = new String[20][10];
            EPCs.clear();
            status.setText(status.getText() + "\n");

            Thread.sleep(100);

            RF.startInventoryTag(0, 0);

            Thread.sleep(900);

            for (LoopVariable = 0; LoopVariable < 10; LoopVariable++) {
                TIDBuffer[LoopVariable] = RF.readTagFromBuffer();
            }

            RF.stopInventory();

            try {

                for (LoopVariable = 0; LoopVariable < 10; LoopVariable++) {
                    if (edit) {
                        EPCs.put(TIDBuffer[LoopVariable][1], 1);
                        TID = TIDBuffer[LoopVariable][0];
                        EPC = TIDBuffer[LoopVariable][1].substring(4);
                    } else {
                        if (!(TIDBuffer[LoopVariable][1].startsWith("30", 4))) {
                            EPCs.put(TIDBuffer[LoopVariable][1], 1);
                            TID = TIDBuffer[LoopVariable][0];
                            EPC = TIDBuffer[LoopVariable][1].substring(4);
                        }
                    }
                }

                Collision = EPCs.size() != 1;

                if (Collision) {

                    if (edit) {
                        status.setText(status.getText() + "تعداد تگ های یافت شده بیشتر از یک عدد است");
                    } else {
                        if (EPCs.size() == 0) {
                            status.setText(status.getText() + "هیچ تگ جدیدی یافت نشد");
                        } else {
                            status.setText(status.getText() + "تعداد تگ های جدید یافت شده بیشتر از یک عدد است");
                        }
                    }

                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                    status.setBackgroundColor(Color.RED);
                    return;
                }

                status.setText(status.getText() + "اسکن دوم با موفقیت انجام شد" + "\nTID: " + TID + "\nEPC: " + EPC);
            }
            catch (NullPointerException e) {
                status.setText(status.getText() + "هیچ تگی یافت نشد");
                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                status.setBackgroundColor(Color.RED);
                return;
            }
        }

        DataBase.Barcode = BarcodeID;
        DataBase.run = true;

        while (DataBase.run) {}

        if (!DataBase.status) {

            status.setText(status.getText() + "\nخطا در دیتابیس\n" + DataBase.Response + "\n");
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            status.setBackgroundColor(Color.RED);
            return;
        }

        while (!RF.setPower(30)){}
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

            if(RF.writeData("00000000", RFIDWithUHF.BankEnum.TID, 0, 96, TID, RFIDWithUHF.BankEnum.UII, 2, 6, New)) {
                break;
            }
        }

        String EPCVerify = null;

        int o;

        for(o=0; o < 15; o++) {

            try {
                EPCVerify = RF.readData("00000000", RFIDWithUHF.BankEnum.TID, 0, 96, TID, RFIDWithUHF.BankEnum.UII, 2, 6).toLowerCase();
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
        while (!RF.setPower(RFPower)) {
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
        status.setText(status.getText() + "\nHeader: " + headerNumber);
        status.setText(status.getText() + "\nFilter: " + filterNumber);
        status.setText(status.getText() + "\nPartition: " + partitionNumber);
        status.setText(status.getText() + "\nCompany number: " + companyNumber);
        status.setText(status.getText() + "\nItem number: " + itemNumber);
        status.setText(status.getText() + "\nSerial number: " + serialNumber);
        status.setText(status.getText() + "\nNew EPC: " + New);
        counterValue++;
        counterValueModified++;
        numberOfWritten.setText("تعداد تگ های برنامه ریزی شده: " + (counterValue - counterMinValue));
        numberOfWrittenModified.setText("مقدار شمارنده: " + counterValueModified);

        if(counterValue >= counterMaxValue) {
            isAddNewOK = false;
        }

        ContentValues val = new ContentValues();
        val.put("value", counterValue);
        val.put("counterModified", counterValueModified);
        counter.update("counterDatabase", val, null, null);

    }

    @SuppressLint("SetTextI18n")
    public void CounterClearButton(View view) {
        counterValueModified = 0;
        numberOfWrittenModified.setText("مقدار شمارنده: " + counterValueModified);

        ContentValues val = new ContentValues();
        val.put("counterModified", counterValueModified);
        counter.update("counterDatabase", val, null, null);
    }

    public void changeOption(View view) {
        edit = editOption.isChecked();
    }
}