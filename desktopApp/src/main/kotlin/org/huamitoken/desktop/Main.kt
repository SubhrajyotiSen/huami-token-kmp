package org.huamitoken.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.huamitoken.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "huami-token",
        state = rememberWindowState(width = 600.dp, height = 750.dp),
    ) {
        App()
    }
}
