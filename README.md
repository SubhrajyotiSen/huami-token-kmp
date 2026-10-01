# huami-token — Kotlin Multiplatform port

Kotlin Multiplatform port of [huami-token](https://codeberg.org/argrento/huami-token/). It logs in to Zepp (Amazfit)
or Xiaomi Mi Fitness servers and retrieves the Bluetooth pairing keys of bound watches/bands, for use with Gadgetbridge.

## Modules

| Module | Target | Notes |
|---|---|---|
| `:shared` | Android, iOS, JVM, JS | All protocol logic, crypto, parsing, `TokenRepository` UI facade, Compose UI |
| `:cli` | JVM console app | `huami-token -m amazfit -e … -p … -b` (mirrors `cli.py`) |
| `:desktopApp` | Desktop (macOS, Windows, Linux) | Compose Multiplatform (Material 3) desktop application |
| `:androidApp` | Android app | Single screen (Compose Material 3): context + fields + button + results |
| `:web` | Browser (Kotlin/JS) | Single page: context + e-mail/password + button + results |
| `:serverlessProxy` | Node.js / Vercel (Kotlin/JS) | Serverless proxy route resolving CORS & cookie headers (`/api/proxy`) |
| `iosApp/` | iOS (SwiftUI) | `ContentView.swift` consuming the shared framework |

## Prerequisites

- JDK 17+, Gradle 8.10+
- Android SDK (compileSdk 34) for `:shared` Android artifacts and `:androidApp`
- Xcode 15+ for the iOS framework + `iosApp`

## Commands

```bash
# Shared unit tests (crypto vectors, parsing, uihh, zip) on JVM
./gradlew :shared:jvmTest

# Desktop App (Compose Multiplatform)
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDistributionForCurrentOS

# CLI
./gradlew :cli:run --args="-m amazfit -e you@example.com -p secret --bt_keys"
./gradlew :cli:installDist   # → cli/build/install/huami-token/bin/huami-token

# Web (dev server with hot reload)
./gradlew :web:jsBrowserDevelopmentRun
# Production bundle (+ copy web/src/main/resources/index.html next to it)
./gradlew :web:jsBrowserDistribution

# Serverless Proxy (Kotlin/JS for Vercel / Node.js)
./gradlew :serverlessProxy:jsNodeTest
./gradlew :serverlessProxy:copyVercelProxy

# Android APK (needs SDK)
./gradlew :androidApp:assembleDebug

# iOS App (needs Xcode)
# Open iosApp/iosApp.xcodeproj in Xcode and run on a simulator or device.
# Alternatively build via command line:
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16' build
```

CLI flags mirror the Python tool: `-m {amazfit,xiaomi}` (required),
`-e/--email`, `-p/--password` (prompted when missing), `-b/--bt_keys`,
`-g/--gps` (Amazfit: downloads archives + builds `gps_uihh.bin`),
`-n/--no_logout`.

iOS wiring: Open `iosApp/iosApp.xcodeproj` directly in Xcode. The project is configured with a build script phase that automatically invokes `./gradlew :shared:embedAndSignAppleFrameworkForXcode` to build and embed the shared Kotlin framework into the app bundle.

## Web, CORS & Serverless Proxy

Browsers enforce Same-Origin Policies (SOP) and restrict access to HTTP response cookies (`Set-Cookie`). Because upstream Zepp/Amazfit and Xiaomi authentication endpoints do not send permissive CORS headers, direct client-side browser requests are blocked. Furthermore, standard browser `fetch` calls cannot access the `Set-Cookie` headers containing `serviceToken` required for Xiaomi authentication.

To eliminate these browser limitations and maximize utility across web environments, this project includes a standalone Node.js serverless proxy route (`api/proxy.js`):

- **Amazfit / Zepp Login:** Direct client requests or proxy routing handle token exchanges.
- **Xiaomi Login:** Fully functional when routed through the serverless proxy, which extracts and forwards authentication cookies (`serviceToken` via `x-received-cookies` / `X-Cookie`).
- **Custom CORS Proxy Support:** The web client UI provides an *Advanced → CORS proxy prefix* field for specifying a custom proxy URL if not using the built-in route.

### Proxy protocol (JSON envelope)

`api/proxy.js` is not a transparent HTTP proxy. The browser sends `POST /api/proxy` with a JSON body
`{url, method, headers, cookies, bodyBase64, followRedirects}`. The function performs the request with
`redirect: "manual"` and always answers with HTTP 200 + JSON `{status, headers, location, setCookies[], bodyBase64}`.
Redirect statuses (e.g. Zepp's `303`) and every `Set-Cookie` value therefore arrive as plain data that the
browser can't rewrite. Only headers listed in the envelope are forwarded. Proxy-level failures return
`{error, stage}` (`400` malformed, `403` host not allowed, `502` fetch failed, `504` timeout). Only
`*.huami.com`, `*.amazfit.com`, `*.zepp.com`, `*.xiaomi.com` and `*.mi.com` are allowed as targets.

### Limits that can't be fixed in code

If Zepp/Xiaomi block datacenter IPs or require a captcha / 2FA, the web app shows the real upstream status
(e.g. `403`/`429`). In that case use the CLI, Desktop, Android or iOS app, which connect from your own IP.

## Vercel Deployment

The project is preconfigured with `vercel.json` to deploy the single-page web application (`:web`) alongside the zero-dependency standalone Node.js serverless proxy (`api/proxy.js`).

### 1. Build and Route Architecture (`vercel.json`)

```json
{
  "buildCommand": "./gradlew :web:jsBrowserDistribution",
  "outputDirectory": "web/build/dist/js/productionExecutable",
  "rewrites": [
    {
      "source": "/api/proxy",
      "destination": "/api/proxy.js"
    }
  ]
}
```

- **Static Web Frontend:** Compiled from `:web` to `web/build/dist/js/productionExecutable` (serving `index.html` and `huami-token-web.js`).
- **Serverless API Function:** Executed directly from `api/proxy.js` on Node.js without requiring extra Gradle compilation or cold-start penalties, handling `/api/proxy?url=...`.

### 2. Deployment Instructions

#### Option A: Deploy via Vercel CLI

1. Ensure prerequisites (JDK 17+) and Vercel CLI are installed:
   ```bash
   npm install -g vercel
   ```
2. Build the production web bundle:
   ```bash
   ./gradlew :web:jsBrowserDistribution
   ```
3. Deploy to Vercel:
   ```bash
   # Preview deployment
   vercel

   # Production deployment
   vercel --prod
   ```

#### Option B: Deploy via Vercel Git Integration

1. Push the repository to GitHub, GitLab, or Bitbucket.
2. Import the project in the [Vercel Dashboard](https://vercel.com/new).
3. Ensure the build environment has Java 17+ available or pre-build artifacts via CI/CD before deploying.
4. Vercel automatically applies `vercel.json`, building the web interface and executing `api/proxy.js` to deliver full multiplatform functionality with fast build times.

## Credits & Attribution

This project is a Kotlin Multiplatform port of the original [huami-token](https://codeberg.org/argrento/huami-token) tool by **argrento**.
All credit for the reverse engineering of the Huami/Zepp and Xiaomi authentication protocols, endpoints, and token generation belongs to the original project and its contributors.
