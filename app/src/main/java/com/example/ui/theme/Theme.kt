package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BigstaPrimaryLight,
    onPrimary = BigstaOnPrimaryLight,
    primaryContainer = BigstaPrimaryContainerLight,
    onPrimaryContainer = BigstaOnPrimaryContainerLight,
    secondary = BigstaSecondaryLight,
    onSecondary = BigstaOnSecondaryLight,
    secondaryContainer = BigstaSecondaryContainerLight,
    onSecondaryContainer = BigstaOnSecondaryContainerLight,
    background = BigstaBackgroundLight,
    onBackground = BigstaTextPrimaryLight,
    surface = BigstaSurfaceLight,
    onSurface = BigstaTextPrimaryLight,
    surfaceVariant = BigstaSurfaceVariantLight,
    onSurfaceVariant = BigstaTextSecondaryLight,
    outline = BigstaOutlineLight,
    error = BigstaErrorRed,
    onError = BigstaOnPrimaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = BigstaPrimaryDark,
    onPrimary = BigstaOnPrimaryDark,
    primaryContainer = BigstaPrimaryContainerDark,
    onPrimaryContainer = BigstaOnPrimaryContainerDark,
    secondary = BigstaSecondaryDark,
    onSecondary = BigstaOnSecondaryDark,
    background = BigstaBackgroundDark,
    onBackground = BigstaTextPrimaryDark,
    surface = BigstaSurfaceDark,
    onSurface = BigstaTextPrimaryDark,
    surfaceVariant = BigstaSurfaceVariantDark,
    onSurfaceVariant = BigstaTextSecondaryDark,
    outline = BigstaOutlineDark,
    error = BigstaErrorRed,
    onError = BigstaOnPrimaryLight
)

@Composable
fun BigstaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = BigstaTypography,
        content = content
    )
}
