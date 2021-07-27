
package com.jeanwest.reader;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.exception.ConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class reading extends AppCompatActivity {

    public static RFIDWithUHFUART RF;
    ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    public TextView status;
    TextView percentage;
    TextView powerText;
    SeekBar powerSeekBar;
    Toast response;
    public static Map<String, Integer> EPCTable = new HashMap<String, Integer>();
    public static Map<String, Integer> EPCTableValid = new HashMap<String, Integer>();
    public static Integer ID;
    public static APIReadingEPC API;
    public static APIReadingConflicts API2;
    private boolean readingInProgress = false;
    public static boolean databaseInProgress = false;
    private boolean processingInProgress = false;
    Button button;
    CircularProgressBar circularProgressBar;
    Intent intent;
    public static int allStuffs = 0;
    int EPCLastLength = 0;
    int readingPower = 30;

    SharedPreferences table;
    SharedPreferences.Editor tableEditor;

    String temp;
    JSONArray subStuffs;
    JSONObject temp2;

    public static boolean fromLogin = false;

    String header;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @SuppressLint({"SetTextI18n", "ResourceAsColor"})
        @Override
        public void run() {

            if (readingInProgress) {

                UHFTAGInfo uhftagInfo;

                while(true) {

                    uhftagInfo = RF.readTagFromBuffer();

                    if(uhftagInfo != null) {
                        EPCTable.put(uhftagInfo.getEPC(), 1);
                    }
                    else {
                        break;
                    }
                }

                status.setText("کد شعبه: " + userSpecActivity.departmentInfoID + '\n');
                if (userSpecActivity.wareHouseID == 1) {
                    status.setText(status.getText() + "در سطح فروش" + '\n');
                } else {
                    status.setText(status.getText() + "در سطح انبار" + '\n');
                }
                status.setText(status.getText() + "سرعت اسکن (تگ بر ثانیه): " + (EPCTable.size() - EPCLastLength) + '\n');

                if (EPCTable.size() > EPCLastLength + 100) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 700);
                    EPCLastLength = EPCTable.size();
                }
                if (EPCTable.size() > EPCLastLength + 30) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                    EPCLastLength = EPCTable.size();
                }
                if (EPCTable.size() > EPCLastLength + 10) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 300);
                    EPCLastLength = EPCTable.size();
                }
                if (EPCTable.size() > EPCLastLength) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                    EPCLastLength = EPCTable.size();
                }

                timerHandler.postDelayed(this, 1000);

            } else if (processingInProgress) {

                EPCTableValid.clear();

                for (Map.Entry<String, Integer> EPC : EPCTable.entrySet()) {

                    Log.e("errorm", EPC.getKey());

                    if(EPC.getKey().length() > 0) {

                        header = EPC.getKey().substring(0, 2);

                        if (header.equals("30")) {
                            EPCTableValid.put(EPC.getKey(), 1);
                        }
                    }
                    else {
                        Log.e("errorx", EPC.getKey());
                    }
                }

                status.setText("کد شعبه: " + userSpecActivity.departmentInfoID + '\n');
                if (userSpecActivity.wareHouseID == 1) {
                    status.setText(status.getText() + "در سطح فروش" + '\n');
                } else {
                    status.setText(status.getText() + "در سطح انبار" + '\n');
                }

                status.setText(status.getText() + "سرعت اسکن (تگ بر ثانیه): " + '0' + '\n');
                status.setText(status.getText() + "تعداد کالا های پیدا شده: " + EPCTableValid.size() + '/' + allStuffs + '\n');
                circularProgressBar.setProgress((float) ((EPCTableValid.size() * 100) / allStuffs));
                percentage.setText(String.valueOf((float) ((EPCTableValid.size() * 100) / allStuffs)) + '%');
                //status.setText(status.getText() + "تعداد تگ های خام: " + EPCTableInvalid.size() + "\n");

                readingInProgress = false;
                databaseInProgress = false;
                processingInProgress = false;
                button.setBackgroundColor(getColor(R.color.Primary));
            } else if (databaseInProgress) {

                if (!API.status) {
                    status.setText("در حال ارسال به سرور ");
                    timerHandler.postDelayed(this, 1000);
                } else if (!API2.status) {
                    status.setText("در حال دریافت اطلاعات از سرور ");
                    timerHandler.postDelayed(this, 1000);
                } else {
                    startActivity(intent);
                }
            } else {

                response.setText("خطا در دیتابیس" + '\n' + API.Response);
                response.show();

                status.setText("کد شعبه: " + userSpecActivity.departmentInfoID + '\n');
                if (userSpecActivity.wareHouseID == 1) {
                    status.setText(status.getText() + "در سطح فروش" + '\n');
                } else {
                    status.setText(status.getText() + "در سطح انبار" + '\n');
                }
                status.setText(status.getText() + "سرعت اسکن (تگ بر ثانیه): " + '0' + '\n');
                status.setText(status.getText() + "تعداد کالا های پیدا شده: " + EPCTableValid.size() + '/' + allStuffs + '\n');
            }
        }
    };

    @SuppressLint({"SetTextI18n", "CommitPrefEdits"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading);
        status = findViewById(R.id.section_label);
        response = Toast.makeText(this, "", Toast.LENGTH_LONG);
        button = findViewById(R.id.buttonReading);
        intent = new Intent(this, readingResultActivity.class);
        circularProgressBar = findViewById(R.id.circularProgressBar);
        percentage = findViewById(R.id.progressText);
        powerText = findViewById(R.id.readingPowerTextView);
        powerSeekBar = findViewById(R.id.readingPowerSeekBar);

        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if (!readingInProgress) {
                    readingPower = progress + 5;
                    powerText.setText("اندازه توان(" + readingPower + ")");
                } else {
                    powerSeekBar.setProgress(readingPower - 5);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        circularProgressBar.setProgressMax(100f);
        circularProgressBar.setProgressBarColor(getColor(R.color.Primary));
        circularProgressBar.setBackgroundProgressBarColor(Color.GRAY);
        circularProgressBar.setProgressBarWidth(15f); // in DP
        circularProgressBar.setBackgroundProgressBarWidth(7f); // in DP
        circularProgressBar.setRoundBorder(true);
        circularProgressBar.setProgressDirection(CircularProgressBar.ProgressDirection.TO_RIGHT);

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

        EPCTable.clear();
        EPCTableValid.clear();

        EPCTableValid = new Gson().fromJson(table.getString(String.valueOf(userSpecActivity.wareHouseID), ""), HashMap.class);

        if (EPCTableValid == null) {
            EPCTableValid = new HashMap<String, Integer>();
        } else {
            EPCTable.putAll(EPCTableValid);
            EPCLastLength = EPCTable.size();
        }

        while (!RF.setEPCMode()) {
        }

        if (RF.getPower() != readingPower) {

            while (!RF.setPower(readingPower)) {
            }
        }

        API = new APIReadingEPC();
        API2 = new APIReadingConflicts();
        //readTask = new readingThread();

        databaseInProgress = false;
        readingInProgress = false;
        processingInProgress = false;

        API.status = false;
        API2.status = false;

        API.stop = false;
        API.start();

        API2.stop = false;
        API2.start();

        if (fromLogin) {

            API2.status = false;
            API2.run = true;
            while (API2.run) {
            }

            allStuffs = 0;
            for (int i = 0; i < API2.stuffs.length(); i++) {

                try {
                    temp = API2.stuffs.getString(i);
                    subStuffs = API2.conflicts.getJSONArray(temp);

                    for (int j = 0; j < subStuffs.length(); j++) {
                        temp2 = subStuffs.getJSONObject(j);
                        allStuffs += temp2.getInt("dbCount");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            fromLogin = false;
        }

        status.setText("کد شعبه: " + userSpecActivity.departmentInfoID + '\n');
        if (userSpecActivity.wareHouseID == 1) {
            status.setText(status.getText() + "در سطح فروش" + '\n');
        } else {
            status.setText(status.getText() + "در سطح انبار" + '\n');
        }
        status.setText(status.getText() + "سرعت اسکن (تگ بر ثانیه): " + '0' + '\n');
        status.setText(status.getText() + "تعداد کالا های پیدا شده: " + EPCTableValid.size() + '/' + allStuffs + '\n');
        circularProgressBar.setProgress((float) ((EPCTableValid.size() * 100) / allStuffs));
        percentage.setText(String.valueOf((float) ((EPCTableValid.size() * 100) / allStuffs)) + '%');

        powerText.setText("اندازه توان(" + readingPower + ")");
        powerSeekBar.setProgress(readingPower - 5);

    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139 || keyCode == 293) {

            if (event.getRepeatCount() == 0) {

                if (!readingInProgress) {

                    while (!RF.setPower(readingPower)) {
                    }
                    databaseInProgress = false;
                    processingInProgress = false;
                    readingInProgress = true;
                    button.setBackgroundColor(Color.GRAY);
                    //readTask.readEnable = true;
                    RF.startInventoryTag(0, 0, 0);

                    timerHandler.post(timerRunnable);
                } else {
                    timerHandler.removeCallbacks(timerRunnable);
                    RF.stopInventory();

                    databaseInProgress = false;
                    readingInProgress = false;
                    processingInProgress = true;
                    status.setText("در حال پردازش ...");
                    timerHandler.postDelayed(timerRunnable, 500);
                }
            }

        } else if (keyCode == 4) {

            if (readingInProgress) {
                RF.stopInventory();
                readingInProgress = false;
            }
            API.stop = true;
            API2.stop = true;
            finish();
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();

        JSONObject tableJson;

        if (readingInProgress) {
            RF.stopInventory();
            readingInProgress = false;
        }
        API.stop = true;
        API2.stop = true;

        tableJson = new JSONObject(EPCTableValid);
        tableEditor.putString(String.valueOf(userSpecActivity.wareHouseID), tableJson.toString());
        tableEditor.putInt(String.valueOf(userSpecActivity.departmentInfoID) + userSpecActivity.wareHouseID, ID);
        tableEditor.commit();
    }

    @SuppressLint("SetTextI18n")
    public void sendFile(View view) {

        if (readingInProgress || processingInProgress) {
            return;
        }
        readingResultActivity.indexNumber = 0;
        API.status = false;
        API2.status = false;
        API.run = true;
        databaseInProgress = true;

        timerHandler.post(timerRunnable);
    }

    @SuppressLint("SetTextI18n")
    public void clearAll(View view) {

        AlertDialog alertDialog;
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle("تمام اطلاعات قبلی پاک می شود");
        alertBuilder.setMessage("آیا ادامه می دهید؟");
        alertBuilder.setPositiveButton("بله", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                EPCTable.clear();
                EPCTableValid.clear();
                tableEditor.putString(String.valueOf(userSpecActivity.wareHouseID), "");
                tableEditor.putInt(String.valueOf(userSpecActivity.departmentInfoID) + userSpecActivity.departmentInfoID, ID);
                tableEditor.commit();
                EPCLastLength = 0;

                status.setText("کد شعبه: " + userSpecActivity.departmentInfoID + '\n');
                if (userSpecActivity.wareHouseID == 1) {
                    status.setText(status.getText() + "در سطح فروش" + '\n');
                } else {
                    status.setText(status.getText() + "در سطح انبار" + '\n');
                }

                status.setText(status.getText() + "سرعت اسکن (تگ بر ثانیه): " + '0' + '\n');
                status.setText(status.getText() + "تعداد کالا های پیدا شده: " + EPCTableValid.size() + '/' + allStuffs + '\n');
                circularProgressBar.setProgress((float) ((EPCTableValid.size() * 100) / allStuffs));
                percentage.setText(String.valueOf((float) ((EPCTableValid.size() * 100) / allStuffs)) + '%');
            }
        });
        alertBuilder.setNegativeButton("خیر", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        alertDialog = alertBuilder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dlg) {
                alertDialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // set title and message direction to RTL
            }
        });
        alertDialog.show();

    }
}