package org.huamitoken.android

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.huamitoken.LoginMethod
import org.huamitoken.ui.DeviceCard
import org.huamitoken.ui.HuamiTheme
import org.huamitoken.ui.SampleDevices
import org.huamitoken.ui.TokenScreenContent

@Preview(name = "Android Phone - Light", showBackground = true, showSystemUi = true)
@Composable
fun AndroidTokenScreenLightPreview() {
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

@Preview(
    name = "Android Phone - Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun AndroidTokenScreenDarkPreview() {
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

@Preview(name = "Android Phone - Results", showBackground = true, showSystemUi = true)
@Composable
fun AndroidTokenScreenResultsPreview() {
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

@Preview(
    name = "Android Phone - Results Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun AndroidTokenScreenResultsDarkPreview() {
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

@Preview(name = "Android Phone - Xiaomi", showBackground = true, showSystemUi = true)
@Composable
fun AndroidTokenScreenXiaomiPreview() {
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

@Preview(name = "Device Card - Active", showBackground = true)
@Composable
fun AndroidDeviceCardActivePreview() {
    HuamiTheme(darkTheme = false) {
        Surface {
            DeviceCard(device = SampleDevices[0])
        }
    }
}

@Preview(
    name = "Device Card - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun AndroidDeviceCardDarkPreview() {
    HuamiTheme(darkTheme = true) {
        Surface {
            DeviceCard(device = SampleDevices[1])
        }
    }
}
