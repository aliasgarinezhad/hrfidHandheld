package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.exception.ConfigurationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class WareHouseScanningFindingProduct extends AppCompatActivity {

    GetProductEPCAPI api;
    RFIDWithUHFUART RF;
    Map<String, Integer> EPCTableFinding = new HashMap<>();
    ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    int findingPower = 30;
    TextView status;
    TextView powerText;
    SeekBar powerSeekBar;
    TextView numberOfFoundText;
    TextView stuffSpec;
    long stuffCode;
    WebView picture;
    CheckBox option;

    String stuffPrimaryCode;
    String stuffRFIDCode;
    JSONArray subStuffs;
    JSONObject stuff;
    Map<String, Integer> EPCTableFindingMatched = new HashMap<>();
    String temp;
    JSONObject temp2;
    WebSettings setting;
    Button update;

    SharedPreferences table;
    SharedPreferences.Editor tableEditor;

    boolean isChecked = true;
    boolean findingInProgress = false;
    boolean updateDatabaseInProgress;
    boolean readEnable = false;

    Handler databaseBackgroundTaskHandler = new Handler();

    Runnable databaseBackgroundTask = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {

            if (findingInProgress) {

                UHFTAGInfo uhftagInfo;

                while (true) {

                    uhftagInfo = RF.readTagFromBuffer();

                    if (uhftagInfo != null) {
                        EPCTableFinding.put(uhftagInfo.getEPC(), 1);
                    } else {
                        break;
                    }
                }

                if (EPCTableFinding.size() > 0) {

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
                            EPCTableFindingMatched.put(EPCHexString, 1);
                            flag = true;
                        }
                    }

                    numberOfFoundText.setText(String.valueOf(EPCTableFindingMatched.size()));
                    status.setText("");
                    status.setText("در حال جست و جو ...");

                    for (Map.Entry<String, Integer> valid : EPCTableFindingMatched.entrySet()) {
                        status.setText(status.getText() + "\n" + valid.getKey());
                    }

                    if (flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                    }

                    EPCTableFinding.clear();
                    databaseBackgroundTaskHandler.postDelayed(databaseBackgroundTask, 1000);
                } else {
                    databaseBackgroundTaskHandler.postDelayed(databaseBackgroundTask, 1000);
                }
            }

            if (WarehouseScanningActivity.databaseInProgress) {

                if (!WarehouseScanningActivity.API.status) {
                    status.setText("در حال ارسال به سرور ");
                    databaseBackgroundTaskHandler.postDelayed(this, 1000);
                } else if (!WarehouseScanningActivity.API2.status) {
                    if(!WarehouseScanningActivity.API2.isAlive()) {
                        WarehouseScanningActivity.API2.start();
                    }
                    status.setText("در حال دریافت اطلاعات از سرور ");
                    databaseBackgroundTaskHandler.postDelayed(this, 1000);
                } else {
                    try {

                        for (int i = 0; i < WarehouseScanningActivity.API2.stuffs.length(); i++) {
                            temp = WarehouseScanningActivity.API2.stuffs.getString(i);
                            subStuffs = WarehouseScanningActivity.API2.conflicts.getJSONArray(temp);

                            for (int j = 0; j < subStuffs.length(); j++) {

                                temp2 = subStuffs.getJSONObject(j);
                                if (temp2.getString("BarcodeMain_ID").equals(stuffPrimaryCode)) {
                                    ReadingResultActivity.index = WarehouseScanningActivity.API2.stuffs.getString(i);
                                    readingResultSubActivity.subIndex = j;
                                    i = WarehouseScanningActivity.API2.stuffs.length() + 10;
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
        update = findViewById(R.id.updateButton);

        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if (!readEnable) {
                    switch (progress) {
                        case 0:
                            findingPower = 5;
                            break;
                        case 1:
                            findingPower = 10;
                            break;
                        case 2:
                            findingPower = 15;
                            break;
                        case 3:
                            findingPower = 20;
                            break;
                        case 4:
                            findingPower = 30;
                            break;
                    }
                    powerText.setText("قدرت سیگنال(" + findingPower + ")");
                } else {
                    RF.stopInventory();
                    switch (progress) {
                        case 0:
                            findingPower = 5;
                            break;
                        case 1:
                            findingPower = 10;
                            break;
                        case 2:
                            findingPower = 15;
                            break;
                        case 3:
                            findingPower = 20;
                            break;
                        case 4:
                            findingPower = 30;
                            break;
                    }
                    powerText.setText("قدرت سیگنال(" + findingPower + ")");

                    while (!RF.setPower(findingPower)) {
                    }
                    RF.startInventoryTag(0, 0, 0);
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
            RF = RFIDWithUHFUART.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        table = PreferenceManager.getDefaultSharedPreferences(this);
        tableEditor = table.edit();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        updateDatabaseInProgress = false;

        while (!RF.setPower(findingPower)) {
        }

        try {

            subStuffs = WarehouseScanningActivity.API2.conflicts.getJSONArray(ReadingResultActivity.index);
            stuff = subStuffs.getJSONObject(readingResultSubActivity.subIndex);

            if (stuff.getBoolean("status")) {
                stuffSpec.setText(stuff.getString("productName") + "\n" +
                        "کد محصول: " + stuff.getString("K_Bar_Code") + "\n" +
                        "بارکد: " + stuff.getString("KBarCode") + "\n" +
                        "تعداد اضافی: " + stuff.getString("diffCount") + "\n" +
                        "تعداد اسکن شده: " + stuff.getString("handheldCount") + "\n" +
                        "تعداد کل: " + stuff.getString("dbCount"));

            } else {
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

        api = new GetProductEPCAPI();
        api.id = String.valueOf(WarehouseScanningActivity.ID);
        api.primaryCode = stuffPrimaryCode;
        api.rfidCode = stuffRFIDCode;
        api.start();
        while (api.run) {}

        if (!api.status) {
            stuffSpec.setText(stuffSpec.getText() + "\n" + api.response);
        } else {
            stuffSpec.setText(stuffSpec.getText() + "\n" + api.response);
        }

        WarehouseScanningActivity.databaseInProgress = false;
        switch (findingPower) {
            case 5:
                powerSeekBar.setProgress(0);
                break;
            case 10:
                powerSeekBar.setProgress(1);
                break;
            case 15:
                powerSeekBar.setProgress(2);
                break;
            case 20:
                powerSeekBar.setProgress(3);
                break;
            case 30:
                powerSeekBar.setProgress(4);
                break;
        }
        powerText.setText("قدرت سیگنال(" + findingPower + ")");

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

        if (updateDatabaseInProgress) {
            return true;
        }

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {

            if (event.getRepeatCount() == 0) {

                status.setText("");

                if (!readEnable) {

                    update.setBackgroundColor(Color.GRAY);

                    EPCTableFinding.clear();
                    if (!isChecked) {
                        EPCTableFindingMatched.clear();
                    }

                    while (!RF.setPower(findingPower)) {
                    }
                    readEnable = true;
                    RF.startInventoryTag(0, 0, 0);

                    status.setText("در حال جست و جو ...");
                    findingInProgress = true;
                    databaseBackgroundTaskHandler.postDelayed(databaseBackgroundTask, 1000);
                } else {

                    update.setBackgroundColor(getColor(R.color.Primary));

                    databaseBackgroundTaskHandler.removeCallbacks(databaseBackgroundTask);

                    readEnable = false;
                    RF.stopInventory();

                    findingInProgress = false;

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

                            EPCTableFindingMatched.put(EPCHexString, 1);
                            flag = true;
                        }
                    }
                    numberOfFoundText.setText(String.valueOf(EPCTableFindingMatched.size()));

                    status.setText("");
                    for (Map.Entry<String, Integer> valid : EPCTableFindingMatched.entrySet()) {
                        status.setText(status.getText() + "\n" + valid.getKey());
                    }

                    if (flag) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                    }

                    EPCTableFinding.clear();
                }
            }

        } else if (keyCode == 4) {

            if (readEnable) {
                RF.stopInventory();
                readEnable = false;
            }

            findingInProgress = false;
            databaseBackgroundTaskHandler.removeCallbacks(databaseBackgroundTask);

            finish();
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (readEnable) {
            RF.stopInventory();
            readEnable = false;
        }
    }

    public void clearEPCs(View view) {

        EPCTableFinding.clear();
        EPCTableFindingMatched.clear();
        status.setText("");
        numberOfFoundText.setText("0");
    }

    @SuppressLint("SetTextI18n")
    public void updateDatabase(View view) {

        if (findingInProgress) {
            return;
        }

        updateDatabaseInProgress = true;

        JSONObject tableJson;

        WarehouseScanningActivity.EPCTable.putAll(EPCTableFindingMatched);
        WarehouseScanningActivity.EPCTableValid.putAll(EPCTableFindingMatched);

        tableJson = new JSONObject(WarehouseScanningActivity.EPCTableValid);
        tableEditor.putString(String.valueOf(WarehouseScanningActivity.warehouseID), tableJson.toString());
        tableEditor.commit();

        WarehouseScanningActivity.API = new APIReadingEPC();
        WarehouseScanningActivity.API2 = new APIReadingConflicts();
        WarehouseScanningActivity.API.start();

        WarehouseScanningActivity.databaseInProgress = true;
        databaseBackgroundTaskHandler.post(databaseBackgroundTask);
    }

    public void optionChange(View view) {

        isChecked = option.isChecked();
    }
}