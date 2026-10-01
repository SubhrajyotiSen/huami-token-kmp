package org.huamitoken

import androidx.compose.ui.window.ComposeUIViewController
import org.huamitoken.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    App()
}
