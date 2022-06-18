package com.jeanwest.reader.hardware

import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import android.content.Context
import android.os.Build
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import com.jeanwest.reader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


fun setRFEpcMode(rf: RFIDWithUHFUART, state: SnackbarHostState): Boolean {
    for (i in 0..11) {
        if (rf.setEPCMode()) {
            return true
        }
    }
    CoroutineScope(Dispatchers.Default).launch {
        state.showSnackbar(
            "مشکلی در سخت افزار پیش آمده است",
            null,
            SnackbarDuration.Long
        )
    }
    return false
}

fun setRFEpcAndTidMode(rf: RFIDWithUHFUART, state: SnackbarHostState): Boolean {

    for (i in 0..11) {
        if (rf.setEPCAndTIDMode()) {
            return true
        }
    }

    CoroutineScope(Dispatchers.Default).launch {
        state.showSnackbar(
            "مشکلی در سخت افزار پیش آمده است",
            null,
            SnackbarDuration.Long
        )
    }
    return false
}

fun rfInit(rf: RFIDWithUHFUART, context: Context, state: SnackbarHostState) {

    val frequency: Int
    val rfLink = 2

    when (Build.MODEL) {
        context.getString(R.string.EXARK) -> {
            frequency = 0x08
        }
        context.getString(R.string.chainway) -> {
            frequency = 0x04
        }
        else -> {
            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "این دستگاه توسط برنامه پشتیبانی نمی شود",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }
    }

    for (i in 0..11) {

        if (rf.init()) {
            break
        } else if (i == 10) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "مشکلی در سخت افزار پیش آمده است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        } else {
            rf.free()
        }
    }

    for (i in 0..11) {

        if (rf.setFrequencyMode(frequency)) {
            break
        } else if (i == 10) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "مشکلی در سخت افزار پیش آمده است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }
    }

    for (i in 0..11) {

        if (rf.setRFLink(rfLink)) {
            break
        } else if (i == 10) {

            CoroutineScope(Dispatchers.Default).launch {
                state.showSnackbar(
                    "مشکلی در سخت افزار پیش آمده است",
                    null,
                    SnackbarDuration.Long
                )
            }
            return
        }
    }
    setRFEpcMode(rf, state)
}

fun setRFPower(state: SnackbarHostState, rf: RFIDWithUHFUART, power: Int): Boolean {
    if (rf.power != power) {

        for (i in 0..11) {
            if (rf.setPower(power)) {
                return true
            }
        }
        CoroutineScope(Dispatchers.Default).launch {
            state.showSnackbar(
                "مشکلی در سخت افزار پیش آمده است",
                null,
                SnackbarDuration.Long
            )
        }
        return false
    } else {
        return true
    }
}
