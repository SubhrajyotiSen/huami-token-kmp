plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
            implementation(project(":shared-core"))
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// `gradle :web:jsBrowserDevelopmentRun` serves index.html + bundle locally.
// `gradle :web:jsBrowserDistribution` emits web/build/dist/js/productionExecutable/.
