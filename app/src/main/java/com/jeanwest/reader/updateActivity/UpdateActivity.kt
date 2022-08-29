package com.jeanwest.reader.updateActivity

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jeanwest.reader.R
import com.jeanwest.reader.iotHub.IotHub
import com.jeanwest.reader.sharedClassesAndFiles.testClasses.RFIDWithUHFUART
import com.jeanwest.reader.sharedClassesAndFiles.theme.MyApplicationTheme
import java.io.File


class UpdateActivity : ComponentActivity() {

    private lateinit var rf: RFIDWithUHFUART
    private var isDownloading = mutableStateOf(false)
    private var openDialog = mutableStateOf(false)
    private var downLoadId = 0L

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e : Exception) {
            e.printStackTrace()
        }
        rf.stopInventory()
        rf.free()

        Toast.makeText(
            applicationContext,
            "نسخه جدید (${IotHub.appVersion}) موجود است",
            Toast.LENGTH_LONG
        ).show()

        setContent {
            AboutUsUI()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            if (!packageManager.canRequestPackageInstalls()) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse(String.format("package:%s", packageName))), 2
                )
            }
        }

        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        );
    }

    private fun downloadApkFile() {

        val path = getExternalFilesDir(null)?.path + "/download/" + "app.apk"
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }

        val serverAddress =
            "https://rfid-api.avakatan.ir/apk/app-debug-" + IotHub.appVersion + ".apk"
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val downloadManagerRequest = DownloadManager.Request(Uri.parse(serverAddress))
        downloadManagerRequest.setTitle("بروزرسانی RFID")
            .setDescription("در حال دانلود ...")
            .setDestinationInExternalFilesDir(
                this,
                "download",
                "app.apk"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        downLoadId = downloadManager.enqueue(downloadManagerRequest)
    }

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            if (downLoadId == id) {
                isDownloading.value = false

                val path = getExternalFilesDir(null)?.path + "/download/" + "app.apk"

                val file = File(path)
                if (file.exists()) {
                    val installIntent = Intent(Intent.ACTION_VIEW)
                    installIntent.setDataAndType(
                        uriFromFile(applicationContext, File(path)),
                        "application/vnd.android.package-archive"
                    )
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    applicationContext.startActivity(installIntent)

                } else {
                    Toast.makeText(this@UpdateActivity, "خطا در به روز رسانی", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
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

    private fun back() {
        unregisterReceiver(onDownloadComplete)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        if (keyCode == 4) {
            back()
        }
        return true
    }

    @Composable
    fun AboutUsUI() {
        MyApplicationTheme {

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = {
                        TopAppBar(

                            navigationIcon = {
                                IconButton(onClick = { back() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                                        contentDescription = ""
                                    )
                                }
                            },

                            title = {

                                Text(
                                    text = "درباره ما",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .wrapContentSize()
                                        .padding(end = 50.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        )
                    },
                    content = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ورژن کنونی: " + packageManager.getPackageInfo(
                                    packageName,
                                    0
                                ).versionName,
                                modifier = Modifier.padding(bottom = 20.dp, top = 20.dp),
                                fontSize = 20.sp
                            )
                            Text(
                                text = "ورژن جدید: " + IotHub.appVersion.toString(),
                                modifier = Modifier.padding(bottom = 20.dp, top = 20.dp),
                                fontSize = 20.sp
                            )
                            Button(
                                onClick = {
                                    openDialog.value = true
                                },
                            ) {
                                Text(
                                    text = "به روز رسانی",
                                    fontSize = 20.sp
                                )
                            }
                            if (openDialog.value) {
                                UpdateAlertDialog()
                            }
                            if (isDownloading.value) {

                                CircularProgressIndicator(modifier = Modifier.padding(top = 50.dp))
                                Text(
                                    text = "در حال دانلود",
                                    modifier = Modifier.padding(bottom = 10.dp, top = 10.dp)
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    @Composable
    fun UpdateAlertDialog() {
        AlertDialog(
            onDismissRequest = {
                openDialog.value = false
            },
            buttons = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = "نرم افزار به روز رسانی شود؟",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontSize = 22.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            downloadApkFile()
                            openDialog.value = false
                            isDownloading.value = true

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(
                            onClick = { openDialog.value = false },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "خیر")
                        }
                    }
                }
            }
        )
    }

    @Preview
    @Composable
    fun Preview() {
        AboutUsUI()
    }
}