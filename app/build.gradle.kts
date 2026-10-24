plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Every @Preview of the app drawn to a picture on the computer, without a device: ./gradlew :app:recordRoborazziDebug
// puts them in app/build/outputs/roborazzi (see PreviewImagesTest)

/*
 * The version, worked out from git, so it never has to be changed by hand for a release:
 * - versionName: baseVersion below, then the release's number, e.g. "1.6.3". A build of a commit with a release
 *   tag (v1.6.3) is that release; any other build is the next one: one more than the newest v1.6.* tag behind it, or
 *   1.6.0 when there is none. Releases count up without gaps; change baseVersion (to 1.7) only for a big step, which
 *   starts again at 1.7.0.
 *   Builds of the dev branch (VERSION_SUFFIX "-dev", set by the build workflow) add "-dev." and the number of commits
 *   since that newest tag, or since baseVersion became what it is, e.g. "1.6.4-dev.5", which sorts below 1.6.4.
 * - versionCode: the minutes since 2025 at the commit, so each newer commit's build installs over the one before,
 *   stable and dev alike, and it stays far below Android's limit for over 4000 years. A config's min_app_version is
 *   compared with it, so it must never go down.
 * - BuildConfig.COMMIT_TIME: the commit's time, in seconds since 1970, for the About page.
 * The commit's time and tags rather than the build's: building the same commit again gives the same version.
 * On CI anything git can't tell (a shallow checkout, no git) fails the build; elsewhere the version falls back to
 * "1.6.0-local" and the time to now.
 */
val baseVersion = "1.6"
val isCi = System.getenv("CI") == "true"
val isDevBuild = System.getenv("VERSION_SUFFIX") == "-dev"

fun git(vararg args: String): String = providers.exec {
    workingDir = rootDir
    commandLine("git", *args)
}.standardOutput.asText.get().trim()

// Only "v1.6.<number>": never the older tags such as v1.5.20261014.1558, v1.4.0, release or dev-build
val releaseTag = Regex("""^v${Regex.escape(baseVersion)}\.(\d+)$""")
fun releaseNumbers(tags: String): List<Int> = tags.lines().mapNotNull { releaseTag.matchEntire(it.trim())?.groupValues?.get(1)?.toInt() }

val gitVersion: Pair<String, Long>? = runCatching {
    check(git("rev-parse", "--is-shallow-repository") != "true") {
        "the checkout is shallow, so its commits and tags can't be counted (actions/checkout needs fetch-depth: 0)"
    }
    val commitTime = git("log", "-1", "--format=%ct").toLong()
    val released = releaseNumbers(git("tag", "--points-at", "HEAD")).maxOrNull()
    val name = if (released != null) "$baseVersion.$released" else {
        val latest = releaseNumbers(git("tag", "--merged", "HEAD", "--list", "v$baseVersion.*")).maxOrNull()
        val next = "$baseVersion.${latest?.plus(1) ?: 0}"
        if (!isDevBuild) next else {
            val since = if (latest != null) "v$baseVersion.$latest" else {
                // The newest commit that changed how often this line appears: the one that set this base
                val baseLine = "val baseVersion = \"$baseVersion\""
                git("log", "-1", "--format=%H", "-S", baseLine, "--", buildFile.relativeTo(rootDir).invariantSeparatorsPath)
                    .ifEmpty { error("the change to baseVersion $baseVersion isn't committed yet") }
            }
            "$next-dev.${git("rev-list", "--count", "$since..HEAD")}"
        }
    }
    name to commitTime
}.getOrElse { if (isCi) throw GradleException("Can't work out the version from git: ${it.message}", it) else null }
val computedVersionName = gitVersion?.first ?: "$baseVersion.0-local"
val commitEpochSeconds: Long = gitVersion?.second ?: (System.currentTimeMillis() / 1000)

// Calculate Epoch second since: January 1, 2025 at 12:00:00 AM UTC
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
        buildConfigField("long", "COMMIT_TIME", "${commitEpochSeconds}L")

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
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric draws the previews with the app's resources (see roborazzi above)
            isIncludeAndroidResources = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
                it.maxHeapSize = "4096m"
            }
        }
    }
    packaging {
        resources {
            // BouncyCastle's tables for what the SMB provider never uses: post-quantum Picnic (over 1 MB) and the
            // messages of its certificate path checks
            excludes += setOf("org/bouncycastle/pqc/**", "org/bouncycastle/x509/*.properties")
        }
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
    // Only Icons itself: the icons the app uses are its own (see ui/icons/AppIcons.kt), not all of material-icons-extended
    implementation(libs.androidx.compose.material.icons.core)
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
    // Songs which go on in the background, with their controls in the notification (see PlaybackService)
    implementation(libs.androidx.media3.session)

    testImplementation(libs.junit)
    // Draws the previews in a test (see roborazzi above)
    testImplementation(libs.roborazzi.compose.preview.scanner.support)
    testImplementation(libs.robolectric)
    testImplementation(libs.composable.preview.scanner)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.core.splashscreen)
}