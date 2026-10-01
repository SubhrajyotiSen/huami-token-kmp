@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("compose") {
                withJvm()
                withIos()
                withCompilations { it.target.name.startsWith("android") }
            }
        }
    }

    jvm()
    android {
        namespace = "org.huamitoken.shared"
        compileSdk = 37
        minSdk = 24
    }
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        named("composeMain") {
            dependencies {
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
            }
        }

        named("iosMain") {
            dependsOn(getByName("composeMain"))
        }

        named("androidMain") {
            dependencies {
                implementation(libs.androidx.compose.ui.tooling)
            }
        }

        named("jvmMain") {
            dependencies {
                implementation(compose.components.uiToolingPreview)
                implementation(compose.uiTooling)
            }
        }
    }
}
