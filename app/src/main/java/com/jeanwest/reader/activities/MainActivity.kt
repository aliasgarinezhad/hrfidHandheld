package com.jeanwest.reader.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.R
import com.jeanwest.reader.management.IotHub
import com.jeanwest.reader.management.ErrorSnackBar
import com.jeanwest.reader.management.ExceptionHandler
import com.jeanwest.reader.management.rfInit
import com.jeanwest.reader.management.syncServerToLocalWarehouse
import com.jeanwest.reader.test.RFIDWithUHFUART
import com.jeanwest.reader.ui.MyApplicationTheme
import com.jeanwest.reader.ui.borderColor
import com.rscja.deviceapi.exception.ConfigurationException
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    private var openAccountDialog by mutableStateOf(false)
    lateinit var rf: RFIDWithUHFUART
    private lateinit var memory: SharedPreferences
    private var deviceId = ""
    private var buttonSize = 100.dp
    private var iconSize = 48.dp
    private var buttonPadding = 24.dp
    private var userLocationCode = 0
    private var fullName = ""
    private var state = SnackbarHostState()
    private lateinit var queue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        queue = Volley.newRequestQueue(this)

        clearCash()

        if(deviceId != "") {
            startService(Intent(this, IotHub::class.java))
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

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
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
                Intent(this@MainActivity, OperatorLogin::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else if (username == "") {
            val intent =
                Intent(this@MainActivity, UserLogin::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun clearCash() {
        deleteRecursive(cacheDir)
        deleteRecursive(codeCacheDir)
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.let {
                for (child in it) {
                    deleteRecursive(child)
                }
            }
        }
        fileOrDirectory.delete()
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

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun MainMenu() {


        MyApplicationTheme {

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                Scaffold(
                    topBar = {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            TopAppBar(
                                title = {
                                    Row(
                                        modifier = Modifier
                                            .padding(start = 30.dp)
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
                        }
                    },
                    content = {

                        LazyColumn(
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            item {

                                if (openAccountDialog) {
                                    AccountAlertDialog()
                                }

                                Row(

                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(top = buttonPadding, bottom = buttonPadding)
                                ) {
                                    OpenActivityButton(
                                        stringResource(R.string.manualRefill),
                                        R.drawable.check_in
                                    ) {
                                        val intent =
                                            Intent(
                                                this@MainActivity,
                                                ManualRefill::class.java
                                            )
                                        startActivity(intent)
                                    }
                                    OpenActivityButton(
                                        stringResource(R.string.refill),
                                        R.drawable.refill
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, Refill::class.java)
                                        startActivity(intent)
                                    }
                                    OpenActivityButton(
                                        stringResource(R.string.refill2),
                                        R.drawable.refill
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, Refill2::class.java)
                                        startActivity(intent)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(bottom = buttonPadding)
                                ) {
                                    OpenActivityButton(
                                        stringResource(R.string.checkInText),
                                        R.drawable.inventory
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, ConfirmStockDraft::class.java)
                                        startActivity(intent)
                                    }
                                    OpenActivityButton(
                                        stringResource(R.string.checkOut),
                                        R.drawable.check_in
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, CreateStockDraft::class.java)
                                        startActivity(intent)
                                    }
                                    OpenActivityButton(
                                        stringResource(R.string.search),
                                        R.drawable.search
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, Kiosk::class.java)
                                        startActivity(intent)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(bottom = buttonPadding)
                                ) {
                                    OpenActivityButton(
                                        stringResource(R.string.stockDraftsList),
                                        R.drawable.ic_baseline_format_list_bulleted_24
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, StockDraftsList::class.java)
                                        startActivity(intent)
                                    }
                                    OpenActivityButton(
                                        text = stringResource(R.string.inventoryText),
                                        iconId = R.drawable.inventory
                                    ) {
                                        Intent(
                                            this@MainActivity,
                                            Inventory::class.java
                                        ).apply {
                                            startActivity(this)
                                        }
                                    }
                                    OpenActivityButton(
                                        stringResource(R.string.tagProgramming),
                                        R.drawable.write
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, WriteTag::class.java)
                                        startActivity(intent)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(bottom = buttonPadding)
                                ) {
                                    OpenActivityButton(
                                        stringResource(R.string.count),
                                        R.drawable.counter
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, Count::class.java)
                                        startActivity(intent)
                                    }
                                    OpenActivityButton(
                                        text = stringResource(R.string.centralCheckInCheckInText),
                                        iconId = R.drawable.inventory
                                    ) {
                                        Intent(
                                            this@MainActivity,
                                            AttachEPCsToStockDraft::class.java
                                        ).apply {
                                            startActivity(this)
                                        }
                                    }
                                    OpenActivityButton(
                                        text = stringResource(R.string.createCarton),
                                        iconId = R.drawable.check_in
                                    ) {
                                        Intent(
                                            this@MainActivity,
                                            CreateCarton::class.java
                                        ).apply {
                                            startActivity(this)
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(bottom = buttonPadding)
                                ) {
                                    OpenActivityButton(
                                        stringResource(R.string.showCarton),
                                        R.drawable.search
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, ShowCarton::class.java)
                                        startActivity(intent)
                                    }
                                    OpenActivityButton(
                                        stringResource(R.string.createStockDraftByCartons),
                                        R.drawable.check_in
                                    ) {
                                        val intent =
                                            Intent(this@MainActivity, CreateStockDraftByCartons::class.java)
                                        startActivity(intent)
                                    }
                                    Box(Modifier.size(buttonSize))
                                }
                            }
                        }
                    },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun OpenActivityButton(text: String, iconId: Int, onClick: () -> Unit) {

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .size(buttonSize)
                .shadow(elevation = 5.dp, shape = MaterialTheme.shapes.small)
                .border(
                    BorderStroke(1.dp, borderColor),
                    shape = MaterialTheme.shapes.small
                )
                .clickable {
                    onClick()
                }
                .background(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.onPrimary,
                )

        ) {

            Icon(
                painter = painterResource(iconId),
                tint = MaterialTheme.colors.primary,
                contentDescription = "",
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text,
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
                        .width(288.dp)
                        .height(120.dp),
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
                                    Intent(this@MainActivity, UserLogin::class.java)
                                intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }, modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(text = "خروج از حساب")
                        }

                        Button(
                            onClick = {
                                openAccountDialog = false
                                syncServerToLocalWarehouse(queue, state)
                            },
                        ) {
                            Text(text = "ارسال و دریافت")
                        }
                    }
                }
            }
        )
    }
}
