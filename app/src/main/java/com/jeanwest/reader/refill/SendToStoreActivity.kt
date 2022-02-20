package com.jeanwest.reader.refill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jeanwest.reader.R
import com.jeanwest.reader.theme.MyApplicationTheme


@ExperimentalCoilApi
class SendToStoreActivity : ComponentActivity() {

    private var uiList = mutableStateListOf<RefillProduct>()

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Page()
        }

        val type = object : TypeToken<List<RefillProduct>>() {}.type

        uiList = Gson().fromJson(
            intent.getStringExtra("RefillProducts"), type
        ) ?: mutableStateListOf()
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Page() {
        MyApplicationTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = { AppBar() },
                    content = { Content() },
                )
            }
        }
    }

    @Composable
    fun AppBar() {

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
                    text = stringResource(id = R.string.refill),
                    modifier = Modifier
                        .padding(end = 40.dp)
                        .fillMaxSize()
                        .wrapContentSize(),
                    textAlign = TextAlign.Center,
                )
            }
        )
    }

    @ExperimentalCoilApi
    @ExperimentalFoundationApi
    @Composable
    fun Content() {

        val modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .wrapContentWidth()

        Column {

            LazyColumn(modifier = Modifier.padding(top = 5.dp)) {

                items(uiList.size) { i ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .background(
                                color = MaterialTheme.colors.onPrimary,
                                shape = MaterialTheme.shapes.small,
                            )
                            .fillMaxWidth(),
                    ) {
                        Column {
                            Text(
                                text = uiList[i].name,
                                style = MaterialTheme.typography.h1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                            )

                            Text(
                                text = uiList[i].KBarCode,
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                            )

                            Text(
                                text = "تعداد اسکن شده: " + uiList[i].scannedNumber.toString(),
                                style = MaterialTheme.typography.body1,
                                textAlign = TextAlign.Right,
                                modifier = modifier,
                            )
                        }

                        Image(
                            painter = rememberImagePainter(
                                uiList[i].imageUrl,
                            ),
                            contentDescription = "",
                            modifier = Modifier
                                .height(100.dp)
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }

                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {

                        Text(
                            text = "مجموع ارسالی: ${uiList.size}",
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .padding(top = 5.dp, bottom = 5.dp, start = 5.dp)
                                .align(Alignment.CenterVertically)
                                .weight(1F)
                        )

                        Button(
                            onClick = {
                                //
                            }, modifier = Modifier
                                .padding(top = 5.dp, bottom = 5.dp, start = 5.dp, end = 5.dp)
                                .weight(1F)
                        ) {
                            Text(text = "ارسال به فروشگاه")
                        }
                    }
                }
            }
        }
    }
}