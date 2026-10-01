pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "huami-token-kmp"
include(":shared-core")
include(":shared-ui")
include(":cli")
include(":web")
include(":serverlessProxy")
include(":androidApp")
include(":desktopApp")
