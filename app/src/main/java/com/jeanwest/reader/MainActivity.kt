package com.jeanwest.reader

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.jeanwest.reader.add.AddProductActivity
import com.jeanwest.reader.add.LoginToSettingActivity
import com.jeanwest.reader.confirm.ConfirmScanningLogin
import com.jeanwest.reader.finding.FindingProductActivity
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.transfer.TransferenceActivityLogIn
import com.jeanwest.reader.warehouseScanning.WarehouseScanningUserLogin
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    lateinit var rf: RFIDWithUHFUART
    private lateinit var memory: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        memory = PreferenceManager.getDefaultSharedPreferences(this)

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }
        while (!rf.init()) {
            rf.free()
        }
        if (Build.MODEL == "EXARKXK650") {
            while (!rf.setFrequencyMode(0x08)) {
                rf.free()
            }
        } else if (Build.MODEL == "c72") {
            while (!rf.setFrequencyMode(0x04)) {
                rf.free()
            }
        }
        while (!rf.setRFLink(2)) {
            rf.free()
        }
    }

    override fun onResume() {
        super.onResume()
        setContent {
            MainMenu()
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

        val scaffoldState = rememberScaffoldState()
        val scope = rememberCoroutineScope()

        MyApplicationTheme() {

            Scaffold(
                scaffoldState = scaffoldState,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 0.dp, end = 60.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "مدیریت محصولات", textAlign = TextAlign.Center,
                                )
                            }
                        },
                        navigationIcon = {
                            Box(
                                modifier = Modifier.width(60.dp)
                            ) {
                                IconButton(
                                    onClick = { scope.launch { scaffoldState.drawerState.open() } },
                                ) {
                                    Icon(Icons.Filled.Menu, "")
                                }
                            }
                        }
                    )
                },
                content = {

                    Column(
                        modifier = Modifier.padding(vertical = 40.dp, horizontal = 20.dp)
                    ) {

                        Row(
                            modifier = Modifier.weight(1F)
                        ) {

                            Box(
                                modifier = Modifier
                                    .padding(top = 0.dp, bottom = 10.dp, start = 0.dp, end = 10.dp)
                                    .weight(1F)
                                    .fillMaxSize(),
                            ) {
                                AddProductButton()
                            }

                            Box(
                                modifier = Modifier
                                    .padding(top = 0.dp, bottom = 10.dp, start = 10.dp, end = 0.dp)
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
                                    .padding(top = 10.dp, bottom = 10.dp, start = 0.dp, end = 10.dp)
                                    .weight(1F)
                                    .fillMaxSize(),
                            ) {
                                TransferButton()
                            }

                            Box(
                                Modifier
                                    .padding(top = 10.dp, bottom = 10.dp, start = 10.dp, end = 0.dp)
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
                                    .padding(top = 10.dp, bottom = 10.dp, start = 0.dp, end = 10.dp)
                                    .weight(1F)
                                    .fillMaxSize(),
                            ) {
                                AddProductSettingButton()
                            }

                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp, bottom = 10.dp, start = 10.dp, end = 0.dp)
                                    .weight(1F)
                                    .fillMaxSize()
                            ) {
                                ConfirmButton()
                            }
                        }
                    }
                },

                drawerContent = {

                    Column(
                        modifier = Modifier
                            .weight(1F)
                    ) {}

                    Column(modifier = Modifier
                        .weight(2F)
                        .background(color = MaterialTheme.colors.background)
                    ) {
                        LoginButton()
                        AboutUsButton()
                    }
                },
                drawerBackgroundColor = MaterialTheme.colors.primary,
                drawerContentColor = MaterialTheme.colors.onSecondary,
            )
        }
    }

    @Composable
    fun AddProductSettingButton() {
        Button(
            onClick = {
                if (memory.getString("username", "empty") == "") {
                    Toast.makeText(
                        this,
                        "دسترسی به این امکان برای حساب کاربری شما تعریف نشده است!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val intent = Intent(this, LoginToSettingActivity::class.java)
                    startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
        ) {
            Column {

                Spacer(modifier = Modifier.weight(0.5F))

                Icon(
                    painter = painterResource(R.drawable.ic_baseline_settings_24),
                    tint = colorResource(id = R.color.OrangeRed),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize()
                )
                Spacer(modifier = Modifier.weight(0.5F))
                Text(
                    "تنظیمات",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
            }
        }
    }

    @Composable
    fun ConfirmButton() {
        Button(
            onClick = {
                if (memory.getString("username", "empty") == "") {
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
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
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
                    fontSize = 20.sp
                )
            }
        }
    }

    @Composable
    fun LoginButton() {

        var loginButtonText by remember { mutableStateOf("") }

        memory = PreferenceManager.getDefaultSharedPreferences(this)

        if (memory.getString("username", "empty") != "") {

            username = memory.getString("username", "empty")!!
            token = memory.getString("accessToken", "empty")!!
            loginButtonText = "خروج از حساب $username"
        } else {

            loginButtonText = "ورود به حساب کاربری"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (memory.getString("username", "empty") != "") {

                        val editor: SharedPreferences.Editor = memory.edit()
                        editor.putString("accessToken", "")
                        editor.putString("username", "")
                        editor.apply()
                        username = ""
                        token = ""
                        loginButtonText = "ورود به حساب کاربری"
                    } else {

                        val intent =
                            Intent(this@MainActivity, UserLoginActivity::class.java)
                        startActivity(intent)
                    }
                }
                .padding(start = 20.dp, top = 10.dp, bottom = 10.dp)
        ) {

            Icon(
                painter = painterResource(R.drawable.ic_baseline_person_24),
                tint = colorResource(id = R.color.MediumAquamarine),
                contentDescription = "",
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = loginButtonText,
                textAlign = TextAlign.Center,
                fontSize = 18.sp
            )
        }
    }

    @Composable
    fun AboutUsButton() {

        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent =
                    Intent(this@MainActivity, AboutUsActivity::class.java)
                startActivity(intent)
            }
            .padding(start = 20.dp, top = 10.dp, bottom = 10.dp)) {

            Icon(
                painter = painterResource(R.drawable.ic_baseline_info_24),
                tint = colorResource(id = R.color.DarkBlue),
                contentDescription = "",
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                "درباره ما",
                textAlign = TextAlign.Center,
                fontSize = 18.sp
            )
        }

    }

    @Composable
    fun WarehouseScanning() {
        Button(
            onClick = {
                if (memory.getString("username", "empty") == "") {
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
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
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
                    fontSize = 20.sp
                )
            }
        }
    }

    @Composable
    fun TransferButton() {
        Button(
            onClick = {
                if (memory.getString("username", "empty") == "") {
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
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
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
                    fontSize = 20.sp
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
                    FindingProductActivity::class.java
                )
                startActivity(intent)
            },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
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
                    fontSize = 20.sp
                )
            }
        }
    }

    @Composable
    fun AddProductButton() {

        Button(
            onClick = {
                if (memory.getString("username", "empty") == "") {
                    Toast.makeText(
                        this,
                        "دسترسی به این امکان برای حساب کاربری شما تعریف نشده است!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val intent = Intent(this, AddProductActivity::class.java)
                    startActivity(intent)
                }
            },

            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
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
                    "اضافه کردن",
                    modifier = Modifier
                        .weight(2F)
                        .fillMaxSize(),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        MyApplicationTheme {
            MainMenu()
        }
    }
}