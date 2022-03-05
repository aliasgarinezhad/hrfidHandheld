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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.jeanwest.reader.aboutUs.AboutUsActivity
import com.jeanwest.reader.checkIn.GetBarcodesByCheckInNumberActivity
import com.jeanwest.reader.count.CountActivity
import com.jeanwest.reader.iotHub.IotHub
import com.jeanwest.reader.logIn.OperatorLoginActivity
import com.jeanwest.reader.logIn.UserLoginActivity
import com.jeanwest.reader.manualRefill.ManualRefillActivity
import com.jeanwest.reader.manualRefillWarehouseManager.ManualRefillWarehouseManagerActivity
import com.jeanwest.reader.refill.RefillActivity
import com.jeanwest.reader.search.SearchActivity
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.write.WriteActivity
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException


class MainActivity : ComponentActivity() {

    private var openAccountDialog by mutableStateOf(false)
    lateinit var rf: RFIDWithUHFUART
    private lateinit var memory: SharedPreferences
    private var deviceId = ""
    private var buttonSize = 140.dp
    private var iconSize = 70.dp
    private var pageSize = buttonSize * 3 + 150.dp
    private var userLocationCode = 0
    private var fullName = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Intent(this, IotHub::class.java).also { intent ->
            startService(intent)
        }

        memory = PreferenceManager.getDefaultSharedPreferences(this)

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

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

        rfInit()

        loadMemory()
    }

    private fun loadMemory() {

        memory = PreferenceManager.getDefaultSharedPreferences(this)

        userLocationCode = memory.getInt("userLocationCode", 0)
        username = memory.getString("username", "") ?: ""
        token = memory.getString("accessToken", "") ?: ""
        deviceId = memory.getString("deviceId", "") ?: ""
        fullName = memory.getString("userFullName", "") ?: ""
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

        if (deviceId == "") {
            val intent =
                Intent(this@MainActivity, OperatorLoginActivity::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else if (username == "") {
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
                                        .padding(start = 40.dp)
                                        .height(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_jeanswest_logo),
                                        contentDescription = "",
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
                            verticalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.size(pageSize),
                        ) {

                            if (openAccountDialog) {
                                AccountAlertDialog()
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                WriteTagButton()
                                SearchButton()
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RefillButton()
                                WarehouseScanning()
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CountButton()
                                CheckInButton()
                            }
                        }
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun CountButton() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    val intent = Intent(this, CountActivity::class.java)
                    startActivity(intent)
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {

            Icon(
                painter = painterResource(R.drawable.counter),
                tint = colorResource(id = R.color.OrangeRed),
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                "شمارش",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h1
            )
        }
    }

    @Composable
    fun CheckInButton() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    val intent = Intent(this, GetBarcodesByCheckInNumberActivity::class.java)
                    startActivity(intent)
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {
            Icon(
                painter = painterResource(R.drawable.true_flase),
                tint = colorResource(id = R.color.DarkGreen),
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(R.string.checkInText),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h1,
            )
        }
    }


    @Composable
    fun WarehouseScanning() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    val intent = Intent(this, ManualRefillWarehouseManagerActivity::class.java)
                    startActivity(intent)
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {

            Icon(
                painter = painterResource(R.drawable.check_in),
                contentDescription = "",
                tint = colorResource(id = R.color.Purple),
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(id = R.string.manualRefill),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h1,
            )
        }
    }

    @Composable
    fun TransferButton() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    /*val intent = Intent(this, TransferenceActivityLogIn::class.java)
                    startActivity(intent)*/
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {

            Icon(
                painter = painterResource(R.drawable.check_in),
                tint = colorResource(id = R.color.YellowGreen),
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(R.string.transferText),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h1
            )
        }
    }

    @Composable
    fun SearchButton() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    val intent = Intent(
                        this@MainActivity,
                        SearchActivity::class.java
                    )
                    startActivity(intent)
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {

            Icon(
                painter = painterResource(R.drawable.search),
                tint = colorResource(id = R.color.Goldenrod),
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                "جست و جو",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h1,
            )
        }
    }

    @Composable
    fun WriteTagButton() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    val intent = Intent(this, WriteActivity::class.java)
                    startActivity(intent)
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {

            Icon(
                painter = painterResource(R.drawable.write),
                tint = colorResource(id = R.color.Brown),
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                "رایت",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h1
            )
        }
    }

    @Composable
    fun RefillButton() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    val intent = Intent(this, RefillActivity::class.java)
                    startActivity(intent)
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {

            Icon(
                painter = painterResource(R.drawable.refill),
                tint = colorResource(id = R.color.YellowGreen),
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(id = R.string.refill),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h1
            )
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
                        .width(300.dp)
                        .height(130.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {

                    Text(
                        text = fullName,
                        modifier = Modifier
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Button(
                            onClick = {
                                openAccountDialog = false
                                val intent =
                                    Intent(
                                        this@MainActivity,
                                        AboutUsActivity::class.java
                                    )
                                startActivity(intent)

                            },
                        ) {
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
                        ) {
                            Text(text = "خروج از حساب")
                        }
                    }
                }
            }
        )
    }
}