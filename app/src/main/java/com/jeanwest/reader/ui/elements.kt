package com.jeanwest.reader.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.jeanwest.reader.R
import com.jeanwest.reader.data.Product

@Composable
fun ErrorSnackBar(state: SnackbarHostState) {

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom
    ) {

        SnackbarHost(hostState = state, snackbar = {
            Snackbar(
                shape = MaterialTheme.shapes.large,
                action = {
                    Text(
                        text = "باشه",
                        color = MaterialTheme.colors.secondary,
                        style = MaterialTheme.typography.h2,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable {
                                state.currentSnackbarData?.dismiss()
                            }
                    )
                }
            ) {
                Text(
                    text = state.currentSnackbarData?.message ?: "",
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.h2,
                )
            }
        })
    }
}

@Composable
fun OpenActivityButton(text: String, iconId: Int, onClick: () -> Unit) {

    val buttonSize = 100.dp
    val iconSize = 48.dp

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
fun BigButton(
    text: String, enable: Boolean = true, onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp)
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Jeanswest,
            disabledBackgroundColor = DisableButtonColor,
            disabledContentColor = Color.White
        ),
        onClick = onClick
    ) {
        Text(
            text = text,
            style = Typography.h2
        )
    }
}

@Composable
fun BottomBarButton(text: String, enable: Boolean = true, onClick: () -> Unit) {

    Box(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(0.dp))
                .background(Color.White, RoundedCornerShape(0.dp))
                .height(100.dp)
                .align(Alignment.BottomCenter),
        ) {

            Button(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp)
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .align(Alignment.Center),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Jeanswest,
                    disabledBackgroundColor = DisableButtonColor,
                    disabledContentColor = Color.White
                ),
                onClick = onClick
            ) {
                Text(
                    text = text,
                    style = Typography.body1,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Item(
    i: Int,
    uiList: MutableList<Product> = mutableListOf(),
    clickable: Boolean = false,
    text3: String,
    text4: String,
    colorFull: Boolean = false,
    enableWarehouseNumberCheck: Boolean = false,
    text1: String = "",
    text2: String = "",
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
) {

    val topPadding = if (i == 0) 16.dp else 12.dp
    val bottomPadding = if (i == uiList.size - 1) 128.dp else 0.dp

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding, top = topPadding)
            .shadow(elevation = 5.dp, shape = MaterialTheme.shapes.small)
            .background(
                color = if (uiList[i].scannedNumber > uiList[i].wareHouseNumber && enableWarehouseNumberCheck) {
                    errorColor
                } else if (colorFull) {
                    JeanswestSelected
                } else {
                    MaterialTheme.colors.onPrimary
                },
                shape = MaterialTheme.shapes.small
            )
            .fillMaxWidth()
            .height(100.dp)
            .testTag("items")
            .combinedClickable(
                enabled = clickable,
                onLongClick = { onLongClick() },
                onClick = { onClick() })
    ) {

        Box {

            Image(
                painter = rememberImagePainter(
                    uiList[i].imageUrl,
                ),
                contentDescription = "",
                modifier = Modifier
                    .padding(end = 4.dp, top = 12.dp, bottom = 12.dp, start = 12.dp)
                    .shadow(0.dp, shape = Shapes.large)
                    .background(
                        color = MaterialTheme.colors.onPrimary,
                        shape = Shapes.large
                    )
                    .border(
                        BorderStroke(2.dp, color = BorderLight),
                        shape = Shapes.large
                    )
                    .fillMaxHeight()
                    .width(70.dp)
            )

            if (uiList[i].requestedNumber > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, start = 6.dp)
                        .background(
                            shape = RoundedCornerShape(24.dp),
                            color = warningColor
                        )
                        .size(24.dp)
                ) {
                    Text(
                        text = uiList[i].requestedNumber.toString(),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .weight(1.2F)
                    .fillMaxHeight()
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                Text(
                    text = if (text1 == "") uiList[i].KBarCode else text1,
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Right,
                    fontSize = 12.sp
                )
                Text(
                    text = if (text2 == "") uiList[i].name else text2,
                    style = MaterialTheme.typography.h4,
                    textAlign = TextAlign.Right,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1F)
                    .fillMaxHeight()
                    .padding(top = 16.dp, bottom = 16.dp)
                    .wrapContentWidth()
                    .background(
                        color = innerBackground,
                        shape = Shapes.large
                    ),

                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = text3,
                    style = MaterialTheme.typography.h3,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                Divider(
                    color = Jeanswest,
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .width(66.dp)
                )
                Text(
                    text = text4,
                    style = MaterialTheme.typography.h3,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Item2(
    clickable: Boolean = false,
    enableBottomSpace: Boolean = false,
    text1: String,
    text2: String,
    text3: String,
    text4: String,
    text5: String,
    text6: String,
    onClick: () -> Unit = {},
) {

    val bottomPadding = if (enableBottomSpace) 128.dp else 0.dp

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding, top = 12.dp)
            .shadow(elevation = 3.dp, shape = MaterialTheme.shapes.small)
            .background(
                color = MaterialTheme.colors.onPrimary,
                shape = MaterialTheme.shapes.small
            )
            .fillMaxWidth()
            .height(80.dp)
            .testTag("items")
            .combinedClickable(
                enabled = clickable,
                onClick = { onClick() })
    ) {

        Row(
            modifier = Modifier
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .weight(2F)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                Text(
                    text = text1,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                )

                Text(
                    text = text3,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                )

                Text(
                    text = text5,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1F)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .wrapContentWidth(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                Text(
                    text = text2,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .weight(1F)
                        .fillMaxWidth(),
                )

                Text(
                    text = text4,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .weight(1F),
                )

                Text(
                    text = text6,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .weight(1F),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Item3(
    clickable: Boolean = false,
    enableBottomSpace: Boolean = false,
    text1: String,
    text2: String,
    text3: String,
    text4: String,
    onClick: () -> Unit = {},
) {

    val bottomPadding = if (enableBottomSpace) 128.dp else 0.dp

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding, top = 12.dp)
            .shadow(elevation = 3.dp, shape = MaterialTheme.shapes.small)
            .background(
                color = MaterialTheme.colors.onPrimary,
                shape = MaterialTheme.shapes.small
            )
            .fillMaxWidth()
            .height(80.dp)
            .testTag("items")
            .combinedClickable(
                enabled = clickable,
                onClick = { onClick() })
    ) {

        Row(
            modifier = Modifier
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.5F)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                Text(
                    text = text1,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                )

                Text(
                    text = text2,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1F)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .wrapContentWidth(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                Text(
                    text = text3,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .weight(1F)
                        .fillMaxWidth(),
                )

                Text(
                    text = text4,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .weight(1F),
                )
            }
        }
    }
}

@Composable
fun FilterDropDownList(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    values: MutableList<String>,
    onClick: (item: String) -> Unit
) {

    var expanded by rememberSaveable {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
            .shadow(elevation = 1.dp, shape = MaterialTheme.shapes.small)
            .background(
                color = MaterialTheme.colors.onPrimary,
                shape = MaterialTheme.shapes.small
            )
            .border(
                BorderStroke(1.dp, if (expanded) Jeanswest else borderColor),
                shape = MaterialTheme.shapes.small
            )
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .testTag("FilterDropDownList")
                .fillMaxHeight(),
        ) {

            icon()
            text()
            Icon(
                painter = painterResource(
                    id = if (expanded) {
                        R.drawable.ic_baseline_arrow_drop_up_24
                    } else {
                        R.drawable.ic_baseline_arrow_drop_down_24
                    }
                ),
                "",
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 0.dp, end = 4.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .wrapContentWidth()
                .background(color = BottomBar, shape = Shapes.small)
        ) {
            values.forEach {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onClick(it)
                }) {
                    Text(text = it)
                }
            }
        }
    }
}

@Composable
fun CustomTextField(
    modifier: Modifier,
    onSearch: () -> Unit,
    hint: String,
    onValueChange: (it: String) -> Unit,
    value: String,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {

    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        textStyle = MaterialTheme.typography.body2,

        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = ""
            )
        },
        value = value,
        onValueChange = {
            onValueChange(it)
        },
        modifier = modifier
            .testTag("CustomTextField")
            .background(
                color = MaterialTheme.colors.secondary,
                shape = MaterialTheme.shapes.small
            ),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            onSearch()
        }),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            keyboardType = keyboardType
        ),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            unfocusedBorderColor = MaterialTheme.colors.secondary
        ),
        placeholder = { Text(text = hint) },
        isError = isError,
        singleLine = true,

        )
}

@Composable
fun SimpleTextField(
    modifier: Modifier,
    hint: String,
    onValueChange: (it: String) -> Unit,
    value: String,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {

    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        textStyle = MaterialTheme.typography.body2,
        value = value,
        onValueChange = {
            onValueChange(it)
        },
        modifier = modifier
            .testTag("TextField")
            .fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            keyboardType = keyboardType
        ),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
        }),
        label = { Text(text = hint) },
        isError = isError,
        singleLine = true,

        )
}

@Composable
fun LoadingCircularProgressIndicator(isScanning: Boolean = false, isDataLoading: Boolean = false) {

    if (isScanning || isDataLoading) {
        Row(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(), horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colors.primary)

            if (isScanning) {
                Text(
                    text = "در حال اسکن",
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .align(Alignment.CenterVertically)
                )
            } else if (isDataLoading) {
                Text(
                    text = "در حال بارگذاری",
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
fun PowerSlider(enable: Boolean, rfPower: Int, onClick: (it: Int) -> Unit) {

    var slideValue by rememberSaveable { mutableStateOf(rfPower.toFloat()) }

    if (enable) {
        Row {

            Text(
                text = "قدرت آنتن (" + slideValue.toInt() + ")  ",
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically),
                textAlign = TextAlign.Center
            )

            Slider(
                value = slideValue,
                onValueChange = {
                    slideValue = it
                    onClick(it.toInt())
                },
                enabled = true,
                valueRange = 5f..30f,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}

@Composable
fun ScanTypeDropDownList(
    modifier: Modifier,
    scanTypeValue: String,
    onClick: (it: String) -> Unit
) {

    var expanded by rememberSaveable {
        mutableStateOf(false)
    }

    val scanTypeValues = mutableListOf("RFID", "بارکد")

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = true }
                .testTag("scanTypeDropDownList")) {
            Text(text = scanTypeValue)
            Icon(
                painter = if (!expanded) {
                    painterResource(id = R.drawable.ic_baseline_arrow_drop_down_24)
                } else {
                    painterResource(id = R.drawable.ic_baseline_arrow_drop_up_24)
                }, ""
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.wrapContentWidth()
        ) {

            scanTypeValues.forEach {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onClick(it)
                }) {
                    Text(text = it)
                }
            }
        }
    }
}
