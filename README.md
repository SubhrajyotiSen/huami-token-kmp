# huami-token — Kotlin Multiplatform port

Kotlin Multiplatform port of [huami-token](https://codeberg.org/argrento/huami-token/). It logs in to Zepp (Amazfit)
or Xiaomi Mi Fitness servers and retrieves the Bluetooth pairing keys of bound watches/bands, for use with Gadgetbridge.

## Modules

| Module | Target | Notes |
|---|---|---|
| `:shared` | Android, iOS, JVM, JS | All protocol logic, crypto, parsing, `TokenRepository` UI facade |
| `:cli` | JVM console app | `huami-token -m amazfit -e … -p … -b` (mirrors `cli.py`) |
| `:web` | Browser (Kotlin/JS) | Single page: context + e-mail/password + button + results |
| `:androidApp` | Android app | Single screen (Compose Material3): context + fields + button + results |
| `iosApp/` | iOS (SwiftUI) | `ContentView.swift` consuming the shared framework |

## Prerequisites

- JDK 17+, Gradle 8.10+
- Android SDK (compileSdk 34) for `:shared` Android artifacts and `:androidApp`
- Xcode 15+ for the iOS framework + `iosApp`

## Commands

```bash
cd kmp

# Shared unit tests (crypto vectors, parsing, uihh, zip) on JVM
gradle :shared:jvmTest

# CLI
gradle :cli:run --args="-m amazfit -e you@example.com -p secret --bt_keys"
gradle :cli:installDist   # → cli/build/install/huami-token/bin/huami-token

# Web (dev server with hot reload)
gradle :web:jsBrowserDevelopmentRun
# Production bundle (+ copy web/src/main/resources/index.html next to it)
gradle :web:jsBrowserDistribution

# Android APK (needs SDK)
gradle :androidApp:assembleDebug

# iOS framework (needs Xcode): link the XCFramework into iosApp/
gradle :shared:assembleSharedXCFramework
```

CLI flags mirror the Python tool: `-m {amazfit,xiaomi}` (required),
`-e/--email`, `-p/--password` (prompted when missing), `-b/--bt_keys`,
`-g/--gps` (Amazfit: downloads archives + builds `gps_uihh.bin`),
`-n/--no_logout`.

iOS wiring: in Xcode create an app from `iosApp/`, add the built
`shared.xcframework` (Framework Search Paths), `import shared`, build.
`TokenRepository.fetchDevices` is a `suspend` function and appears in Swift
with a completion handler.

## Web / CORS note

Browsers block cross-origin requests. The Zepp/Xiaomi APIs do not send CORS
headers, so the web app exposes an *Advanced → CORS proxy prefix* field that is
prepended to every request (run your own proxy; never paste credentials into a
proxy you do not trust). Without one, the page shows exactly why the call was
blocked and points at the CLI/Android/iOS builds. Also note browsers never
expose `Set-Cookie` to `fetch`, so Xiaomi login (which needs the
`serviceToken` cookie) only works from CLI/Android/iOS or a same-origin
backend — the UI surfaces this as an explicit error.
