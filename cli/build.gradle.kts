plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared-core"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("org.huamitoken.cli.MainKt")
}

distributions {
    main {
        contents {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}

tasks.withType<JavaExec> {
    // Allow `gradle :cli:run --args="-m amazfit -e you@x.com -p secret -b"`
    standardInput = System.`in`
}
