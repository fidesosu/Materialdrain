plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tools.senko.materialdrain.provider.genericrest"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    testOptions {
        // ProviderLog writes to android.util.Log, which in plain JVM tests only returns defaults
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":provider-api"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json.v163)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation("com.squareup.okhttp3:mockwebserver:5.1.0")
}
