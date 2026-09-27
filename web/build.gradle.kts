plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "huami-token-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared"))
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
    }
}

// `gradle :web:jsBrowserDevelopmentRun` serves index.html + bundle locally.
// `gradle :web:jsBrowserDistribution` emits build/dist/js/productionExecutable/.
