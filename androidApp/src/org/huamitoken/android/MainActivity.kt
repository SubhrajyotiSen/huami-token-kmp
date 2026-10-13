package org.huamitoken.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.huamitoken.AndroidAppContext
import org.huamitoken.LoginMethod
import org.huamitoken.ui.App
import org.huamitoken.ui.DeviceCard
import org.huamitoken.ui.HuamiTheme
import org.huamitoken.ui.SampleDevices
import org.huamitoken.ui.TokenScreenContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.context = applicationContext
        setContent {
            App()
        }
    }
}

@Preview(name = "Light Theme", showBackground = true)
@Composable
fun MainActivityPreviewLight() {
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

@Preview(name = "Dark Theme", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MainActivityPreviewDark() {
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

@Preview(name = "Results View", showBackground = true)
@Composable
fun MainActivityPreviewResults() {
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
