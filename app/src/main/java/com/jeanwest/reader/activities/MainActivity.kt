package com.jeanwest.reader.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import com.google.gson.Gson
import com.jeanwest.reader.R
import com.jeanwest.reader.data.syncServerToLocalWarehouse
import com.jeanwest.reader.data.userLogin
import com.jeanwest.reader.hardware.rfInit
import com.jeanwest.reader.management.ExceptionHandler
import com.jeanwest.reader.management.IotHub
import com.jeanwest.reader.test.RFIDWithUHFUART
import com.jeanwest.reader.ui.*
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    private var userWarehouses = mutableMapOf<String, String>()
    private var userWarehouse = 0
    private var openAccountDialog by mutableStateOf(false)
    lateinit var rf: RFIDWithUHFUART
    private var deviceId = ""
    private var buttonPadding = 24.dp
    private var userLocationCode = 0
    private var userFullName = ""
    private var state = SnackbarHostState()
    private lateinit var queue: RequestQueue
    private var loginMode by mutableStateOf(false)
    private var username by mutableStateOf("")
    private var password by mutableStateOf("")
    var loading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        queue = Volley.newRequestQueue(this)

        clearCash()

        requestPermissions()

        loadMemory()
        if (deviceId != "") {
            startService(Intent(this, IotHub::class.java))
        }

        try {
            rf = RFIDWithUHFUART.getInstance()
        } catch (e: ConfigurationException) {
            e.printStackTrace()
        }

        CoroutineScope(Dispatchers.Default).launch {
            loading = true
            delay(1000)
            rfInit(rf, this@MainActivity, state)
            loading = false
        }

        Thread.setDefaultUncaughtExceptionHandler(
            ExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler()!!
            )
        )
    }

    override fun onResume() {
        super.onResume()
        setContent {
            Page()
        }

        if (deviceId == "") {
            val intent =
                Intent(this@MainActivity, DeviceRegister::class.java)
            intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else if (token == "") {
            loginMode = true
        }
    }

    private fun requestPermissions() {
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
    }

    private fun login() {

        loading = true

        userLogin(
            queue,
            state,
            username,
            password,
            { assignedToken, fullName, assignedLocation, assignedWarehouse, warehouses ->

                userLocationCode = assignedLocation
                userWarehouse = assignedWarehouse
                token = assignedToken
                userFullName = fullName
                userWarehouses = warehouses
                saveToMemory()
                loginMode = false
                loading = false
            },
            {
                loading = false
            })
    }

    private fun saveToMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = memory.edit()

        editor.putString("accessToken", token)
        editor.putString("username", username)
        editor.putString("userFullName", userFullName)
        editor.putInt("userLocationCode", userLocationCode)
        editor.putInt("userWarehouseCode", userWarehouse)
        editor.putString("userWarehouses", Gson().toJson(userWarehouses).toString())
        editor.apply()
    }

    private fun loadMemory() {

        val memory = PreferenceManager.getDefaultSharedPreferences(this)

        userLocationCode = memory.getInt("userLocationCode", 0)
        username = memory.getString("username", "") ?: ""
        token = memory.getString("accessToken", "") ?: ""
        deviceId = memory.getString("deviceId", "") ?: ""
        userFullName = memory.getString("userFullName", "") ?: ""
    }

    private fun clearCash() {
        loading = true
        deleteRecursive(cacheDir)
        deleteRecursive(codeCacheDir)
        loading = false
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
        var token = ""
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { if (loginMode) AppBar2() else AppBar() },
                    content = { if (loginMode) Content2() else Content() },
                    snackbarHost = { ErrorSnackBar(state) },
                )
            }
        }
    }

    @Composable
    fun AppBar() {
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
                    }, modifier = Modifier.testTag("account"))
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_person_24),
                            contentDescription = "",
                        )
                    }
                }
            )
        }
    }

    @Composable
    fun Content() {

        Column {
            if (loading) {
                LoadingCircularProgressIndicator(isDataLoading = loading)
            } else {

                LazyColumn(
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    item {

                        if (openAccountDialog) {
                            AccountAlertDialog()
                        }

                        Row(

                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
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
                            modifier = Modifier
                                .fillMaxWidth()
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
                            modifier = Modifier
                                .fillMaxWidth()
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
                            modifier = Modifier
                                .fillMaxWidth()
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
                            modifier = Modifier
                                .fillMaxWidth()
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
                            Box(Modifier.size(100.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Content2() {

        Column {

            if (loading) {
                LoadingCircularProgressIndicator(isDataLoading = loading)
            } else {

                SimpleTextField(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp)
                        .fillMaxWidth(),
                    hint = "نام کاربری خود را وارد کنید",
                    onValueChange = { username = it },
                    value = username,
                )

                SimpleTextField(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                        .fillMaxWidth(),
                    hint = "رمز عبور خود را وارد کنید",
                    onValueChange = { password = it },
                    value = password,
                )

                BigButton(text = "ورود") {
                    login()
                }
            }
        }
    }

    @Composable
    fun AppBar2() {

        TopAppBar(

            title = {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ورود به حساب کاربری", textAlign = TextAlign.Center,
                    )
                }
            },
        )
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
                        text = userFullName,
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
                                token = ""
                                password = ""
                                saveToMemory()
                                loginMode = true
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
