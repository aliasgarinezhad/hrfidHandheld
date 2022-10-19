package com.jeanwest.reader.shared.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    /*primary = Jeanswest,
    primaryVariant = JeanswestStatusBar,
    background = JeanswestBackground,
    surface = JeanswestBackground,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    secondaryVariant = Jeanswest,*/
)

private val LightColorPalette = lightColors(

    primary = Jeanswest,
    primaryVariant = JeanswestStatusBar,
    background = JeanswestBackground,
    surface = JeanswestBackground,
    onPrimary = Color.White,
    secondaryVariant = Jeanswest,
    onBackground = Color.Black,
    onSurface = Color.Black,
    secondary = BorderLight,
    error = errorColor,
)

@Composable
fun MyApplicationTheme(
    //darkTheme: Boolean = isSystemInDarkTheme(),
    darkTheme: Boolean = false,
    content: @Composable() () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}