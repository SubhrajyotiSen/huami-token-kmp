package org.huamitoken.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.huamitoken.DeviceDisplay
import org.huamitoken.LoginMethod

@Preview
@Composable
fun TokenScreenPreviewLight() {
    HuamiTheme(darkTheme = false) {
        Surface {
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
fun TokenScreenPreviewDark() {
    HuamiTheme(darkTheme = true) {
        Surface {
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
fun TokenScreenPreviewXiaomi() {
    HuamiTheme(darkTheme = false) {
        Surface {
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
fun TokenScreenPreviewBusy() {
    HuamiTheme(darkTheme = false) {
        Surface {
            TokenScreenContent(
                darkTheme = false,
                username = "user@example.com",
                password = "password123",
                busy = true,
            )
        }
    }
}

@Preview
@Composable
fun TokenScreenPreviewResultsLight() {
    HuamiTheme(darkTheme = false) {
        Surface {
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
fun TokenScreenPreviewResultsDark() {
    HuamiTheme(darkTheme = true) {
        Surface {
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
fun TokenScreenPreviewError() {
    HuamiTheme(darkTheme = false) {
        Surface {
            TokenScreenContent(
                darkTheme = false,
                username = "user@example.com",
                password = "wrong_password",
                error = "Lookup failed: Invalid credentials or account not found.",
            )
        }
    }
}

@Preview
@Composable
fun DeviceCardActivePreview() {
    HuamiTheme(darkTheme = false) {
        Surface {
            DeviceCard(
                device = SampleDevices[0],
            )
        }
    }
}

@Preview
@Composable
fun DeviceCardInactivePreview() {
    HuamiTheme(darkTheme = true) {
        Surface {
            DeviceCard(
                device = SampleDevices[1],
            )
        }
    }
}
