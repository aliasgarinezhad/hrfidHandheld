package com.jeanwest.reader.transference

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jeanwest.reader.R
import java.util.*

class TransferScanningLogin : AppCompatActivity() {
    private lateinit var status: Toast
    private lateinit var transferIDView: EditText
    private lateinit var nextActivityIntent: Intent
    private var transferID = 0L

    @SuppressLint("SetTextI18n", "CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_imformation)
        transferIDView = findViewById<EditText>(R.id.transferIDView)
        nextActivityIntent = Intent(this, TransferScanningActivity::class.java)
        status = Toast.makeText(this, "", Toast.LENGTH_LONG)

        transferIDView.setOnEditorActionListener{_, _, _ ->
            startReading(View(this))
            true
        }
    }

    fun startReading(view: View?) {
        if (transferIDView.editableText.toString().isEmpty()) {
            status.setText("\nلطفا شماره حواله را وارد کنید\n")
            status.show()
            return
        }

        transferID = transferIDView.editableText.toString().toLong()
        nextActivityIntent.putExtra("departmentInfoID", transferID)
        startActivity(nextActivityIntent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 4) {
            finish()
        }
        return true
    }
}