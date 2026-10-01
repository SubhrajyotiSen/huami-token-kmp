package org.huamitoken.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.huamitoken.DeviceDisplay
import org.huamitoken.HttpEngine
import org.huamitoken.HuamiTokenError
import org.huamitoken.LoginMethod
import org.huamitoken.TokenRepository
import org.huamitoken.saveGpsFiles
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private const val DEFAULT_PROXY_PREFIX = "/api/proxy?url="

private var currentMethod = LoginMethod.AMAZFIT

fun main() {
    initTheme()
    initInfoToggle()
    initMethodSelector()
    initInputAffordances()
    initClearResults()
    initGpsDownload()

    val goBtn = document.getElementById("go") as HTMLButtonElement
    goBtn.addEventListener("click", {
        val username = (document.getElementById("username") as HTMLInputElement).value.trim()
        val password = (document.getElementById("password") as HTMLInputElement).value
        val rawProxy = (document.getElementById("proxy") as HTMLInputElement).value.trim()
        val effectiveProxy = when {
            rawProxy.equals("direct", ignoreCase = true) || rawProxy.equals("none", ignoreCase = true) -> ""
            rawProxy.isNotEmpty() -> rawProxy
            else -> DEFAULT_PROXY_PREFIX
        }
        runLookup(currentMethod, username, password, effectiveProxy)
    })
}

private fun initTheme() {
    val themeToggle = document.getElementById("theme-toggle") as? HTMLButtonElement ?: return
    val themeIcon = document.getElementById("theme-icon") as? HTMLElement

    val sunSvg = """<path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0s-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0s-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41s-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.41s-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z"/>"""
    val moonSvg = """<path d="M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z"/>"""

    val prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches
    val savedTheme = window.localStorage.getItem("huami_theme")
    val initialDark = if (savedTheme != null) savedTheme == "dark" else prefersDark

    fun applyTheme(dark: Boolean) {
        if (dark) {
            document.documentElement?.setAttribute("data-theme", "dark")
            themeIcon?.innerHTML = sunSvg
            themeToggle.setAttribute("aria-label", "Switch to Light Mode")
        } else {
            document.documentElement?.removeAttribute("data-theme")
            themeIcon?.innerHTML = moonSvg
            themeToggle.setAttribute("aria-label", "Switch to Dark Mode")
        }
    }

    applyTheme(initialDark)

    themeToggle.addEventListener("click", {
        val isDark = document.documentElement?.getAttribute("data-theme") == "dark"
        val nextDark = !isDark
        window.localStorage.setItem("huami_theme", if (nextDark) "dark" else "light")
        applyTheme(nextDark)
    })
}

private fun initInfoToggle() {
    val infoBtn = document.getElementById("info-toggle-btn") as? HTMLButtonElement ?: return
    val collapsible = document.getElementById("info-collapsible") as? HTMLElement ?: return

    infoBtn.addEventListener("click", {
        val isExpanded = collapsible.classList.contains("expanded")
        if (isExpanded) {
            collapsible.classList.remove("expanded")
            infoBtn.textContent = "Learn more"
        } else {
            collapsible.classList.add("expanded")
            infoBtn.textContent = "Show less"
        }
    })
}

private fun initMethodSelector() {
    val amazfitBtn = document.getElementById("method-amazfit") as? HTMLButtonElement ?: return
    val xiaomiBtn = document.getElementById("method-xiaomi") as? HTMLButtonElement ?: return
    val xiaomiNotice = document.getElementById("xiaomi-notice") as? HTMLElement
    val gpsBtn = document.getElementById("download-gps-btn") as? HTMLButtonElement

    amazfitBtn.addEventListener("click", {
        currentMethod = LoginMethod.AMAZFIT
        amazfitBtn.classList.add("active")
        amazfitBtn.setAttribute("aria-checked", "true")
        xiaomiBtn.classList.remove("active")
        xiaomiBtn.setAttribute("aria-checked", "false")
        xiaomiNotice?.style?.display = "none"
        gpsBtn?.style?.display = "flex"
    })

    xiaomiBtn.addEventListener("click", {
        currentMethod = LoginMethod.XIAOMI
        xiaomiBtn.classList.add("active")
        xiaomiBtn.setAttribute("aria-checked", "true")
        amazfitBtn.classList.remove("active")
        amazfitBtn.setAttribute("aria-checked", "false")
        xiaomiNotice?.style?.display = "flex"
        gpsBtn?.style?.display = "none"
    })
}

private fun initInputAffordances() {
    val usernameInput = document.getElementById("username") as? HTMLInputElement
    val clearUserBtn = document.getElementById("clear-username-btn") as? HTMLButtonElement
    val passwordInput = document.getElementById("password") as? HTMLInputElement
    val togglePwdBtn = document.getElementById("toggle-pwd-btn") as? HTMLButtonElement
    val eyeIcon = document.getElementById("eye-icon") as? HTMLElement

    usernameInput?.addEventListener("input", {
        if (usernameInput.value.isNotEmpty()) {
            clearUserBtn?.style?.display = "flex"
        } else {
            clearUserBtn?.style?.display = "none"
        }
    })

    clearUserBtn?.addEventListener("click", {
        if (usernameInput != null) {
            usernameInput.value = ""
            clearUserBtn.style.display = "none"
            usernameInput.focus()
        }
    })

    var pwdVisible = false
    val eyeOpenSvg = """<path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>"""
    val eyeClosedSvg = """<path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"/>"""

    togglePwdBtn?.addEventListener("click", {
        pwdVisible = !pwdVisible
        passwordInput?.type = if (pwdVisible) "text" else "password"
        eyeIcon?.innerHTML = if (pwdVisible) eyeClosedSvg else eyeOpenSvg
    })
}

private fun initClearResults() {
    val clearBtn = document.getElementById("clear-results-btn") as? HTMLButtonElement ?: return
    val resultsSection = document.getElementById("results") as? HTMLElement
    val devicesContainer = document.getElementById("devices") as? HTMLElement
    val countBadge = document.getElementById("device-count-badge") as? HTMLElement

    clearBtn.addEventListener("click", {
        resultsSection?.classList?.remove("show")
        if (devicesContainer != null) devicesContainer.innerHTML = ""
        if (countBadge != null) countBadge.textContent = "0"
    })
}

private fun showSnackbar(message: String) {
    val snackbar = document.getElementById("snackbar") as? HTMLElement ?: return
    val text = document.getElementById("snackbar-text") as? HTMLElement ?: return
    text.textContent = message
    snackbar.classList.add("show")
    scope.launch {
        delay(3000)
        snackbar.classList.remove("show")
    }
}

private fun copyToClipboard(text: String, label: String, button: HTMLButtonElement?) {
    try {
        window.navigator.clipboard.writeText(text)
        showSnackbar("Copied $label to clipboard")
        if (button != null) {
            button.classList.add("copied")
            button.innerHTML = """<svg viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg> Copied!"""
            scope.launch {
                delay(2000)
                button.classList.remove("copied")
                button.innerHTML = """<svg viewBox="0 0 24 24"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg> Copy Key"""
            }
        }
    } catch (_: Exception) {
        showSnackbar("Failed to copy to clipboard")
    }
}

private fun runLookup(method: LoginMethod, username: String, password: String, proxy: String) {
    val goBtn = document.getElementById("go") as HTMLButtonElement
    val goText = document.getElementById("go-text") as HTMLElement
    val gpsButton = document.getElementById("download-gps-btn") as? HTMLButtonElement
    val statusCard = document.getElementById("status-card") as HTMLElement
    val statusText = document.getElementById("status-text") as HTMLElement
    val errorCard = document.getElementById("error-card") as HTMLElement
    val errorText = document.getElementById("error-text") as HTMLElement
    val resultsSection = document.getElementById("results") as HTMLElement
    val devicesContainer = document.getElementById("devices") as HTMLElement
    val countBadge = document.getElementById("device-count-badge") as HTMLElement

    if (username.isBlank() || password.isEmpty()) {
        showError(errorCard, errorText, statusCard, resultsSection, "Please enter both account e-mail and password.")
        return
    }

    goBtn.disabled = true
    goBtn.classList.add("loading")
    goText.textContent = "Contacting servers…"
    gpsButton?.disabled = true
    statusCard.classList.remove("show")
    errorCard.classList.remove("show")

    scope.launch {
        try {
            val engine = HttpEngine().also { it.proxyPrefix = proxy }
            val list = TokenRepository(engine).fetchDevices(method, username, password)

            devicesContainer.innerHTML = ""
            countBadge.textContent = "${list.size}"

            if (list.isEmpty()) {
                statusText.textContent = "Authentication succeeded, but no bound devices were found on this account."
                statusCard.classList.add("show")
                resultsSection.classList.remove("show")
            } else {
                renderDevices(list, devicesContainer)
                statusText.textContent = "Found ${list.size} device(s). Copy the Bluetooth Auth Key into Gadgetbridge."
                statusCard.classList.add("show")
                resultsSection.classList.add("show")
            }
        } catch (e: HuamiTokenError) {
            val hint = if (e.code == "proxy" || e.message?.contains("403") == true || e.message?.contains("429") == true) {
                " If the upstream server is blocking cloud IPs or asking for a captcha/2FA, use the CLI, Android, Desktop, or iOS app."
            } else ""
            showError(errorCard, errorText, statusCard, resultsSection, "Lookup failed: ${e.message}$hint")
        } catch (e: Exception) {
            val hint = if (e.message?.contains("fetch", ignoreCase = true) == true ||
                e.message?.contains("CORS", ignoreCase = true) == true ||
                e.message?.contains("Network", ignoreCase = true) == true
            ) {
                " The browser blocked the request (CORS). Set a CORS-proxy prefix under Advanced, or use the CLI, Android, Desktop, or iOS app."
            } else ""
            showError(errorCard, errorText, statusCard, resultsSection, "Lookup failed: ${e.message}$hint")
        } finally {
            goBtn.disabled = false
            goBtn.classList.remove("loading")
            goText.textContent = "Get Bluetooth Keys"
            gpsButton?.disabled = false
        }
    }
}

private fun initGpsDownload() {
    val button = document.getElementById("download-gps-btn") as? HTMLButtonElement ?: return
    val gpsText = document.getElementById("gps-text") as? HTMLElement
    val goBtn = document.getElementById("go") as? HTMLButtonElement
    val statusCard = document.getElementById("status-card") as HTMLElement
    val statusText = document.getElementById("status-text") as HTMLElement
    val errorCard = document.getElementById("error-card") as HTMLElement
    val errorText = document.getElementById("error-text") as HTMLElement
    val resultsSection = document.getElementById("results") as HTMLElement

    button.addEventListener("click", {
        val username = (document.getElementById("username") as HTMLInputElement).value.trim()
        val password = (document.getElementById("password") as HTMLInputElement).value
        val rawProxy = (document.getElementById("proxy") as HTMLInputElement).value.trim()
        val proxy = when {
            rawProxy.equals("direct", ignoreCase = true) || rawProxy.equals("none", ignoreCase = true) -> ""
            rawProxy.isNotEmpty() -> rawProxy
            else -> DEFAULT_PROXY_PREFIX
        }
        if (username.isBlank() || password.isEmpty()) {
            showError(errorCard, errorText, statusCard, resultsSection, "Please enter both account e-mail and password.")
            return@addEventListener
        }
        button.disabled = true
        button.classList.add("loading")
        gpsText?.textContent = "Downloading GPS files…"
        goBtn?.disabled = true
        statusCard.classList.remove("show")
        errorCard.classList.remove("show")

        scope.launch {
            try {
                val engine = HttpEngine().also { it.proxyPrefix = proxy }
                val files = TokenRepository(engine).fetchGpsFiles(username, password)
                val path = saveGpsFiles(files)
                val namesList = files.keys.sorted().joinToString(", ")
                statusText.textContent = "Downloaded ${files.size} GPS file(s): $namesList\n$path"
                statusCard.classList.add("show")
                showSnackbar("Downloaded ${files.size} GPS file(s)")
            } catch (e: HuamiTokenError) {
                val hint = if (e.code == "proxy" || e.message?.contains("403") == true || e.message?.contains("429") == true) {
                    " If the upstream server is blocking cloud IPs or asking for a captcha/2FA, use the CLI, Android, Desktop, or iOS app."
                } else ""
                showError(errorCard, errorText, statusCard, resultsSection, "GPS download failed: ${e.message}$hint")
            } catch (e: Exception) {
                val hint = if (e.message?.contains("fetch", ignoreCase = true) == true ||
                    e.message?.contains("CORS", ignoreCase = true) == true ||
                    e.message?.contains("Network", ignoreCase = true) == true
                ) {
                    " The browser blocked the request (CORS). Set a CORS-proxy prefix under Advanced, or use the CLI, Android, Desktop, or iOS app."
                } else ""
                showError(errorCard, errorText, statusCard, resultsSection, "GPS download failed: ${e.message ?: "Unknown error"}$hint")
            } finally {
                button.disabled = false
                button.classList.remove("loading")
                gpsText?.textContent = "Download GPS Files"
                goBtn?.disabled = false
            }
        }
    })
}

private fun renderDevices(list: List<DeviceDisplay>, container: HTMLElement) {
    for (d in list) {
        val card = document.createElement("div") as HTMLElement
        card.className = "device-card"

        val activeHtml = if (d.active != null) {
            """<span class="device-active-badge">Active status: ${d.active}</span>"""
        } else ""

        card.innerHTML = """
          <div class="device-card-header">
            <div class="device-icon-badge">
              <svg viewBox="0 0 24 24"><path d="M20 12c0-2.54-1.19-4.81-3.04-6.27L16 2H8l-.95 3.73C5.19 7.18 4 9.45 4 12s1.19 4.81 3.05 6.27L8 22h8l.96-3.73C18.81 16.81 20 14.54 20 12zM6 12c0-3.31 2.69-6 6-6s6 2.69 6 6-2.69 6-6 6-6-2.69-6-6z"/></svg>
            </div>
            <div class="device-title-col">
              <span class="device-title">${d.title}</span>
              $activeHtml
            </div>
          </div>
          <div class="device-row-surface">
            <div style="display: flex; align-items: center; gap: 8px;">
              <span class="row-label">MAC:</span>
              <span class="row-value">${d.mac}</span>
            </div>
            <button type="button" class="btn-tonal copy-mac-btn" aria-label="Copy MAC Address">
              <svg viewBox="0 0 24 24"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
              <span>Copy MAC</span>
            </button>
          </div>
          <div class="device-row-surface key-box">
            <div class="key-box-header">
              <span class="row-label primary">Bluetooth Auth Key</span>
              <button type="button" class="btn-tonal copy-key-btn" aria-label="Copy Bluetooth Auth Key">
                <svg viewBox="0 0 24 24"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
                <span>Copy Key</span>
              </button>
            </div>
            <span class="row-value key-val">${d.key}</span>
          </div>
        """.trimIndent()

        val copyMacBtn = card.querySelector(".copy-mac-btn") as? HTMLButtonElement
        copyMacBtn?.addEventListener("click", {
            copyToClipboard(d.mac, "MAC address", null)
        })

        val copyKeyBtn = card.querySelector(".copy-key-btn") as? HTMLButtonElement
        copyKeyBtn?.addEventListener("click", {
            copyToClipboard(d.key, "Bluetooth Auth Key", copyKeyBtn)
        })

        container.appendChild(card)
    }
}

private fun showError(
    errorCard: HTMLElement,
    errorText: HTMLElement,
    statusCard: HTMLElement,
    resultsSection: HTMLElement,
    message: String,
) {
    statusCard.classList.remove("show")
    resultsSection.classList.remove("show")
    errorText.textContent = message
    errorCard.classList.add("show")
}
