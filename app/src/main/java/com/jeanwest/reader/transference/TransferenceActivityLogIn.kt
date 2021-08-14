package com.jeanwest.reader.transference

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.jeanwest.reader.R

class TransferenceActivityLogIn : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transference_login)
    }

    fun startTransfer(view: View) {

        val sourceWarehouseV = findViewById<EditText>(R.id.sourceWareHouseTextView)
        val desWarehouseV = findViewById<EditText>(R.id.desWareHouseTextView)
        val transferExplanationV = findViewById<EditText>(R.id.transferExplainationTV)

        if (sourceWarehouseV.editableText.toString()
                .isEmpty() || desWarehouseV.editableText.toString().isEmpty()
            || transferExplanationV.editableText.toString().isEmpty()
        ) {
            Toast.makeText(this, "لطفا مبدا، مقصد و توضیحات را کامل کنید", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, TransferenceActivity::class.java)
        intent.putExtra("source", sourceWarehouseV.editableText.toString().toInt())
        intent.putExtra("des", desWarehouseV.editableText.toString().toInt())
        intent.putExtra("explanation", transferExplanationV.editableText.toString())
        startActivity(intent)
    }
}