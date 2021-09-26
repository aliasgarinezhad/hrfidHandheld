package com.jeanwest.reader.aboutUs

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.util.Log
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
import com.jeanwest.reader.theme.MyApplicationTheme
import java.io.File

class AboutUsActivity : ComponentActivity() {

    private var isDownloading = mutableStateOf(false)
    private var openDialog = mutableStateOf(false)

    var api = UpdateAPI()
    var handler = Handler()
    var thread: Runnable = object : Runnable {
        override fun run() {

            if (api.finished) {

                isDownloading.value = false

                if (!api.response.equals("ok")) {
                    Toast.makeText(this@AboutUsActivity, api.response, Toast.LENGTH_LONG).show()
                } else {

                    val path =
                        Environment.getExternalStorageDirectory().toString() + "/" + "app.apk"

                    val file = File(path)
                    if (file.exists()) {
                        val intent = Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(
                            uriFromFile(applicationContext, File(path)),
                            "application/vnd.android.package-archive"
                        );
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            applicationContext.startActivity(intent);
                        } catch (e: ActivityNotFoundException) {
                            e.printStackTrace();
                            Log.e("TAG", "Error in opening the file!");
                        }
                    } else {
                        Toast.makeText(applicationContext, "Error", Toast.LENGTH_LONG).show();
                    }
                }

            } else {
                handler.postDelayed(this, 500)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AboutUsUI()
        }

        api.context = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse(String.format("package:%s", packageName))), 2
                )
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


    @Composable
    fun AboutUsUI() {
        MyApplicationTheme {

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = {
                        TopAppBar(

                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
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
                            Button(onClick = {
                                openDialog.value = true
                            },) {
                                Text(text = "به روز رسانی",
                                    fontSize = 20.sp)
                            }
                            if (openDialog.value) {
                                UpdateAlertDialog()
                            }
                            if(isDownloading.value) {

                                CircularProgressIndicator(modifier = Modifier.padding(top = 50.dp))
                                Text(text = "در حال دانلود",
                                    modifier = Modifier.padding(bottom = 10.dp, top = 10.dp))
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

                Column (modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {

                    Text(text = "نرم افزار به روز رسانی شود؟", modifier = Modifier.padding(bottom = 10.dp))

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {

                        Button(onClick = {
                            api = UpdateAPI()
                            api.start()
                            handler.postDelayed(thread, 1000)
                            openDialog.value = false
                            isDownloading.value = true

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "بله")
                        }
                        Button(onClick = { openDialog.value = false }, modifier = Modifier.padding(top = 10.dp, start = 20.dp)) {
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