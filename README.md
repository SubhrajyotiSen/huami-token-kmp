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

# iOS framework (needs Xcode): link the XCFramework into iosApp/
./gradlew :shared:assembleSharedXCFramework
```

CLI flags mirror the Python tool: `-m {amazfit,xiaomi}` (required),
`-e/--email`, `-p/--password` (prompted when missing), `-b/--bt_keys`,
`-g/--gps` (Amazfit: downloads archives + builds `gps_uihh.bin`),
`-n/--no_logout`.

iOS wiring: in Xcode create an app from `iosApp/`, add the built
`shared.xcframework` (Framework Search Paths), `import shared`, build.
`TokenRepository.fetchDevices` is a `suspend` function and appears in Swift
with a completion handler.

## Web, CORS & Serverless Proxy

Browsers enforce Same-Origin Policies (SOP) and restrict access to HTTP response cookies (`Set-Cookie`). Because upstream Zepp/Amazfit and Xiaomi authentication endpoints do not send permissive CORS headers, direct client-side browser requests are blocked. Furthermore, standard browser `fetch` calls cannot access the `Set-Cookie` headers containing `serviceToken` required for Xiaomi authentication.

To eliminate these browser limitations and maximize utility across web environments, this project includes a Kotlin/JS serverless proxy route (`:serverlessProxy`):

- **Amazfit / Zepp Login:** Direct client requests or proxy routing handle token exchanges.
- **Xiaomi Login:** Fully functional when routed through the serverless proxy, which extracts and forwards authentication cookies (`serviceToken` via `x-received-cookies` / `X-Cookie`).
- **Custom CORS Proxy Support:** The web client UI provides an *Advanced → CORS proxy prefix* field for specifying a custom proxy URL if not using the built-in route.

## Vercel Deployment

The project is preconfigured with `vercel.json` to deploy both the Kotlin/JS single-page web application (`:web`) and the Kotlin/JS serverless API proxy (`:serverlessProxy`) in a single cohesive build.

### 1. Build and Route Architecture (`vercel.json`)

```json
{
  "buildCommand": "./gradlew :web:jsBrowserDistribution :serverlessProxy:copyVercelProxy",
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
- **Serverless API Function:** Compiled from `:serverlessProxy` into `api/huami-token-kmp-serverlessProxy.js` and loaded by `api/proxy.js` to handle `/api/proxy?url=...`.

### 2. Deployment Instructions

#### Option A: Deploy via Vercel CLI

1. Ensure prerequisites (JDK 17+) and Vercel CLI are installed:
   ```bash
   npm install -g vercel
   ```
2. Build the production web bundle and serverless function proxy:
   ```bash
   ./gradlew :web:jsBrowserDistribution :serverlessProxy:copyVercelProxy
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
4. Vercel automatically applies `vercel.json`, building both the web interface and the serverless proxy to deliver full multiplatform functionality.

## Credits & Attribution

This project is a Kotlin Multiplatform port of the original [huami-token](https://codeberg.org/argrento/huami-token) tool by **argrento**.
All credit for the reverse engineering of the Huami/Zepp and Xiaomi authentication protocols, endpoints, and token generation belongs to the original project and its contributors.
