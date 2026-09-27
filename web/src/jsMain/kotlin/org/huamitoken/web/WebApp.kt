package org.huamitoken.web

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.huamitoken.HuamiTokenError
import org.huamitoken.HttpEngine
import org.huamitoken.LoginMethod
import org.huamitoken.TokenRepository
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

fun main() {
    val go = document.getElementById("go") as HTMLButtonElement
    go.addEventListener("click", {
        val method = document.querySelector("input[name=method]:checked") as? HTMLInputElement
        val username = (document.getElementById("username") as HTMLInputElement).value.trim()
        val password = (document.getElementById("password") as HTMLInputElement).value
        val proxy = (document.getElementById("proxy") as HTMLInputElement).value.trim()
        runLookup(
            if (method?.value == "xiaomi") LoginMethod.XIAOMI else LoginMethod.AMAZFIT,
            username, password, proxy,
        )
    })
}

private fun runLookup(method: LoginMethod, username: String, password: String, proxy: String) {
    val go = document.getElementById("go") as HTMLButtonElement
    val status = document.getElementById("status") as HTMLElement
    val results = document.getElementById("results") as HTMLElement
    val devices = document.getElementById("devices") as HTMLElement
    val error = document.getElementById("error") as HTMLElement

    if (username.isBlank() || password.isEmpty()) {
        showError(error, results, "Please fill in both e-mail and password.")
        return
    }
    go.disabled = true
    status.textContent = "Contacting servers…"
    status.className = "status busy"
    error.className = "err"
    results.className = "results"

    scope.launch {
        try {
            val engine = HttpEngine().also { it.proxyPrefix = proxy }
            val list = TokenRepository(engine).fetchDevices(method, username, password)
            devices.innerHTML = ""
            if (list.isEmpty()) {
                status.textContent = "Login worked, but no bound devices were found."
            } else {
                for (d in list) {
                    val card = document.createElement("div")
                    card.className = "dev"
                    card.innerHTML =
                        "<h3></h3><dl><dt>MAC</dt><dd></dd>" +
                        (if (d.active != null) "<dt>Active</dt><dd></dd>" else "") +
                        "<dt>Key</dt><dd></dd></dl>"
                    val h3 = card.querySelector("h3") as HTMLElement
                    h3.textContent = d.title
                    val cells = card.querySelectorAll("dd")
                    var k = 0
                    (cells.item(k++) as HTMLElement).textContent = d.mac
                    if (d.active != null) (cells.item(k++) as HTMLElement).textContent = d.active
                    (cells.item(k) as HTMLElement).textContent = d.key
                    devices.appendChild(card)
                }
                status.textContent = "Found ${list.size} device(s). Copy the Key into Gadgetbridge."
                results.className = "results show"
            }
            status.className = "status"
        } catch (e: HuamiTokenError) {
            status.textContent = ""
            status.className = "status"
            showError(error, results, "Lookup failed: ${e.message}")
        } catch (e: Exception) {
            status.textContent = ""
            status.className = "status"
            val hint = if (e.message?.contains("fetch", ignoreCase = true) == true ||
                e.message?.contains("CORS", ignoreCase = true) == true ||
                e.message?.contains("Network", ignoreCase = true) == true
            ) {
                " The browser blocked the request (CORS). Set a CORS-proxy prefix under Advanced, or use the CLI/Android/iOS app."
            } else ""
            showError(error, results, "Lookup failed: ${e.message}$hint")
        } finally {
            go.disabled = false
        }
    }
}

private fun showError(error: HTMLElement, results: HTMLElement, message: String) {
    results.className = "results"
    error.textContent = message
    error.className = "err show"
}
