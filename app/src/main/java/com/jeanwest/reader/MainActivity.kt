package com.jeanwest.reader

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.jeanwest.reader.centralWarehouseCheckIn.CentralWarehouseCheckInActivity
import com.jeanwest.reader.checkIn.GetCheckInPropertiesActivity
import com.jeanwest.reader.checkOut.CheckOutActivity
import com.jeanwest.reader.count.CountActivity
import com.jeanwest.reader.inventory.InventoryActivity
import com.jeanwest.reader.sharedClassesAndFiles.ExceptionHandler
import com.jeanwest.reader.sharedClassesAndFiles.rfInit
import com.jeanwest.reader.iotHub.IotHub
import com.jeanwest.reader.logIn.OperatorLoginActivity
import com.jeanwest.reader.logIn.UserLoginActivity
import com.jeanwest.reader.manualRefill.ManualRefillActivity
import com.jeanwest.reader.refill.RefillActivity
import com.jeanwest.reader.search.SearchActivity
import com.jeanwest.reader.sharedClassesAndFiles.ErrorSnackBar
import com.jeanwest.reader.sharedClassesAndFiles.theme.MyApplicationTheme
import com.jeanwest.reader.sharedClassesAndFiles.theme.borderColor
import com.jeanwest.reader.write.WriteActivity
import com.jeanwest.reader.sharedClassesAndFiles.test.RFIDWithUHFUART
import com.rscja.deviceapi.exception.ConfigurationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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

        clearCash()

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

        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler()!!))
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

    private fun syncServerToLocalWarehouse() {

        val url = "https://rfid-api.avakatan.ir/department-infos/sync"
        val request = object : StringRequest(Method.GET, url,  {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "ارسال و دریافت با موفقیت انجام شد",
                    null,
                    SnackbarDuration.Long
                )
            }

        }, {

            if(it is NoConnectionError) {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            "اینترنت قطع است. شبکه وای فای را بررسی کنید.",
                            null,
                            SnackbarDuration.Long
                        )
                    }
                } else {
                    CoroutineScope(Dispatchers.Default).launch {
                        state.showSnackbar(
                            it.toString(),
                            null,
                            SnackbarDuration.Long
                        )
                    }
                }
        }) {

            override fun getHeaders(): MutableMap<String, String> {
                val header = mutableMapOf<String, String>()
                header["accept"] = "application/json"
                header["Content-Type"] = "application/json"
                header["Authorization"] = "Bearer $token"
                return header
            }
        }

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
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

    @OptIn(ExperimentalFoundationApi::class)
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

                                OpenActivityButton("خطی", R.drawable.refill) {
                                    val intent = Intent(this@MainActivity, RefillActivity::class.java)
                                    startActivity(intent)
                                }
                                OpenActivityButton("تروفالس", R.drawable.inventory) {
                                    val intent = Intent(this@MainActivity, GetCheckInPropertiesActivity::class.java)
                                    startActivity(intent)
                                }
                                OpenActivityButton("جستجو", R.drawable.search) {
                                    val intent = Intent(this@MainActivity, SearchActivity::class.java)
                                    startActivity(intent)
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OpenActivityButton("شمارش", R.drawable.counter) {
                                    val intent = Intent(this@MainActivity, CountActivity::class.java)
                                    startActivity(intent)
                                }
                                OpenActivityButton("رایت", R.drawable.write) {
                                    val intent = Intent(this@MainActivity, WriteActivity::class.java)
                                    startActivity(intent)
                                }
                                OpenActivityButton("شارژ", R.drawable.check_in) {
                                    val intent = Intent(this@MainActivity, ManualRefillActivity::class.java)
                                    startActivity(intent)
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OpenActivityButton("ثبت حواله", R.drawable.check_in) {
                                    val intent = Intent(this@MainActivity, CheckOutActivity::class.java)
                                    startActivity(intent)
                                }
                                OpenActivityButton(
                                    text = "انبارگردانی",
                                    iconId = R.drawable.inventory
                                ) {
                                    Intent(this@MainActivity, InventoryActivity::class.java).apply {
                                        startActivity(this)
                                    }
                                }
                                OpenActivityButton(
                                    text = "تروفالس انبار مرکزی",
                                    iconId = R.drawable.inventory
                                ) {
                                    Intent(this@MainActivity, CentralWarehouseCheckInActivity::class.java).apply {
                                        startActivity(this)
                                    }
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
    fun OpenActivityButton(text : String, iconId : Int, onClick : () -> Unit) {

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
                                    Intent(this@MainActivity, UserLoginActivity::class.java)
                                intent.flags += Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }, modifier = Modifier.padding(end  = 8.dp)
                        ) {
                            Text(text = "خروج از حساب")
                        }

                        Button(
                            onClick = {
                                openAccountDialog = false
                                syncServerToLocalWarehouse()
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