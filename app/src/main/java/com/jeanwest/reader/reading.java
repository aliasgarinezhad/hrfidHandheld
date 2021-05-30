
package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mikhaellopez.circularprogressbar.CircularProgressBar;
import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class reading extends AppCompatActivity {

    public static RFIDWithUHF RF;
    public ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    public TextView status;
    TextView percentage;
    TextView powerText;
    SeekBar powerSeekBar;
    public readingThread readTask = new readingThread();
    Toast response;
    public static Map<String, Integer> EPCTable = new HashMap<String, Integer>();
    public static Map<String, Integer> EPCTableFilter0 = new HashMap<String, Integer>();
    public static Map<String, Integer> EPCTableFilter1 = new HashMap<String, Integer>();
    public Map<String, Integer> EPCTableFilterOther = new HashMap<String, Integer>();
    boolean step = false;
    public static Integer ID;
    APIReadingFile API = new APIReadingFile();
    public static APIReadingResult API2 = new APIReadingResult();
    private boolean readingInProgress = false;
    public static boolean databaseInProgress = false;
    private boolean processingInProgress = false;
    Button button;
    CircularProgressBar circularProgressBar;
    Intent intent;
    int allStuffs = 0;
    int EPCLastLength = 0;
    int readingPower = 30;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @SuppressLint("SetTextI18n")
        @Override
        public void run() {

            if(readingInProgress) {
                status.setText("تعداد کالاهای اسکن شده: " + EPCTable.size() + "\n");

                if(EPCTable.size() > EPCLastLength) {
                    beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                    EPCLastLength = EPCTable.size();
                }

                timerHandler.postDelayed(this, 1000);
            }

            else if(processingInProgress) {

                EPCTableFilter1.clear();
                EPCTableFilter0.clear();

                for(Map.Entry<String, Integer> EPC : EPCTable.entrySet()) {

                    String header = EPC.getKey().substring(0,2);

                    if(userSpecActivity.API.wareHouseID == 1707 && header.equals("30")) {
                        EPCTableFilter0.put(EPC.getKey(), 1);
                    }
                    else if(userSpecActivity.API.wareHouseID == 1706 && header.equals("30")) {
                        EPCTableFilter1.put(EPC.getKey(), 1);
                    }
                    else {
                        EPCTableFilterOther.put(EPC.getKey(), 1);
                    }
                }

                status.setText("تعداد کل کالاهای اسکن شده: " + EPCTable.size() + '\n');
                if(userSpecActivity.API.wareHouseID == 1706) {
                    status.setText(status.getText() + "تعداد کالا های فروشگاه: " + EPCTableFilter1.size() + '/' + allStuffs + '\n');
                    status.setText(status.getText() + "تعداد کالا های انبار: " + EPCTableFilter0.size() + '\n');
                    circularProgressBar.setProgress((float)((EPCTableFilter1.size() * 100)/allStuffs));
                    percentage.setText(String.valueOf((float)((EPCTableFilter1.size() * 100)/allStuffs)) + '%');

                }
                else if(userSpecActivity.API.wareHouseID == 1707) {
                    status.setText(status.getText() + "تعداد کالا های فروشگاه: " + EPCTableFilter1.size() + '\n');
                    status.setText(status.getText() + "تعداد کالا های انبار: " + EPCTableFilter0.size() + '/' + allStuffs + '\n');
                    circularProgressBar.setProgress((float)((EPCTableFilter0.size() * 100)/allStuffs));
                    percentage.setText(String.valueOf((float)((EPCTableFilter0.size() * 100)/allStuffs)) + '%');
                }
                status.setText(status.getText() + "تعداد تگ های خام: " + EPCTableFilterOther.size() + "\n");
                readingInProgress = false;
                databaseInProgress = true;
                button.setBackgroundColor(Color.BLUE);
                processingInProgress = false;
            }

            else if (databaseInProgress) {

                if(!API.status) {
                    status.setText("در حال ارسال به سرور ");
                    timerHandler.postDelayed(this, 1000);
                }
                else if(!API2.status) {
                    status.setText("در حال دریافت اطلاعات از سرور ");
                    timerHandler.postDelayed(this, 1000);
                }
                else {
                    startActivity(intent);
                }
            }
            else {

                response.setText("خطا در دیتابیس" + '\n' + API.Response);
                response.show();
                status.setText("تعداد کل کالاهای اسکن شده: " + EPCTable.size() + '\n');
                if(userSpecActivity.API.wareHouseID == 1706) {
                    status.setText(status.getText() + "تعداد کالا های فروشگاه: " + EPCTableFilter1.size() + '/' + allStuffs + '\n');
                    status.setText(status.getText() + "تعداد کالا های انبار: " + EPCTableFilter0.size() + '\n');
                }
                else if(userSpecActivity.API.wareHouseID == 1707) {
                    status.setText(status.getText() + "تعداد کالا های فروشگاه: " + EPCTableFilter1.size() + '\n');
                    status.setText(status.getText() + "تعداد کالا های انبار: " + EPCTableFilter0.size() + '/' + allStuffs + '\n');
                }
                status.setText(status.getText() + "تعداد تگ های خام: " + EPCTableFilterOther.size() + "\n");
            }
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading);
        status = (TextView) findViewById(R.id.section_label);
        response = Toast.makeText(this, "", Toast.LENGTH_LONG);
        button = (Button) findViewById(R.id.buttonReading);
        intent = new Intent(this, readingResultActivity.class);
        circularProgressBar = findViewById(R.id.circularProgressBar);
        percentage = (TextView) findViewById(R.id.progressText);
        powerText = findViewById(R.id.readingPowerTextView);
        powerSeekBar = findViewById(R.id.readingPowerSeekBar);

        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if(!readingInProgress) {
                    readingPower = progress + 5;
                    powerText.setText("اندازه توان(" + readingPower + ")");
                }
                else {
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
        circularProgressBar.setProgressBarColor(Color.BLUE);
        circularProgressBar.setBackgroundProgressBarColor(Color.GRAY);
        circularProgressBar.setProgressBarWidth(15f); // in DP
        circularProgressBar.setBackgroundProgressBarWidth(7f); // in DP
        circularProgressBar.setRoundBorder(true);
        circularProgressBar.setProgressDirection(CircularProgressBar.ProgressDirection.TO_RIGHT);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {

        super.onResume();

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        while(!RF.setEPCTIDMode(false)) {}
        while(!RF.setPower(readingPower)) {}
        while(!RF.setFrequencyMode((byte) 4)) {}
        while(!RF.setRFLink(2)) {}

        databaseInProgress = false;
        readingInProgress = false;
        API.status = false;
        API2.status = false;

        if (!API.isAlive()) {
            API.start();
        }

        if (!API2.isAlive()) {
            API2.start();
        }

        if (!readTask.isAlive()) {
            readTask.start();
        }

        API2.status = false;
        API2.run = true;
        while(API2.run){}

        allStuffs = 0;
        for (int i = 0; i<API2.stuffs.length(); i++) {
            String temp;
            JSONArray subStuffs;

            try {
                temp = API2.stuffs.getString(i);
                subStuffs = API2.conflicts.getJSONArray(temp);

                for (int j = 0; j < subStuffs.length(); j++) {
                    JSONObject temp2 = subStuffs.getJSONObject(j);
                    allStuffs += temp2.getInt("dbCount");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        status.setText("تعداد کل کالاهای اسکن شده: " + EPCTable.size() + '\n');


        if(userSpecActivity.API.wareHouseID == 1706) {
            status.setText(status.getText() + "تعداد کالا های فروشگاه: " + EPCTableFilter1.size() + '/' + allStuffs + '\n');
            status.setText(status.getText() + "تعداد کالا های انبار: " + EPCTableFilter0.size() + '\n');
            circularProgressBar.setProgress((float)((EPCTableFilter1.size() * 100)/allStuffs));
            percentage.setText(String.valueOf((float)((EPCTableFilter1.size() * 100)/allStuffs)) + '%');
        }
        else if(userSpecActivity.API.wareHouseID == 1707) {
            status.setText(status.getText() + "تعداد کالا های فروشگاه: " + EPCTableFilter1.size() + '\n');
            status.setText(status.getText() + "تعداد کالا های انبار: " + EPCTableFilter0.size() + '/' + allStuffs + '\n');
            circularProgressBar.setProgress((float)((EPCTableFilter0.size() * 100)/allStuffs));
            percentage.setText(String.valueOf((float)((EPCTableFilter0.size() * 100)/allStuffs)) + '%');
        }

         status.setText(status.getText() + "تعداد تگ های خام: " + EPCTableFilterOther.size() + "\n");

        powerText.setText("اندازه توان(" + readingPower + ")");
        powerSeekBar.setProgress(readingPower - 5);

    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {

            if(event.getRepeatCount() == 0) {

                if(!step) {

                    while(!RF.setPower(readingPower)) {}
                    readTask.readEnable = true;
                    timerHandler.post(timerRunnable);
                    step = true;
                    readingInProgress = true;
                    databaseInProgress = false;
                    button.setBackgroundColor(Color.GRAY);
                }
                else {
                    readTask.readEnable = false;
                    while(!readTask.finished){}
                    step = false;
                    readingInProgress = false;
                    processingInProgress = true;
                    status.setText("در حال پردازش ...");
                }
            }

        } else if (keyCode == 4) {

            RF.stopInventory();
            finish();
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        RF.stopInventory();
    }

    @SuppressLint("SetTextI18n")
    public void sendFile(View view) {

        if(readingInProgress) {
            return;
        }
        API.status = false;
        API2.status = false;
        API.run = true;
        databaseInProgress = true;
        timerHandler.post(timerRunnable);
    }

    @SuppressLint("SetTextI18n")
    public void clearAll(View view) {
        EPCTable.clear();
        EPCTableFilter0.clear();
        EPCTableFilterOther.clear();
        EPCTableFilter1.clear();
        EPCLastLength = 0;
        status.setText("تعداد کل کالاهای اسکن شده: " + EPCTable.size() + '\n');
    }
}