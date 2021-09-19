package com.jeanwest.reader.aboutUs

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jeanwest.reader.R
import kotlinx.android.synthetic.main.activity_about_us.*
import java.io.File

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

                    val path = Environment.getExternalStorageDirectory().toString() + "/" + "app.apk"

                    val file = File(path)
                    if(file.exists()) {
                        val intent = Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uriFromFile(applicationContext, File(path)), "application/vnd.android.package-archive");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            applicationContext.startActivity(intent);
                        } catch (e : ActivityNotFoundException) {
                            e.printStackTrace();
                            Log.e("TAG", "Error in opening the file!");
                        }
                    }else{
                        Toast.makeText(applicationContext,"Error",Toast.LENGTH_LONG).show();
                    }
                    if (alert.isShowing) {
                        alert.hide()
                    }
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
        setContentView(R.layout.activity_about_us)
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

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(!packageManager.canRequestPackageInstalls()){
                startActivityForResult(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse(String.format("package:%s", packageName))), 2)
            }
        }

        val alertBuilder = AlertDialog.Builder(api.context)
        alertBuilder.setMessage("لطفا منتظر بمانید ...")
        alertBuilder.setTitle("در حال دانلود نسخه به روز")
        alert = alertBuilder.create()
        alert.setOnShowListener {
            alert.window!!.decorView.layoutDirection =
                View.LAYOUT_DIRECTION_RTL // set title and message direction to RTL
        }
        about_us_toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    fun update(view: View?) {
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setMessage("نرم افزار به روز رسانی شود؟")
        alertBuilder.setTitle("به روز رسانی نرم افزار")
        alertBuilder.setPositiveButton("بله") { _, _ ->
            api = UpdateAPI()
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

    fun uriFromFile(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context, this.packageName + ".provider",
                file
            )
        } else {
            Uri.fromFile(file)
        }
    }
}