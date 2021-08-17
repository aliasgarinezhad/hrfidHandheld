package com.jeanwest.reader

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class AboutUsActivity : AppCompatActivity() {

    lateinit var versionName: TextView
    lateinit var alert: AlertDialog
    var api = UpdateAPI()
    var handler = Handler()
    var thread: Runnable = object : Runnable {
        override fun run() {
            if (api.finished) {
                if (!api.response.equals("ok")) {
                    Toast.makeText(this@AboutUsActivity, api.response, Toast.LENGTH_LONG).show()
                } else {

                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(
                        Uri.fromFile(api.outputFile),
                        "application/vnd.android.package-archive"
                    )
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                }
            } else {
                if (!alert.isShowing) {
                    alert.show()
                }
                handler.postDelayed(this, 500)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        versionName = findViewById(R.id.versionNameView)
        try {
            versionName.text = "ورژن: " + packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        api.context = this
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                0
            )
        }
        val alertBuilder = AlertDialog.Builder(api.context)
        alertBuilder.setMessage("لطفا منتظر بمانید ...")
        alertBuilder.setTitle("در حال دانلود نسخه به روز")
        alert = alertBuilder.create()
        alert.setOnShowListener {
            alert.window!!.decorView.layoutDirection =
                View.LAYOUT_DIRECTION_RTL // set title and message direction to RTL
        }
    }

    fun update(view: View?) {
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setMessage("نرم افزار به روز رسانی شود؟")
        alertBuilder.setTitle("به روز رسانی نرم افزار")
        alertBuilder.setPositiveButton("بله") { _, _ ->
            api.start()
            handler.postDelayed(thread, 1000)
        }
        alertBuilder.setNegativeButton("خیر") { _, _ -> }
        val alertUpdatePermit = alertBuilder.create()
        alertUpdatePermit.setOnShowListener {
            alertUpdatePermit.window!!.decorView.layoutDirection =
                View.LAYOUT_DIRECTION_RTL // set title and message direction to RTL
        }
        alertUpdatePermit.show()
    }
}