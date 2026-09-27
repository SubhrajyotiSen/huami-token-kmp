// Root build: plugin versions are applied in module builds.
plugins {
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.compose") version "2.4.20" apply false
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}
