plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10000"
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

val copyVercelProxy = tasks.register<Copy>("copyVercelProxy") {
    dependsOn(tasks.named("jsProductionExecutableCompileSync"))
    from(rootProject.layout.buildDirectory.dir("js/packages/huami-token-kmp-serverlessProxy/kotlin"))
    into(rootProject.layout.projectDirectory.dir("api"))
    include("*.js", "*.map")
}
