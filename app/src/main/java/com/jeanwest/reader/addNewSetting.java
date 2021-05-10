package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
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
    public databaseHelperClass2 databaseHelper2 = new databaseHelperClass2(this);
    public static SQLiteDatabase counter;
    private boolean dataExist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_setting);
        filterSpinner = (Spinner) findViewById(R.id.filterSpinner);
        partitionSpinner = (Spinner) findViewById(R.id.partitionSpinner);
        headerEditText = (EditText) findViewById(R.id.header);
        companyEditText = (EditText) findViewById(R.id.company);
        powerSet = (SeekBar) findViewById(R.id.poweSeekBar);
        oneStep = (RadioButton) findViewById(R.id.oneStepRadioButton);
        twoStep = (RadioButton) findViewById(R.id.twoStepRadioButton);
        powerText = (TextView) findViewById(R.id.powerIndicatorText);
        serialNumberCounterValue = (TextView) findViewById(R.id.serialNumberText);
        counterMin = (EditText) findViewById(R.id.counterMinText);
        counterMax = (EditText) findViewById(R.id.counterMaxText);
        passwordText = (EditText) findViewById(R.id.passText);
        response = Toast.makeText(this, " ", Toast.LENGTH_LONG);
        steps = (RadioGroup) findViewById(R.id.radioGroup);

        counter = databaseHelper2.getWritableDatabase();
        Cursor counterCursor = counter.rawQuery("select * from counterDatabase", null);
        if(counterCursor.getCount() <= 0) {
            response.setText("دیتای برنامه پاک شده است. جهت استفاده از دستگاه، پارامتر های این صفحه را تنظیم کنید");
            response.show();
            dataExist = false;
        }

        else {
            counterCursor.moveToFirst();
            addNew.counterValue = counterCursor.getLong(0);
            addNew.counterMaxValue = counterCursor.getLong(1);
            addNew.counterMinValue = counterCursor.getLong(2);
            addNew.headerNumber = counterCursor.getInt(3);
            addNew.filterNumber = counterCursor.getInt(4);
            addNew.partitionNumber = counterCursor.getInt(5);
            addNew.companyNumber = counterCursor.getInt(6);
            addNew.RFPower = counterCursor.getInt(7);
            addNew.tagPassword = counterCursor.getString(8);
            addNew.oneStepActive = (counterCursor.getInt(9) == 1);
            dataExist = true;
        }
        counterCursor.close();

        headerEditText.setText(String.valueOf(addNew.headerNumber));
        companyEditText.setText(String.valueOf(addNew.companyNumber));
        powerSet.setProgress(addNew.RFPower - 5);
        powerText.setText("اندازه توان " + String.valueOf(addNew.RFPower) + "dB");
        serialNumberCounterValue.setText("مقدار کنونی شماره سریال: " + String.valueOf(addNew.counterValue));
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
                powerText.setText("اندازه توان " + String.valueOf(addNew.RFPower) + "dB");
                ContentValues val = new ContentValues();
                val.put("value", addNew.counterValue);
                val.put("max", addNew.counterMaxValue);
                val.put("min", addNew.counterMinValue);
                val.put("header", addNew.headerNumber);
                val.put("filter", addNew.filterNumber);
                val.put("partition", addNew.partitionNumber);
                val.put("company", addNew.companyNumber);
                val.put("power", addNew.RFPower);
                val.put("password", addNew.tagPassword);
                if(addNew.oneStepActive) {
                    val.put("step", 1);
                }
                else {
                    val.put("step", 0);
                }
                val.put("counterModified", 0L);

                if(dataExist) {
                    counter.update("counterDatabase", val, null, null);
                }
                else {
                    counter.insert("counterDatabase", null, val);
                    dataExist = true;
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        String[] items = {"انبار (0)", "فروشگاه (1)", "2", "3", "4", "5", "6", "7"};

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        filterSpinner.setAdapter(spinnerAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                addNew.filterNumber = position;
                ContentValues val = new ContentValues();
                val.put("value", addNew.counterValue);
                val.put("max", addNew.counterMaxValue);
                val.put("min", addNew.counterMinValue);
                val.put("header", addNew.headerNumber);
                val.put("filter", addNew.filterNumber);
                val.put("partition", addNew.partitionNumber);
                val.put("company", addNew.companyNumber);
                val.put("power", addNew.RFPower);
                val.put("password", addNew.tagPassword);
                if(addNew.oneStepActive) {
                    val.put("step", 1);
                }
                else {
                    val.put("step", 0);
                }
                val.put("counterModified", 0L);

                if(dataExist) {
                    counter.update("counterDatabase", val, null, null);
                }
                else {
                    counter.insert("counterDatabase", null, val);
                    dataExist = true;
                }
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
                ContentValues val = new ContentValues();
                val.put("value", addNew.counterValue);
                val.put("max", addNew.counterMaxValue);
                val.put("min", addNew.counterMinValue);
                val.put("header", addNew.headerNumber);
                val.put("filter", addNew.filterNumber);
                val.put("partition", addNew.partitionNumber);
                val.put("company", addNew.companyNumber);
                val.put("power", addNew.RFPower);
                val.put("password", addNew.tagPassword);
                if(addNew.oneStepActive) {
                    val.put("step", 1);
                }
                else {
                    val.put("step", 0);
                }
                val.put("counterModified", 0L);

                if(dataExist) {
                    counter.update("counterDatabase", val, null, null);

                }
                else {
                    counter.insert("counterDatabase", null, val);
                    dataExist = true;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        partitionSpinner.setSelection(addNew.partitionNumber);
    }

    public void headerSet(View view) {

        addNew.headerNumber = Integer.parseInt(headerEditText.getEditableText().toString());
        response.setText(String.valueOf(addNew.headerNumber));
        response.show();
        ContentValues val = new ContentValues();
        val.put("value", addNew.counterValue);
        val.put("max", addNew.counterMaxValue);
        val.put("min", addNew.counterMinValue);
        val.put("header", addNew.headerNumber);
        val.put("filter", addNew.filterNumber);
        val.put("partition", addNew.partitionNumber);
        val.put("company", addNew.companyNumber);
        val.put("power", addNew.RFPower);
        val.put("password", addNew.tagPassword);
        if(addNew.oneStepActive) {
            val.put("step", 1);
        }
        else {
            val.put("step", 0);
        }
        val.put("counterModified", 0L);

        if(dataExist) {
            counter.update("counterDatabase", val, null, null);
        }
        else {
            counter.insert("counterDatabase", null, val);
            dataExist = true;
        }
    }

    public void companySet(View view) {

        addNew.companyNumber = Integer.parseInt(companyEditText.getEditableText().toString());
        response.setText(String.valueOf(addNew.companyNumber));
        response.show();
        ContentValues val = new ContentValues();
        val.put("value", addNew.counterValue);
        val.put("max", addNew.counterMaxValue);
        val.put("min", addNew.counterMinValue);
        val.put("header", addNew.headerNumber);
        val.put("filter", addNew.filterNumber);
        val.put("partition", addNew.partitionNumber);
        val.put("company", addNew.companyNumber);
        val.put("power", addNew.RFPower);
        val.put("password", addNew.tagPassword);
        if(addNew.oneStepActive) {
            val.put("step", 1);
        }
        else {
            val.put("step", 0);
        }
        val.put("counterModified", 0L);

        if(dataExist) {
            counter.update("counterDatabase", val, null, null);
        }
        else {
            counter.insert("counterDatabase", null, val);
            dataExist = true;
        }
    }

    public void stepOptionChange(View view) {

        addNew.oneStepActive = oneStep.isChecked();
        ContentValues val = new ContentValues();
        val.put("value", addNew.counterValue);
        val.put("max", addNew.counterMaxValue);
        val.put("min", addNew.counterMinValue);
        val.put("header", addNew.headerNumber);
        val.put("filter", addNew.filterNumber);
        val.put("partition", addNew.partitionNumber);
        val.put("company", addNew.companyNumber);
        val.put("power", addNew.RFPower);
        val.put("password", addNew.tagPassword);
        if(addNew.oneStepActive) {
            val.put("step", 1);
        }
        else {
            val.put("step", 0);
        }
        val.put("counterModified", 0L);

        if(dataExist) {
            counter.update("counterDatabase", val, null, null);
        }
        else {
            counter.insert("counterDatabase", null, val);
            dataExist = true;
        }

        if(addNew.oneStepActive) {
            steps.check(R.id.oneStepRadioButton);
        }

    }

    public void stepOptionChange2(View view) {
        addNew.oneStepActive = (oneStep.isChecked());
        ContentValues val = new ContentValues();
        val.put("value", addNew.counterValue);
        val.put("max", addNew.counterMaxValue);
        val.put("min", addNew.counterMinValue);
        val.put("header", addNew.headerNumber);
        val.put("filter", addNew.filterNumber);
        val.put("partition", addNew.partitionNumber);
        val.put("company", addNew.companyNumber);
        val.put("power", addNew.RFPower);
        val.put("password", addNew.tagPassword);
        if(addNew.oneStepActive) {
            val.put("step", 1);
        }
        else {
            val.put("step", 0);
        }
        val.put("counterModified", 0L);

        if(dataExist) {
            counter.update("counterDatabase", val, null, null);
        }
        else {
            counter.insert("counterDatabase", null, val);
            dataExist = true;
        }

        if(addNew.oneStepActive) {
            steps.check(R.id.oneStepRadioButton);
        }
    }

    public void serialNumberRangeButton(View view) {

        addNew.counterValue = Long.parseLong(counterMin.getEditableText().toString());
        addNew.counterMinValue = Long.parseLong(counterMin.getEditableText().toString());
        addNew.counterMaxValue = Long.parseLong(counterMax.getEditableText().toString());
        serialNumberCounterValue.setText("مقدار کنونی شماره سریال: " + String.valueOf(addNew.counterValue));
        ContentValues val = new ContentValues();
        val.put("value", addNew.counterValue);
        val.put("max", addNew.counterMaxValue);
        val.put("min", addNew.counterMinValue);
        val.put("header", addNew.headerNumber);
        val.put("filter", addNew.filterNumber);
        val.put("partition", addNew.partitionNumber);
        val.put("company", addNew.companyNumber);
        val.put("power", addNew.RFPower);
        val.put("password", addNew.tagPassword);
        if(addNew.oneStepActive) {
            val.put("step", 1);
        }
        else {
            val.put("step", 0);
        }

        val.put("counterModified", 0L);
        if(dataExist) {
            counter.update("counterDatabase", val, null, null);

        }
        else {
            counter.insert("counterDatabase", null, val);
            dataExist = true;
        }
    }

    public void setPassword(View view) {
        addNew.tagPassword = passwordText.getEditableText().toString();
        ContentValues val = new ContentValues();
        val.put("value", addNew.counterValue);
        val.put("max", addNew.counterMaxValue);
        val.put("min", addNew.counterMinValue);
        val.put("header", addNew.headerNumber);
        val.put("filter", addNew.filterNumber);
        val.put("partition", addNew.partitionNumber);
        val.put("company", addNew.companyNumber);
        val.put("power", addNew.RFPower);
        val.put("password", addNew.tagPassword);

        if(addNew.oneStepActive) {
            val.put("step", 1);
        }
        else {
            val.put("step", 0);
        }
        val.put("counterModified", 0L);
        if(dataExist) {
            counter.update("counterDatabase", val, null, null);

        }
        else {
            counter.insert("counterDatabase", null, val);
            dataExist = true;
        }
    }
}