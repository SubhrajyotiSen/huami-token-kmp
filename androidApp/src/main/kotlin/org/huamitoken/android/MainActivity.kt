package org.huamitoken.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.huamitoken.DeviceDisplay
import org.huamitoken.HuamiTokenError
import org.huamitoken.HttpEngine
import org.huamitoken.LoginMethod
import org.huamitoken.TokenRepository

private val Accent = androidx.compose.ui.graphics.Color(0xFFC2410C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TokenScreen()
                }
            }
        }
    }
}

@Composable
fun TokenScreen() {
    var method by remember { mutableStateOf(LoginMethod.AMAZFIT) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<DeviceDisplay>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("huami-token", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Why this app?", fontWeight = FontWeight.SemiBold)
                Text(
                    "Newer Amazfit, Zepp and Xiaomi watches need their unique Bluetooth " +
                        "pairing key to work with Gadgetbridge. The key is stored on " +
                        "Huami/Xiaomi servers next to your paired devices — this app logs " +
                        "in and shows it so you can paste it into Gadgetbridge.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = method == LoginMethod.AMAZFIT,
                onClick = { method = LoginMethod.AMAZFIT },
                label = { Text("Amazfit / Zepp") },
            )
            FilterChip(
                selected = method == LoginMethod.XIAOMI,
                onClick = { method = LoginMethod.XIAOMI },
                label = { Text("Xiaomi") },
            )
        }

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Account e-mail (username)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = {
                if (username.isBlank() || password.isEmpty()) {
                    error = "Please fill in both e-mail and password."
                    return@Button
                }
                busy = true
                error = null
                devices = null
                scope.launch {
                    try {
                        devices = TokenRepository(HttpEngine()).fetchDevices(method, username.trim(), password)
                        if (devices!!.isEmpty()) error = "Login worked, but no bound devices were found."
                    } catch (e: HuamiTokenError) {
                        error = "Lookup failed: ${e.message}"
                    } catch (e: Exception) {
                        error = "Lookup failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Get Bluetooth keys")
        }

        if (method == LoginMethod.AMAZFIT) {
            OutlinedButton(
                onClick = {
                    if (username.isBlank() || password.isEmpty()) {
                        error = "Please fill in both e-mail and password."
                        return@OutlinedButton
                    }
                    busy = true
                    error = null
                    statusMessage = null
                    scope.launch {
                        try {
                            val files = TokenRepository(HttpEngine()).fetchGpsFiles(username.trim(), password)
                            val outDir = context.getExternalFilesDir(null) ?: context.filesDir
                            for ((name, bytes) in files) {
                                java.io.File(outDir, name).writeBytes(bytes)
                            }
                            val namesList = files.keys.joinToString(", ")
                            statusMessage = "Downloaded ${files.size} GPS file(s) to:\n${outDir.absolutePath}\n\nFiles: $namesList"
                        } catch (e: HuamiTokenError) {
                            error = "GPS download failed: ${e.message}"
                        } catch (e: Exception) {
                            error = "GPS download failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Download GPS files")
            }
        }

        statusMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    it,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        devices?.let { list ->
            Text("Paired devices (${list.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { d ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.title, fontWeight = FontWeight.SemiBold)
                            Text("MAC: ${d.mac}", fontFamily = FontFamily.Monospace)
                            d.active?.let { Text("Active: $it") }
                            Text("Key: ${d.key}", fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
        }
    }
}
