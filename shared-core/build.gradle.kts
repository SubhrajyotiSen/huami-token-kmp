plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)
    jvm()
    js {
        browser()
    }

    sourceSets {
        val commonMain = getByName("commonMain").apply {
            kotlin.srcDir("src")
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmMain = getByName("jvmMain").apply {
            kotlin.srcDir("src@jvm")
        }
        val jsMain = getByName("jsMain").apply {
            kotlin.srcDir("src@js")
        }
    }
}
