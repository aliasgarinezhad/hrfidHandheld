package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

public class filterChanging extends AppCompatActivity {

    public static RFIDWithUHF RF;
    public ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    public String EPC;
    public String TID;
    public static int RFPower = 5;
    public static String tagPassword = "00000000";
    public databaseHelperClass2 databaseHelper2 = new databaseHelperClass2(this);
    public static SQLiteDatabase counter;
    public Cursor counterCursor;
    public static boolean isAddNewOK = true;
    public static Toast warning;
    public TextView status;
    public TextView filterText;
    public static int filterNumber = 0;  // 3bit
    public boolean isInit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_changing);
        status = (TextView) findViewById(R.id.section_label);
        warning = Toast.makeText(this, "", Toast.LENGTH_LONG);
        filterText = (TextView) findViewById(R.id.filterTextView);
    }

    @Override
    protected void onResume() {

        super.onResume();

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        if (!RF.init()) {
            RF.free();
            RF.init();
        }
        isInit = true;

        RF.setEPCTIDMode(true);
        RF.setPower(5);
        RF.setFrequencyMode((byte) 4);
        RF.setRFLink(0);

        counter = databaseHelper2.getWritableDatabase();
        counterCursor = counter.rawQuery("select * from counterDatabase", null);

        if(counterCursor.getCount() <= 0) {
            warning.setText("دیتای برنامه پاک شده است. جهت کسب اطلاعات بیشتر با توسعه دهنده تماس بگیرید");
            warning.show();
            isAddNewOK = false;
        }
        else {
            counterCursor.moveToFirst();
            filterNumber = counterCursor.getInt(4);
        }
        counterCursor.close();

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
        RF.free();
        counter.close();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {

            try {
                filterChange();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        else if (keyCode == 4) {
            RF.free();
            counter.close();
            finish();
        }
        return true;
    }

    @SuppressLint("SetTextI18n")
    public void filterChange() throws InterruptedException {

        String[][] TIDBuffer = new String[20][10];
        int LoopVariable;
        boolean Collision;

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

        char[] temp = EPC.toCharArray();

        if(filterNumber == 0) {
            temp[3] = '0';
        }
        else if(filterNumber == 1) {
            temp[3] = '4';
        }
        EPC = String.valueOf(temp);

        if (RF.writeData("00000000", RFIDWithUHF.BankEnum.TID, 0, 6, TID, RFIDWithUHF.BankEnum.UII, 2, 6, EPC)) {

            Thread.sleep(100);

            if(!EPC.equals(RF.inventorySingleTag().substring(4).toLowerCase())) {
                status.setText(status.getText() + "\nConfirmation fail");
                status.setText(status.getText() + RF.inventorySingleTag().substring(4));
                status.setText(status.getText() + EPC);

                beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                status.setBackgroundColor(Color.RED);
                return;
            }

            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            status.setBackgroundColor(Color.GREEN);

        } else {

            status.setText(status.getText() + "\nWrite fail");
            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            status.setBackgroundColor(Color.RED);
        }
    }
}