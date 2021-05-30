package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.rscja.deviceapi.RFIDWithUHF;
import com.rscja.deviceapi.exception.ConfigurationException;

import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;

public class finding extends AppCompatActivity {

    public static RFIDWithUHF RF;
    public static Map<String, Integer> EPCTableFinding = new HashMap<String, Integer>();
    public ToneGenerator beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
    private int findingPower = 5;
    TextView status;
    TextView powerText;
    SeekBar powerSeekBar;
    EditText stuffCodeString;
    TextView stuffSpec;
    private boolean step = false;
    findingThread findTask = new findingThread();
    int numberOfFound = 0;
    private long stuffCode = 162309L;
    private long stuffRFIDCode = 162309L;
    WebView picture;

    String stuffTitle = "پیراهن";
    public static String stuffCardCode = "01531052";
    String stuffBarcode = "01531052J-2420-XL";
    public static String stuffImgUrl = "https://www.banimode.com/jeanswest/image.php?token=tmv43w4as&code=01531052J-2420-XL";
    String stuffTotalNumber = "10";
    public static boolean fromReadingResultSubSubActivity = false;
    public static String ReadingResultSubSubString = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding);
        status = findViewById(R.id.section_label);
        stuffSpec = findViewById(R.id.result);
        stuffCodeString = findViewById(R.id.stuffCodeField);
        picture = findViewById(R.id.pictureView);
        powerText = findViewById(R.id.findingPowerTextView);
        powerSeekBar = findViewById(R.id.findingPowerSeekBar);

        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if(!findTask.readEnable) {
                    findingPower = progress + 5;
                    powerText.setText("قدرت سیگنال(" + findingPower + ")");
                }
                else {
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
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            RF = RFIDWithUHF.getInstance();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        if (!findTask.isAlive()) {
            findTask.start();
        }

        while(!RF.setEPCTIDMode(false)) {}
        while(!RF.setPower(findingPower)) {}
        while(!RF.setFrequencyMode((byte) 4)) {}
        while(!RF.setRFLink(0)) {}

        if(fromReadingResultSubSubActivity) {
            stuffSpec.setText(ReadingResultSubSubString);
            picture.loadUrl(stuffImgUrl);
            WebSettings setting = picture.getSettings();
            setting.setUseWideViewPort(true);
            setting.setLoadWithOverviewMode(true);
            picture.setFocusable(false);
            stuffCodeString.setText(stuffCardCode);
            stuffCodeString.setFocusable(false);
        }

    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {

            if(event.getRepeatCount() == 0) {

                numberOfFound = 0;
                status.setText("");

                if(!step) {

                    EPCTableFinding.clear();
                    while(!RF.setPower(findingPower)) {}
                    findTask.readEnable = true;
                    step = true;
                    stuffCode = Long.parseLong(stuffCodeString.getEditableText().toString());

                    if(!fromReadingResultSubSubActivity) {

                        picture.loadUrl(stuffImgUrl);
                        WebSettings setting = picture.getSettings();
                        setting.setUseWideViewPort(true);
                        setting.setLoadWithOverviewMode(true);
                        picture.setFocusable(false);
                        stuffSpec.setText(stuffTitle + "\n" +
                                "کد محصول: " + stuffCardCode + "\n" +
                                "بارکد: " + stuffBarcode + "\n" +
                                "تعداد کل: " + stuffTotalNumber);
                    }

                    status.setText("در حال جست و جو ...");
                }
                else {
                    findTask.readEnable = false;
                    while(!findTask.finished){}
                    step = false;

                    for(Map.Entry<String, Integer> EPC : EPCTableFinding.entrySet()) {

                        String EPCHexString = EPC.getKey();
                        Long EPCInt1 = Long.parseLong(EPCHexString.substring(0, 8), 16);
                        Long EPCInt2 = Long.parseLong(EPCHexString.substring(8, 16), 16);
                        Long EPCInt3 = Long.parseLong(EPCHexString.substring(16, 24), 16);

                        String EPCBinaryString1 = Long.toBinaryString(EPCInt1);
                        EPCBinaryString1 = String.format("%32s", EPCBinaryString1).replaceAll(" ", "0");
                        String EPCBinaryString2 = Long.toBinaryString(EPCInt2);
                        EPCBinaryString2 = String.format("%32s", EPCBinaryString2).replaceAll(" ", "0");
                        String EPCBinaryString3 = Long.toBinaryString(EPCInt3);
                        EPCBinaryString3 = String.format("%32s", EPCBinaryString3).replaceAll(" ", "0");

                        String EPCBinaryString = EPCBinaryString1 + EPCBinaryString2 + EPCBinaryString3;

                        Integer header = Integer.parseInt(EPCBinaryString.substring(0, 8), 2);
                        Long itemNumber = Long.parseLong(EPCBinaryString.substring(26, 58), 2);

                        if (header == 48 && itemNumber == stuffCode) {

                            beep.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                            status.setText(status.getText() + "\n" + EPCHexString);
                            numberOfFound ++;
                        }
                    }
                    if(numberOfFound == 0) {
                        beep.startTone(ToneGenerator.TONE_CDMA_PIP, 500);
                        status.setText("کالا در این محدوده نیست");
                    }
                    else {
                        status.setText(status.getText() + "\n" + "تعداد پیدا شده: " + numberOfFound);
                    }
                }
            }

        } else if (keyCode == 4) {

            RF.stopInventory();
            finish();
        }
        return true;
    }
}