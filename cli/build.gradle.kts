plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("org.huamitoken.cli.MainKt")
}

tasks.withType<JavaExec> {
    // Allow `gradle :cli:run --args="-m amazfit -e you@x.com -p secret -b"`
    standardInput = System.`in`
}
