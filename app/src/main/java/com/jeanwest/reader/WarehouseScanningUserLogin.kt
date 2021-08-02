package com.jeanwest.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class WarehouseScanningUserLogin : AppCompatActivity() {
    private lateinit var api: APIReadingInformation
    private lateinit var status: Toast
    private lateinit var departmentInfoIDView: EditText
    private lateinit var wareHouseIDView: Spinner
    private lateinit var nextActivityIntent: Intent
    private lateinit var memory: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @SuppressLint("SetTextI18n", "CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_spec)
        departmentInfoIDView = findViewById<View>(R.id.DepartmentInfoIDView) as EditText
        wareHouseIDView = findViewById(R.id.WareHouseIDViewSpinner)
        nextActivityIntent = Intent(this, WarehouseScanning::class.java)
        status = Toast.makeText(this, "", Toast.LENGTH_LONG)
        val spinnerString = ArrayList<String>()
        spinnerString.add("فروشگاه")
        spinnerString.add("انبار")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, spinnerString)
        wareHouseIDView.adapter = spinnerAdapter
        memory = PreferenceManager.getDefaultSharedPreferences(this)
        editor = memory.edit()
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        departmentInfoID = memory.getInt("ID", 1) // value to store
        departmentInfoIDView.setText(departmentInfoID.toString())
        api = APIReadingInformation()
        api.stop = false
        api.start()
    }

    fun startReading(view: View?) {
        if (departmentInfoIDView.editableText.toString().isEmpty()) {
            status.setText("\nلطفا کد شعبه را وارد کنید\n")
            status.show()
            return
        }
        api.departmentInfoID = departmentInfoIDView.editableText.toString().toInt()
        api.wareHouseID = wareHouseIDView.selectedItemPosition + 1
        departmentInfoID = api.departmentInfoID
        wareHouseID = api.wareHouseID
        editor.putInt("ID", departmentInfoID) // value to store
        editor.commit()
        api.run = true
        while (api.run) {
        }
        if (!api.status) {
            status.setText("خطا در دیتابیس")
            status.show()
            return
        }
        wareHouseID = api.wareHouseID
        departmentInfoID = api.departmentInfoID
        WarehouseScanning.ID = api.Response.toInt()
        WarehouseScanning.fromLogin = true
        startActivity(nextActivityIntent)
    }

    override fun onPause() {
        super.onPause()
        api.stop = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 4) {
            api.stop = true
            finish()
        }
        return true
    }

    fun sendFileWithClearing(view: View?) {
        val alertDialog: AlertDialog
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setTitle("تمام اطلاعات قبلی پاک می شود")
        alertBuilder.setMessage("آیا ادامه می دهید؟")
        alertBuilder.setPositiveButton("بله") { dialog, which ->
            WarehouseScanning.EPCTable.clear()
            WarehouseScanning.EPCTableValid.clear()
            editor.putString("1", "")
            editor.putString("2", "")
            editor.commit()
            startReading(view)
        }
        alertBuilder.setNegativeButton("خیر") { dialog, which -> }
        alertDialog = alertBuilder.create()
        alertDialog.setOnShowListener {
            alertDialog.window!!.decorView.layoutDirection =
                View.LAYOUT_DIRECTION_RTL // set title and message direction to RTL
        }
        alertDialog.show()
    }

    fun finishReading(view: View?) {
        val finishWarehouseScanning = APIReadingFinish()
        finishWarehouseScanning.DepoMojodiReviewInfo_ID = memory.getInt(departmentInfoID.toString() + 2, 0)
        finishWarehouseScanning.StoreMojodiReviewInfo_ID = memory.getInt(departmentInfoID.toString() + 1, 0)
        finishWarehouseScanning.start()
        while (finishWarehouseScanning.run) {
        }
        status.setText(finishWarehouseScanning.Response)
        status.show()
    }

    companion object {
        @JvmField
        var departmentInfoID: Int = 0
        @JvmField
        var wareHouseID: Int = 2
    }
}