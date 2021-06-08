package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class readingResultSubSubActivity extends AppCompatActivity {

    APIFindingEPC database = new APIFindingEPC();
    public static RFIDWithUHF RF;
    public static Map<String, Integer> EPCTableFinding = new HashMap<>();
    public ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    private int findingPower = 5;
    TextView status;
    TextView powerText;
    SeekBar powerSeekBar;
    TextView numberOfFoundText;
    TextView stuffSpec;
    findingThread findTask = new findingThread();
    private long stuffCode;
    WebView picture;
    CheckBox option;

    public static String stuffPrimaryCode;
    public static String stuffRFIDCode;
    JSONArray subStuffs;
    JSONObject stuff;
    Map<String, Integer> EPCTableFindingMatched = new HashMap<>();
    String temp;
    JSONObject temp2;
    WebSettings setting;

    boolean isChecked = true;

    Handler databaseBackgroundTaskHandler = new Handler();

    Runnable databaseBackgroundTask = new Runnable() {
        @Override
        public void run() {
            if (reading.databaseInProgress) {

                if(!reading.API.status) {
                    status.setText("در حال ارسال به سرور ");
                    databaseBackgroundTaskHandler.postDelayed(this, 1000);
                }
                else if(!reading.API2.status) {
                    status.setText("در حال دریافت اطلاعات از سرور ");
                    databaseBackgroundTaskHandler.postDelayed(this, 1000);
                }
                else {
                    try {

                        for(int i = 0; i < reading.API2.stuffs.length(); i++) {
                            temp = reading.API2.stuffs.getString(i);
                            subStuffs = reading.API2.conflicts.getJSONArray(temp);

                            for (int j = 0; j < subStuffs.length(); j++) {

                                temp2 = subStuffs.getJSONObject(j);
                                if(temp2.getString("BarcodeMain_ID").equals(stuffPrimaryCode)){
                                    readingResultActivity.index = i;
                                    readingResultSubActivity.subIndex = j;
                                    i = reading.API2.stuffs.length() + 10;
                                    j = subStuffs.length() + 10;
                                }
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    EPCTableFinding.clear();
                    EPCTableFindingMatched.clear();
                    status.setText("");
                    onResume();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_result_sub_sub);
        status = findViewById(R.id.section_label);
        stuffSpec = findViewById(R.id.result);
        picture = findViewById(R.id.pictureView);
        powerText = findViewById(R.id.findingPowerTextView);
        powerSeekBar = findViewById(R.id.findingPowerSeekBar);
        numberOfFoundText = findViewById(R.id.numberOfFoundTextView);
        option = findViewById(R.id.checkBox);

        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if (!findTask.readEnable) {
                    findingPower = progress + 5;
                    powerText.setText("قدرت سیگنال(" + findingPower + ")");
                } else {
                    powerSeekBar.setProgress(findingPower - 5);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

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

        if (!findTask.isAlive()) {
            findTask.start();
        }

        while (!RF.setPower(findingPower)) {
        }

        if (!database.isAlive()) {
            database.start();
        }

        try {

            subStuffs = reading.API2.conflicts.getJSONArray(reading.API2.stuffs.getString(readingResultActivity.index));
            stuff = subStuffs.getJSONObject(readingResultSubActivity.subIndex);

            if (stuff.getBoolean("status")) {
                stuffSpec.setText(stuff.getString("productName") + "\n" +
                        "کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                        "بارکد: " + stuff.getString("KBarCode") + "\n" +
                        "تعداد اضافی: " + stuff.getString("diffCount") + "\n" +
                        "تعداد اسکن شده: " + stuff.getString("handheldCount") + "\n" +
                        "تعداد کل: " + stuff.getString("dbCount"));

            }
            else {
                stuffSpec.setText(stuff.getString("productName") + "\n" +
                        "کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                        "بارکد: " + stuff.getString("KBarCode") + "\n" +
                        "تعداد اسکن نشده: " + stuff.getString("diffCount") + "\n" +
                        "تعداد اسکن شده: " + stuff.getString("handheldCount") + "\n" +
                        "تعداد کل: " + stuff.getString("dbCount"));
            }

            picture.loadUrl(stuff.getString("ImgUrl"));
            stuffPrimaryCode = stuff.getString("BarcodeMain_ID");
            stuffRFIDCode = stuff.getString("RFID");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        setting = picture.getSettings();
        setting.setUseWideViewPort(true);
        setting.setLoadWithOverviewMode(true);
        picture.setFocusable(false);

        database.run = true;
        while (database.run) {
        }

        if (!database.status) {
            stuffSpec.setText(stuffSpec.getText() + "\n" + database.Response);
        } else {
            stuffSpec.setText(stuffSpec.getText() + "\n" + database.Response);
        }

    }

    int header;
    int companyNumber;
    long itemNumber;
    boolean flag;
    long EPCInt1;
    long EPCInt2;
    long EPCInt3;
    String EPCHexString;
    String EPCBinaryString;
    String EPCBinaryString1;
    String EPCBinaryString2;
    String EPCBinaryString3;

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {

            if (event.getRepeatCount() == 0) {

                status.setText("");

                if (!findTask.readEnable) {

                    if(!isChecked) {
                        EPCTableFinding.clear();
                        EPCTableFindingMatched.clear();
                    }

                    while (!RF.setPower(findingPower)) {
                    }
                    findTask.readEnable = true;

                    status.setText("در حال جست و جو ...");
                } else {
                    findTask.readEnable = false;
                    while (!findTask.finished) {
                    }

                    flag = false;

                    for (Map.Entry<String, Integer> EPC : EPCTableFinding.entrySet()) {

                        EPCHexString = EPC.getKey();
                        EPCInt1 = Long.parseLong(EPCHexString.substring(0, 8), 16);
                        EPCInt2 = Long.parseLong(EPCHexString.substring(8, 16), 16);
                        EPCInt3 = Long.parseLong(EPCHexString.substring(16, 24), 16);

                        EPCBinaryString1 = Long.toBinaryString(EPCInt1);
                        EPCBinaryString1 = String.format("%32s", EPCBinaryString1).replaceAll(" ", "0");
                        EPCBinaryString2 = Long.toBinaryString(EPCInt2);
                        EPCBinaryString2 = String.format("%32s", EPCBinaryString2).replaceAll(" ", "0");
                        EPCBinaryString3 = Long.toBinaryString(EPCInt3);
                        EPCBinaryString3 = String.format("%32s", EPCBinaryString3).replaceAll(" ", "0");

                        EPCBinaryString = EPCBinaryString1 + EPCBinaryString2 + EPCBinaryString3;

                        header = Integer.parseInt(EPCBinaryString.substring(0, 8), 2);
                        companyNumber = Integer.parseInt(EPCBinaryString.substring(14, 26), 2);
                        itemNumber = Long.parseLong(EPCBinaryString.substring(26, 58), 2);

                        if (companyNumber == 100) {
                            stuffCode = Long.parseLong(stuffPrimaryCode);
                        } else if (companyNumber == 101) {
                            stuffCode = Long.parseLong(stuffRFIDCode);
                        }
                        if (header == 48 && itemNumber == stuffCode) {

                            status.setText(status.getText() + "\n" + EPCHexString);
                            EPCTableFindingMatched.put(EPCHexString, 1);
                            flag = true;
                        }
                    }
                    numberOfFoundText.setText(String.valueOf(EPCTableFindingMatched.size()));
                    if (!flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                    } else {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                    }
                }
            }

        } else if (keyCode == 4) {

            if(findTask.readEnable) {
                RF.stopInventory();
                findTask.readEnable = false;
            }
            finish();
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(findTask.readEnable) {
            RF.stopInventory();
            findTask.readEnable = false;
        }
    }

    public void clearEPCs(View view) {

        EPCTableFinding.clear();
        EPCTableFindingMatched.clear();
        status.setText("");
    }

    @SuppressLint("SetTextI18n")
    public void updateDatabase(View view) {

        reading.EPCTable.putAll(EPCTableFindingMatched);
        reading.EPCTableValid.putAll(EPCTableFindingMatched);
        reading.API.status = false;
        reading.API2.status = false;
        reading.API.run = true;
        reading.databaseInProgress = true;
        databaseBackgroundTaskHandler.post(databaseBackgroundTask);
    }

    public void optionChange(View view) {

        isChecked = option.isChecked();
    }
}