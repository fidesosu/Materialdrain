import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * The version, made from the time of the commit being built, so it never has to be changed by hand for a release:
 * - versionName: BASE_VERSION, then the commit's date and time in UTC, e.g. "1.4.20261009.1530". The build workflow
 *   adds "-dev" for builds of the dev branch (VERSION_SUFFIX). Change BASE_VERSION only for a big step.
 * - versionCode: the minutes since 2025 at that commit, so each newer commit's build installs over the one before,
 *   stable and dev alike, and it stays far below Android's limit for over 4000 years.
 * The commit's time rather than the build's: building the same commit again gives the same version.
 */
val baseVersion = "1.4"
val commitEpochSeconds: Long = runCatching {
    providers.exec { commandLine("git", "log", "-1", "--format=%ct") }.standardOutput.asText.get().trim().toLong()
}.getOrElse { System.currentTimeMillis() / 1000 }
val versionStamp: String = DateTimeFormatter.ofPattern("yyyyMMdd.HHmm").format(Instant.ofEpochSecond(commitEpochSeconds).atZone(ZoneOffset.UTC))
val versionSuffix: String = System.getenv("VERSION_SUFFIX").orEmpty()
val computedVersionName = "$baseVersion.$versionStamp$versionSuffix"
val computedVersionCode: Int = ((commitEpochSeconds - 1_735_689_600L) / 60).toInt().coerceAtLeast(2)

android {
    namespace = "tools.senko.materialdrain"
    compileSdk = 37

    defaultConfig {
        applicationId = "tools.senko.materialdrain"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("materialdrain-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "materialdrain"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    // The example provider configs are bundled as assets (the Pixeldrain one is imported on first start)
    sourceSets {
        getByName("main") {
            assets.srcDirs("../docs/provider-configs")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {

    implementation(project(":provider-api"))
    implementation(project(":provider-pixeldrain"))
    implementation(project(":provider-generic-rest"))
    implementation(project(":provider-webdav"))
    implementation(project(":provider-s3"))
    implementation(project(":provider-smb"))

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.foundation) // This line is un-commented
    implementation(libs.androidx.lifecycle.viewmodel.compose) // Added ViewModel Compose
    implementation(libs.coil.compose) // Added Coil for image loading
    implementation(libs.coil.gif)
    implementation(libs.core.ktx) // Added for GIF support with Coil
    implementation(libs.google.android.material) // Added Material Components for XML themes

    // Ktor Client Dependencies
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging) // Optional but good for debugging
    implementation(libs.ktor.client.okhttp)

    implementation(libs.okhttp)

    // implementation("org.chromium.net:cronet-embedded:119.6045.31")
    // implementation("com.google.android:cronet-transport-for-okhttp:5.1.0")

    // Kotlinx Serialization runtime (needed for Ktor's JSON serialization)
    implementation(libs.kotlinx.serialization.json.v163)

    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp) // Streams media over the same OkHttp client as the API

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.core.splashscreen)
}