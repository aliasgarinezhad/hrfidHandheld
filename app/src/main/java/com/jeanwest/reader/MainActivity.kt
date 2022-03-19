package com.jeanwest.reader

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import coil.annotation.ExperimentalCoilApi
import com.jeanwest.reader.checkIn.GetBarcodesByCheckInNumberActivity
import com.jeanwest.reader.checkOut.CheckOutActivity
import com.jeanwest.reader.count.CountActivity
import com.jeanwest.reader.iotHub.IotHub
import com.jeanwest.reader.logIn.OperatorLoginActivity
import com.jeanwest.reader.logIn.UserLoginActivity
import com.jeanwest.reader.manualRefillWarehouseManager.ManualRefillWarehouseManagerActivity
import com.jeanwest.reader.refill.RefillActivity
import com.jeanwest.reader.search.SearchActivity
import com.jeanwest.reader.theme.CustomSnackBar
import com.jeanwest.reader.theme.MyApplicationTheme
import com.jeanwest.reader.write.WriteActivity
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException


@ExperimentalCoilApi
class MainActivity : ComponentActivity() {

    private var openAccountDialog by mutableStateOf(false)
    lateinit var rf: RFIDWithUHFUART
    private lateinit var memory: SharedPreferences
    private var deviceId = ""
    private var buttonSize = 100.dp
    private var iconSize = 48.dp
    private var pageSize = buttonSize * 3 + 100.dp
    private var userLocationCode = 0
    private var fullName = ""
    private var state = SnackbarHostState()

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

        rfInit(rf, this, state)

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
                                RefillButton()
                                CheckInButton()
                                SearchButton()
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CountButton()
                                WriteTagButton()
                                ManualRefillWarehouseManagerButton()
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                checkOut()
                                Box(modifier = Modifier.size(100.dp)) {}
                                Box(modifier = Modifier.size(100.dp)) {}
                            }
                        }
                    },
                    snackbarHost = { CustomSnackBar(state) },
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
                tint = MaterialTheme.colors.primary,
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                "شمارش",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3
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
                tint = MaterialTheme.colors.primary,
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(R.string.checkInText),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3,
            )
        }
    }


    @Composable
    fun ManualRefillWarehouseManagerButton() {

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
                tint = MaterialTheme.colors.primary,
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(id = R.string.manualRefill),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3,
            )
        }
    }

    @Composable
    fun checkOut() {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .clickable {
                    val intent = Intent(this, CheckOutActivity::class.java)
                    startActivity(intent)
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )
        ) {

            Icon(
                painter = painterResource(R.drawable.check_in),
                tint = MaterialTheme.colors.primary,
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(R.string.transferText),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3
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
                tint = MaterialTheme.colors.primary,
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                "جست و جو",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3,
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
                tint = MaterialTheme.colors.primary,
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                "رایت",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3
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
                tint = MaterialTheme.colors.primary,
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                stringResource(id = R.string.refill),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3
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
                        .width(240.dp)
                        .height(136.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = fullName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
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