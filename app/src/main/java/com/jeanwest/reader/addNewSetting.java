package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class addNewSetting extends AppCompatActivity {

    Spinner filterSpinner;
    Spinner partitionSpinner;
    EditText headerEditText;
    EditText companyEditText;
    SeekBar powerSet;
    RadioButton oneStep;
    RadioButton twoStep;
    RadioGroup steps;
    TextView powerText;
    Toast response;
    TextView serialNumberCounterValue;
    EditText counterMin;
    EditText counterMax;
    EditText passwordText;
    SharedPreferences memory;
    SharedPreferences.Editor memoryEditor;

    @SuppressLint({"SetTextI18n", "CommitPrefEdits"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_setting);
        filterSpinner = findViewById(R.id.filterSpinner);
        partitionSpinner = findViewById(R.id.partitionSpinner);
        headerEditText = findViewById(R.id.header);
        companyEditText = findViewById(R.id.company);
        powerSet = findViewById(R.id.poweSeekBar);
        oneStep = findViewById(R.id.oneStepRadioButton);
        twoStep = findViewById(R.id.twoStepRadioButton);
        powerText = findViewById(R.id.powerIndicatorText);
        serialNumberCounterValue = findViewById(R.id.serialNumberText);
        counterMin = findViewById(R.id.counterMinText);
        counterMax = findViewById(R.id.counterMaxText);
        passwordText = findViewById(R.id.passText);
        response = Toast.makeText(this, " ", Toast.LENGTH_LONG);
        steps = findViewById(R.id.radioGroup);

        memory = PreferenceManager.getDefaultSharedPreferences(this);
        memoryEditor = memory.edit();

        if(memory.getLong("value", -1L) == -1L) {
            response.setText("دیتای برنامه پاک شده است. جهت استفاده از دستگاه، پارامتر های این صفحه را تنظیم کنید");
            response.show();
        }
        else {
            addNew.counterValue = memory.getLong("value", -1L);
            addNew.counterMaxValue = memory.getLong("max", -1L);
            addNew.counterMinValue = memory.getLong("min", -1L);
            addNew.headerNumber = memory.getInt("header", -1);
            addNew.filterNumber = memory.getInt("filter", -1);
            addNew.partitionNumber = memory.getInt("partition", -1);
            addNew.companyNumber = memory.getInt("company", -1);
            addNew.RFPower = memory.getInt("power", -1);
            addNew.tagPassword = memory.getString("password", "");
            addNew.oneStepActive = (memory.getInt("step", -1) == 1);
        }

        headerEditText.setText(String.valueOf(addNew.headerNumber));
        companyEditText.setText(String.valueOf(addNew.companyNumber));
        powerSet.setProgress(addNew.RFPower - 5);
        powerText.setText("اندازه توان " + addNew.RFPower + "dB");
        serialNumberCounterValue.setText("مقدار کنونی شماره سریال: " + addNew.counterValue);
        counterMin.setText(String.valueOf(addNew.counterMinValue));
        counterMax.setText(String.valueOf(addNew.counterMaxValue));
        passwordText.setText(addNew.tagPassword);

        if(addNew.oneStepActive) {
            steps.check(R.id.oneStepRadioButton);
        }

        powerSet.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                addNew.RFPower= powerSet.getProgress() + 5;
                powerText.setText("اندازه توان " + addNew.RFPower + "dB");
                saveSetting();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        String[] items = {"0", "1", "2", "3", "4", "5", "6", "7"};

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        filterSpinner.setAdapter(spinnerAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                addNew.filterNumber = 0;
                saveSetting();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        filterSpinner.setSelection(addNew.filterNumber);

        String[] items2 = {"0", "1", "2", "3", "4", "5", "6", "7",};
        ArrayAdapter<String> partitionSpinnerAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items2);
        partitionSpinner.setAdapter(partitionSpinnerAdaptor);
        partitionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                addNew.partitionNumber = position;
                saveSetting();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        partitionSpinner.setSelection(addNew.partitionNumber);
    }

    public void headerSet(View view) {

        addNew.headerNumber = Integer.parseInt(headerEditText.getEditableText().toString());
        saveSetting();
    }

    public void companySet(View view) {

        addNew.companyNumber = Integer.parseInt(companyEditText.getEditableText().toString());
        saveSetting();
    }

    public void stepOptionChange(View view) {

        addNew.oneStepActive = oneStep.isChecked();
        saveSetting();
    }

    public void stepOptionChange2(View view) {
        addNew.oneStepActive = (oneStep.isChecked());
        saveSetting();
    }

    @SuppressLint("SetTextI18n")
    public void serialNumberRangeButton(View view) {

        addNew.counterValue = Long.parseLong(counterMin.getEditableText().toString());
        addNew.counterMinValue = Long.parseLong(counterMin.getEditableText().toString());
        addNew.counterMaxValue = Long.parseLong(counterMax.getEditableText().toString());
        serialNumberCounterValue.setText("مقدار کنونی شماره سریال: " + addNew.counterValue);
        saveSetting();
    }

    public void setPassword(View view) {
        addNew.tagPassword = passwordText.getEditableText().toString();
        saveSetting();
    }

    void saveSetting() {

        memoryEditor.putLong("value", addNew.counterValue);
        memoryEditor.putLong("max", addNew.counterMaxValue);
        memoryEditor.putLong("min", addNew.counterMinValue);
        memoryEditor.putInt("header", addNew.headerNumber);
        memoryEditor.putInt("filter", addNew.filterNumber);
        memoryEditor.putInt("partition", addNew.partitionNumber);
        memoryEditor.putInt("company", addNew.companyNumber);
        memoryEditor.putInt("power", addNew.RFPower);
        memoryEditor.putString("password", addNew.tagPassword);

        if(addNew.oneStepActive) {
            memoryEditor.putInt("step", 1);
        }
        else {
            memoryEditor.putInt("step", 0);
        }
        memoryEditor.putLong("counterModified", 0L);
        memoryEditor.commit();
    }
}