package com.jeanwest.reader.warehouseScanning

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.R
import java.util.*

class WarehouseScanningUserLogin : AppCompatActivity() {
    private lateinit var api: WarehouseScanningSendingInformationAPI
    private lateinit var departmentInfoIDView: EditText
    private lateinit var wareHouseIDView: Spinner
    private lateinit var nextActivityIntent: Intent
    private lateinit var memory: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var warehouseID: Int = 2
    private var departmentInfoID: Int = 0

    @SuppressLint("SetTextI18n", "CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_spec)
        departmentInfoIDView = findViewById<View>(R.id.DepartmentInfoIDView) as EditText
        wareHouseIDView = findViewById(R.id.WareHouseIDViewSpinner)
        nextActivityIntent = Intent(this, WarehouseScanningActivity::class.java)
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
    }

    fun startReading(view: View?) {
        if (departmentInfoIDView.editableText.toString().isEmpty()) {
            Toast.makeText(this, "\nلطفا کد شعبه را وارد کنید\n", Toast.LENGTH_LONG).show()
            return
        }
        api = WarehouseScanningSendingInformationAPI()
        api.departmentInfoID = departmentInfoIDView.editableText.toString().toInt()
        api.wareHouseID = wareHouseIDView.selectedItemPosition + 1
        departmentInfoID = api.departmentInfoID
        warehouseID = api.wareHouseID
        editor.putInt("ID", departmentInfoID) // value to store
        editor.commit()
        api.start()
        while (api.run) {}
        if (!api.status) {
            Toast.makeText(this, "خطا در دیتابیس", Toast.LENGTH_LONG).show()
            return
        }
        warehouseID = api.wareHouseID
        departmentInfoID = api.departmentInfoID
        nextActivityIntent.putExtra("ID", api.response.toInt())
        nextActivityIntent.putExtra("departmentInfoID", departmentInfoID)
        nextActivityIntent.putExtra("warehouseID", warehouseID)
        startActivity(nextActivityIntent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 4) {
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
            WarehouseScanningActivity.EPCTableValid.clear()
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
        val finishWarehouseScanning = WarehouseScanningFinishAPI()
        finishWarehouseScanning.depoMojodiReviewInfoID = memory.getInt(departmentInfoID.toString() + 2, 0)
        finishWarehouseScanning.storeMojodiReviewInfoID = memory.getInt(departmentInfoID.toString() + 1, 0)
        finishWarehouseScanning.start()
        while (finishWarehouseScanning.run) {
        }
        Toast.makeText(this, finishWarehouseScanning.response, Toast.LENGTH_LONG).show()
    }
}