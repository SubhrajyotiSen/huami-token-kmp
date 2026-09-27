plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "org.huamitoken.shared"
        compileSdk = 34
        minSdk = 24
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
