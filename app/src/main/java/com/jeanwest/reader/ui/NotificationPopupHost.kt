package com.jeanwest.reader.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class NotificationPopupHost {

    var showPopup by mutableStateOf(false)
    var message by mutableStateOf("")
    val onClick : () -> Unit = {
        showPopup = false
    }
    val onDismiss : () -> Unit = {
        showPopup = false
    }
    fun showPopup(message: String) {
        this.message = message
        showPopup = true
    }
}

@Composable
fun NotificationPopUp(state : NotificationPopupHost) {

    if (state.showPopup) {
        AlertDialog(
            onDismissRequest = { state.onDismiss() },
            text = { Text(text = state.message) },
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            buttons = {
                BigButton(text = "باشه") {
                    state.onClick()
                }
            })
    }
}