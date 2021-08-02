package com.jeanwest.reader

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingActivity : AppCompatActivity() {

    private lateinit var filterSpinner: Spinner
    private lateinit var partitionSpinner: Spinner
    private lateinit var headerEditText: EditText
    private lateinit var companyEditText: EditText
    private lateinit var oneStep: RadioButton
    private lateinit var twoStep: RadioButton
    private lateinit var steps: RadioGroup
    private lateinit var response: Toast
    private lateinit var serialNumberCounterValue: TextView
    private lateinit var counterMin: EditText
    private lateinit var counterMax: EditText
    private lateinit var passwordText: EditText
    private lateinit var memory: SharedPreferences
    private lateinit var memoryEditor: SharedPreferences.Editor

    @SuppressLint("SetTextI18n", "CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_new_setting)
        filterSpinner = findViewById(R.id.filterSpinner)
        partitionSpinner = findViewById(R.id.partitionSpinner)
        headerEditText = findViewById(R.id.header)
        companyEditText = findViewById(R.id.company)
        oneStep = findViewById(R.id.oneStepRadioButton)
        twoStep = findViewById(R.id.twoStepRadioButton)
        serialNumberCounterValue = findViewById(R.id.serialNumberText)
        counterMin = findViewById(R.id.counterMinText)
        counterMax = findViewById(R.id.counterMaxText)
        passwordText = findViewById(R.id.passText)
        response = Toast.makeText(this, " ", Toast.LENGTH_LONG)
        steps = findViewById(R.id.radioGroup)
        memory = PreferenceManager.getDefaultSharedPreferences(this)
        memoryEditor = memory.edit()
        if (memory.getLong("value", -1L) == -1L) {
            response.setText("دیتای برنامه پاک شده است. جهت استفاده از دستگاه، پارامتر های این صفحه را تنظیم کنید")
            response.show()
        } else {
            addNew.counterValue = memory.getLong("value", -1L)
            addNew.counterMaxValue = memory.getLong("max", -1L)
            addNew.counterMinValue = memory.getLong("min", -1L)
            addNew.headerNumber = memory.getInt("header", -1)
            addNew.filterNumber = memory.getInt("filter", -1)
            addNew.partitionNumber = memory.getInt("partition", -1)
            addNew.companyNumber = memory.getInt("company", -1)
            addNew.tagPassword = memory.getString("password", "")
            addNew.oneStepActive = memory.getInt("step", -1) == 1
        }
        headerEditText.setText(addNew.headerNumber.toString())
        companyEditText.setText(addNew.companyNumber.toString())
        serialNumberCounterValue.text = "مقدار کنونی شماره سریال: " + addNew.counterValue
        counterMin.setText(addNew.counterMinValue.toString())
        counterMax.setText(addNew.counterMaxValue.toString())
        passwordText.setText(addNew.tagPassword)
        if (addNew.oneStepActive) {
            steps.check(R.id.oneStepRadioButton)
        }
        val items = arrayOf("0", "1", "2", "3", "4", "5", "6", "7")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        filterSpinner.adapter = spinnerAdapter
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                addNew.filterNumber = 0
                saveSetting()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        filterSpinner.setSelection(addNew.filterNumber)
        val items2 = arrayOf("0", "1", "2", "3", "4", "5", "6", "7")
        val partitionSpinnerAdaptor =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, items2)
        partitionSpinner.adapter = partitionSpinnerAdaptor
        partitionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                addNew.partitionNumber = position
                saveSetting()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        partitionSpinner.setSelection(addNew.partitionNumber)
    }

    fun headerSet(view: View?) {
        addNew.headerNumber = headerEditText.editableText.toString().toInt()
        saveSetting()
    }

    fun companySet(view: View?) {
        addNew.companyNumber = companyEditText.editableText.toString().toInt()
        saveSetting()
    }

    fun stepOptionChange(view: View?) {
        addNew.oneStepActive = oneStep.isChecked
        saveSetting()
    }

    fun stepOptionChange2(view: View?) {
        addNew.oneStepActive = oneStep.isChecked
        saveSetting()
    }

    @SuppressLint("SetTextI18n")
    fun serialNumberRangeButton(view: View?) {
        addNew.counterValue = counterMin.editableText.toString().toLong()
        addNew.counterMinValue = counterMin.editableText.toString().toLong()
        addNew.counterMaxValue = counterMax.editableText.toString().toLong()
        serialNumberCounterValue.text = "مقدار کنونی شماره سریال: " + addNew.counterValue
        saveSetting()
    }

    fun setPassword(view: View?) {
        addNew.tagPassword = passwordText.editableText.toString()
        saveSetting()
    }

    fun saveSetting() {
        memoryEditor.putLong("value", addNew.counterValue)
        memoryEditor.putLong("max", addNew.counterMaxValue)
        memoryEditor.putLong("min", addNew.counterMinValue)
        memoryEditor.putInt("header", addNew.headerNumber)
        memoryEditor.putInt("filter", addNew.filterNumber)
        memoryEditor.putInt("partition", addNew.partitionNumber)
        memoryEditor.putInt("company", addNew.companyNumber)
        memoryEditor.putString("password", addNew.tagPassword)
        if (addNew.oneStepActive) {
            memoryEditor.putInt("step", 1)
        } else {
            memoryEditor.putInt("step", 0)
        }
        memoryEditor.putLong("counterModified", 0L)
        memoryEditor.commit()
    }
}