package com.jeanwest.reader

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.jeanwest.reader.aboutUs.AboutUsActivity
import com.jeanwest.reader.write.WriteActivity
import com.jeanwest.reader.confirm.ConfirmScanningLogin
import com.jeanwest.reader.count.CountActivity
import com.jeanwest.reader.iotHub.IotHub
import com.jeanwest.reader.search.SearchActivity
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.transfer.TransferenceActivityLogIn
import com.jeanwest.reader.warehouseScanning.WarehouseScanningUserLogin
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException


class MainActivity : ComponentActivity() {

    private var openAccountDialog by mutableStateOf(false)
    lateinit var rf: RFIDWithUHFUART
    private lateinit var memory: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

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

        memory = PreferenceManager.getDefaultSharedPreferences(this)

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        rfInit()

        loadMemory()

        if (username == "") {
            val intent =
                Intent(this@MainActivity, UserLoginActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        Intent(this, IotHub::class.java).also { intent ->
            startService(intent)
        }

        if(BuildConfig.VERSION_NAME != IotHub.appVersion) {
            Toast.makeText(this, "به روز رسانی موجود است", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadMemory() {

        memory = PreferenceManager.getDefaultSharedPreferences(this)

        if (memory.getString("username", "") != "") {

            username = memory.getString("username", "")!!
            token = memory.getString("accessToken", "")!!
        }
    }

    private fun rfInit() {

        val frequency: Int
        val rfLink = 2

        when (Build.MODEL) {
            getString(R.string.EXARK) -> {
                frequency = 0x08
            }
            getString(R.string.chainway) -> {
                frequency = 0x04
            }
            else -> {
                Toast.makeText(
                    this,
                    "این دستگاه توسط برنامه پشتیبانی نمی شود",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        for (i in 0..6) {

            if (rf.init()) {
                break
            } else if (i == 5) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            } else {
                rf.free()
            }
        }

        for (i in 0..11) {

            if (rf.setFrequencyMode(frequency)) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        for (i in 0..11) {

            if (rf.setRFLink(rfLink)) {
                break
            } else if (i == 10) {

                Toast.makeText(
                    this,
                    "مشکلی در سخت افزار پیش آمده است",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setContent {
            MainMenu()
        }

        if (username == "") {
            val intent =
                Intent(this@MainActivity, UserLoginActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 4) {
            rf.free()
            finish()
        }
        return true
    }

    companion object {

        var username = ""
        var token = ""
    }

    @Composable
    fun MainMenu() {

        MyApplicationTheme {

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 40.dp, end = 0.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Jeanswest", textAlign = TextAlign.Center,
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    openAccountDialog = true
                                })
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_baseline_person_24),
                                        contentDescription = "",
                                    )
                                }
                            }
                        )
                    },
                    content = {

                        Column(
                            modifier = Modifier.padding(vertical = 40.dp, horizontal = 20.dp)
                        ) {

                            if (openAccountDialog) {
                                AccountAlertDialog()
                            }

                            Row(
                                modifier = Modifier.weight(1F)
                            ) {

                                Box(
                                    modifier = Modifier
                                        .padding(
                                            top = 0.dp,
                                            bottom = 10.dp,
                                            start = 0.dp,
                                            end = 10.dp
                                        )
                                        .weight(1F)
                                        .fillMaxSize(),
                                ) {
                                    AddProductButton()
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(
                                            top = 0.dp,
                                            bottom = 10.dp,
                                            start = 10.dp,
                                            end = 0.dp
                                        )
                                        .weight(1F)
                                        .fillMaxSize(),
                                ) {
                                    FindingButton()
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1F)
                            ) {

                                Box(
                                    Modifier
                                        .padding(
                                            top = 10.dp,
                                            bottom = 10.dp,
                                            start = 0.dp,
                                            end = 10.dp
                                        )
                                        .weight(1F)
                                        .fillMaxSize(),
                                ) {
                                    TransferButton()
                                }

                                Box(
                                    Modifier
                                        .padding(
                                            top = 10.dp,
                                            bottom = 10.dp,
                                            start = 10.dp,
                                            end = 0.dp
                                        )
                                        .weight(1F)
                                        .fillMaxSize(),
                                ) {
                                    WarehouseScanning()
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1F)
                            ) {

                                Box(
                                    modifier = Modifier
                                        .padding(
                                            top = 10.dp,
                                            bottom = 10.dp,
                                            start = 0.dp,
                                            end = 10.dp
                                        )
                                        .weight(1F)
                                        .fillMaxSize(),
                                ) {
                                    CountButton()
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(
                                            top = 10.dp,
                                            bottom = 10.dp,
                                            start = 10.dp,
                                            end = 0.dp
                                        )
                                        .weight(1F)
                                        .fillMaxSize()
                                ) {
                                    ConfirmButton()
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    @Composable
    fun CountButton() {

        Button(
            onClick = {
                if (memory.getString("username", "") == "") {
                    Toast.makeText(
                        this,
                        "دسترسی به این امکان برای حساب کاربری شما تعریف نشده است!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val intent = Intent(this, CountActivity::class.java)
                    startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {

                Spacer(modifier = Modifier.weight(0.5F))

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_attach_file_24),
                    tint = colorResource(id = R.color.OrangeRed),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize()
                )
                Spacer(modifier = Modifier.weight(0.5F))
                Text(
                    "شمارش",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h1
                )
            }
        }
    }

    @Composable
    fun ConfirmButton() {
        Button(
            onClick = {
                if (memory.getString("username", "") == "") {
                    Toast.makeText(
                        this,
                        "دسترسی به این امکان برای حساب کاربری شما تعریف نشده است!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val intent = Intent(this, ConfirmScanningLogin::class.java)
                    startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {

                Spacer(modifier = Modifier.weight(0.5F))

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_playlist_add_check_24),
                    tint = colorResource(id = R.color.DarkGreen),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize()
                )
                Spacer(modifier = Modifier.weight(0.5F))
                Text(
                    stringResource(R.string.confirmText),
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h1,
                )
            }
        }
    }

    @Composable
    fun WarehouseScanning() {
        Button(
            onClick = {
                if (memory.getString("username", "") == "") {
                    Toast.makeText(
                        this,
                        "دسترسی به این امکان برای حساب کاربری شما تعریف نشده است!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val intent = Intent(this, WarehouseScanningUserLogin::class.java)
                    startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {

                Spacer(modifier = Modifier.weight(0.5F))

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_format_list_bulleted_24),
                    tint = colorResource(id = R.color.Purple),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize()
                )
                Spacer(modifier = Modifier.weight(0.5F))
                Text(
                    "انبارگردانی",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h1
                )
            }
        }
    }

    @Composable
    fun TransferButton() {
        Button(
            onClick = {
                if (memory.getString("username", "") == "") {
                    Toast.makeText(
                        this,
                        "دسترسی به این امکان برای حساب کاربری شما تعریف نشده است!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val intent = Intent(this, TransferenceActivityLogIn::class.java)
                    startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {

                Spacer(modifier = Modifier.weight(0.5F))

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_playlist_add_24),
                    tint = colorResource(id = R.color.YellowGreen),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize()
                )
                Spacer(modifier = Modifier.weight(0.5F))
                Text(
                    stringResource(R.string.transferText),
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h1
                )
            }
        }
    }

    @Composable
    fun FindingButton() {
        Button(
            onClick = {
                val intent = Intent(
                    this@MainActivity,
                    SearchActivity::class.java
                )
                startActivity(intent)
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {

                Spacer(modifier = Modifier.weight(0.5F))

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_search_24),
                    tint = colorResource(id = R.color.Goldenrod),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize()
                )
                Spacer(modifier = Modifier.weight(0.5F))
                Text(
                    "جست و جو",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h1,
                )
            }
        }
    }

    @Composable
    fun AddProductButton() {

        Button(
            onClick = {
                if (memory.getString("username", "") == "") {
                    Toast.makeText(
                        this,
                        "دسترسی به این امکان برای حساب کاربری شما تعریف نشده است!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val intent = Intent(this, WriteActivity::class.java)
                    startActivity(intent)
                }
            },

            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {

                Spacer(modifier = Modifier.weight(0.5F))

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_add_circle_outline_24),
                    tint = colorResource(id = R.color.Brown),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize()
                )
                Spacer(modifier = Modifier.weight(0.5F))
                Text(
                    "رایت",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h1
                )
            }
        }
    }

    @Composable
    fun AccountAlertDialog() {

        AlertDialog(
            onDismissRequest = {
                openAccountDialog = false
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
                        text = username,
                        modifier = Modifier.padding(bottom = 10.dp),
                        style = MaterialTheme.typography.h1,
                        fontSize = 24.sp
                    )

                    Row(horizontalArrangement = Arrangement.SpaceAround) {

                        Button(onClick = {
                            openAccountDialog = false
                            val intent =
                                Intent(
                                    this@MainActivity,
                                    AboutUsActivity::class.java
                                )
                            startActivity(intent)

                        }, modifier = Modifier.padding(top = 10.dp, end = 20.dp)) {
                            Text(text = "به روز رسانی")
                        }
                        Button(
                            onClick = {
                                openAccountDialog = false
                                val editor: SharedPreferences.Editor = memory.edit()
                                editor.putString("accessToken", "")
                                editor.putString("username", "")
                                editor.apply()
                                username = ""
                                token = ""
                                val intent =
                                    Intent(this@MainActivity, UserLoginActivity::class.java)
                                intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "خروج از حساب")
                        }
                    }
                }
            }
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        MyApplicationTheme {
            MainMenu()
        }
    }
}