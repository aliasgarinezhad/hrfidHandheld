package com.jeanwest.reader.add

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.R

class SettingActivity : AppCompatActivity() {

    private lateinit var filterSpinner: Spinner
    private lateinit var partitionSpinner: Spinner
    private lateinit var headerEditText: EditText
    private lateinit var companyEditText: EditText
    private lateinit var oneStep: RadioButton
    private lateinit var twoStep: RadioButton
    private lateinit var steps: RadioGroup
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
        steps = findViewById(R.id.radioGroup)
        memory = PreferenceManager.getDefaultSharedPreferences(this)
        memoryEditor = memory.edit()
        if (memory.getLong("value", -1L) == -1L) {
            Toast.makeText(this, "دیتای برنامه پاک شده است. جهت استفاده از دستگاه، پارامتر های این صفحه را تنظیم کنید", Toast.LENGTH_LONG).show()
        } else {
            AddProductActivity.counterValue = memory.getLong("value", -1L)
            AddProductActivity.counterMaxValue = memory.getLong("max", -1L)
            AddProductActivity.counterMinValue = memory.getLong("min", -1L)
            AddProductActivity.headerNumber = memory.getInt("header", -1)
            AddProductActivity.filterNumber = memory.getInt("filter", -1)
            AddProductActivity.partitionNumber = memory.getInt("partition", -1)
            AddProductActivity.companyNumber = memory.getInt("company", -1)
            AddProductActivity.tagPassword = memory.getString("password", "")
            AddProductActivity.oneStepActive = memory.getInt("step", -1) == 1
        }
        headerEditText.setText(AddProductActivity.headerNumber.toString())
        companyEditText.setText(AddProductActivity.companyNumber.toString())
        serialNumberCounterValue.text = "مقدار کنونی شماره سریال: " + AddProductActivity.counterValue
        counterMin.setText(AddProductActivity.counterMinValue.toString())
        counterMax.setText(AddProductActivity.counterMaxValue.toString())
        passwordText.setText(AddProductActivity.tagPassword)
        if (AddProductActivity.oneStepActive) {
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
                AddProductActivity.filterNumber = 0
                saveSetting()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        filterSpinner.setSelection(AddProductActivity.filterNumber)
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
                AddProductActivity.partitionNumber = position
                saveSetting()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        partitionSpinner.setSelection(AddProductActivity.partitionNumber)
    }

    fun headerSet(view: View?) {
        AddProductActivity.headerNumber = headerEditText.editableText.toString().toInt()
        saveSetting()
    }

    fun companySet(view: View?) {
        AddProductActivity.companyNumber = companyEditText.editableText.toString().toInt()
        saveSetting()
    }

    fun stepOptionChange(view: View?) {
        AddProductActivity.oneStepActive = oneStep.isChecked
        saveSetting()
    }

    fun stepOptionChange2(view: View?) {
        AddProductActivity.oneStepActive = oneStep.isChecked
        saveSetting()
    }

    @SuppressLint("SetTextI18n")
    fun serialNumberRangeButton(view: View?) {
        AddProductActivity.counterValue = counterMin.editableText.toString().toLong()
        AddProductActivity.counterMinValue = counterMin.editableText.toString().toLong()
        AddProductActivity.counterMaxValue = counterMax.editableText.toString().toLong()
        serialNumberCounterValue.text = "مقدار کنونی شماره سریال: " + AddProductActivity.counterValue
        saveSetting()
    }

    fun setPassword(view: View?) {
        AddProductActivity.tagPassword = passwordText.editableText.toString()
        saveSetting()
    }

    fun saveSetting() {
        memoryEditor.putLong("value", AddProductActivity.counterValue)
        memoryEditor.putLong("max", AddProductActivity.counterMaxValue)
        memoryEditor.putLong("min", AddProductActivity.counterMinValue)
        memoryEditor.putInt("header", AddProductActivity.headerNumber)
        memoryEditor.putInt("filter", AddProductActivity.filterNumber)
        memoryEditor.putInt("partition", AddProductActivity.partitionNumber)
        memoryEditor.putInt("company", AddProductActivity.companyNumber)
        memoryEditor.putString("password", AddProductActivity.tagPassword)
        if (AddProductActivity.oneStepActive) {
            memoryEditor.putInt("step", 1)
        } else {
            memoryEditor.putInt("step", 0)
        }
        memoryEditor.putLong("counterModified", 0L)
        memoryEditor.commit()
    }
}