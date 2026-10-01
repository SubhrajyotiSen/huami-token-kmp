package org.huamitoken.desktop

import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.huamitoken.LoginMethod
import org.huamitoken.ui.DeviceCard
import org.huamitoken.ui.HuamiTheme
import org.huamitoken.ui.SampleDevices
import org.huamitoken.ui.TokenScreenContent

@Preview
@Composable
fun DesktopTokenScreenLightPreview() {
    HuamiTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            TokenScreenContent(
                darkTheme = false,
                username = "user@example.com",
                password = "password123",
            )
        }
    }
}

@Preview
@Composable
fun DesktopTokenScreenDarkPreview() {
    HuamiTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            TokenScreenContent(
                darkTheme = true,
                username = "user@example.com",
                password = "password123",
            )
        }
    }
}

@Preview
@Composable
fun DesktopTokenScreenResultsPreview() {
    HuamiTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            TokenScreenContent(
                darkTheme = false,
                username = "user@example.com",
                password = "password123",
                devices = SampleDevices,
            )
        }
    }
}

@Preview
@Composable
fun DesktopTokenScreenResultsDarkPreview() {
    HuamiTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            TokenScreenContent(
                darkTheme = true,
                username = "user@example.com",
                password = "password123",
                devices = SampleDevices,
            )
        }
    }
}

@Preview
@Composable
fun DesktopTokenScreenXiaomiPreview() {
    HuamiTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            TokenScreenContent(
                darkTheme = false,
                method = LoginMethod.XIAOMI,
                username = "123456789",
                password = "password123",
            )
        }
    }
}

@Preview
@Composable
fun DesktopDeviceCardActivePreview() {
    HuamiTheme(darkTheme = false) {
        Surface {
            DeviceCard(device = SampleDevices[0])
        }
    }
}

@Preview
@Composable
fun DesktopDeviceCardDarkPreview() {
    HuamiTheme(darkTheme = true) {
        Surface {
            DeviceCard(device = SampleDevices[1])
        }
    }
}
