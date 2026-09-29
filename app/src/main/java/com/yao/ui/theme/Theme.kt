package com.yao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LauncherColors = darkColorScheme()

@Composable
fun YaoRelojTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LauncherColors,
        content = content
    )
}
