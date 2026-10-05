plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "tools.senko.materialdrain.provider.smb"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
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
    // SMB client (LGPL-3.0): the SMB 2/3 protocol, with NTLM sign-in and SMB 3 encryption
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation(libs.junit)
}
