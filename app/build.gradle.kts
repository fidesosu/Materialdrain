import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    // The baseline profile, made by :baselineprofile (see BaselineProfileGenerator)
    alias(libs.plugins.androidx.baselineprofile)
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
 *   that change the app (appPaths) since that newest tag, or since baseVersion became what it is (that commit included, so they start at 1 too), e.g.
 *   "1.6.4-dev.5", which sorts below 1.6.4.
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

// What goes into the APK: only commits that change it count for a dev build's number. The same list as the build
// workflow's paths (.github/workflows/build.yml), which decide whether a commit builds at all; keep the two in step
val appPaths = arrayOf(
    ":(glob)app/**",
    ":(glob)provider-*/**",
    ":(glob)docs/provider-configs/**",
    ":(glob)gradle/**",
    ":(glob)build-logic/**",
    ":(glob)*.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    ":(exclude,glob)**/*.md"
)

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
                // The newest commit that changed how often the line above appears: the one that set this base. A regex with
                // "." for the quotes, which Windows would strip from the argument
                val baseLine = "val baseVersion = .${baseVersion.replace(".", "\\.")}."
                git("log", "-1", "--format=%H", "--pickaxe-regex", "-S", baseLine, "--", buildFile.relativeTo(rootDir).invariantSeparatorsPath)
                    .ifEmpty { error("the change to baseVersion $baseVersion isn't committed yet") }
            }
            // The commits after the release, or the one that set the base and those after it: dev builds start at 1
            val commits = git("rev-list", "--count", "$since..HEAD", "--", *appPaths).toInt() + (if (latest == null) 1 else 0)
            "$next-dev.$commits"
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
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tools.senko.materialdrain"
        minSdk = libs.versions.minSdk.get().toInt()
        //noinspection OldTargetApi
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = computedVersionCode
        versionName = computedVersionName
        buildConfigField("long", "COMMIT_TIME", "${commitEpochSeconds}L")

        // The made-up host of the emulator tests in place before the app starts (see FakeHostRunner)
        testInstrumentationRunner = "tools.senko.materialdrain.screenshots.FakeHostRunner"
        // The screenshots are saved through it (see Screenshots.capture in androidTest)
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
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
                // Robolectric reaches into the JDK's file descriptors for the sockets of a test server (UpdateCheckTest)
                it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
        // For the screenshots (app/src/androidTest, see recordScreenshots below): nothing is caught halfway
        animationsDisabled = true
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
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.core.ktx)
    // The window's theme (themes.xml) and the colour utilities the app's own colours are made with (see ui/theme/Color.kt)
    implementation(libs.google.android.material)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp) // Streams media over the same OkHttp client as the API
    // Songs which go on in the background, with their controls in the notification (see PlaybackService)
    implementation(libs.androidx.media3.session)
    // The daily check for a new version in the background, when it's turned on (see UpdateWorker)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.work.testing)

    testImplementation(libs.junit)
    // Serves GitHub's releases to the update check (see UpdateCheckTest)
    testImplementation(libs.okhttp.mockwebserver)
    // Draws the previews in a test (see roborazzi above)
    testImplementation(libs.roborazzi.compose.preview.scanner.support)
    testImplementation(libs.robolectric)
    testImplementation(libs.composable.preview.scanner)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestUtil(libs.androidx.test.services)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profile on devices whose app store doesn't (an APK from GitHub)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}

/*
 * What changed in the app, for "What's new" after an update (see update/WhatsNew.kt) and the update check's description of
 * a release (update.json, made from the APK by .github/scripts/update_json.py): the commits that change what users get (its
 * code and resources, the hosts', the bundled configs; not tests, the workflows or docs), the last 150, newest first, each
 * as the first paragraph of its message with its time. A commit with a "Changes: none" line is left out. The asset
 * changes.json; empty without git.
 */
abstract class ChangesTask : DefaultTask() {
    /** The commit it's made from: made again for each new one */
    @get:Input
    abstract val head: Property<String>

    @get:Internal
    abstract val repository: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun write() {
        val log = if (head.get() == NO_GIT) "" else {
            val out = ByteArrayOutputStream()
            exec.exec {
                workingDir = repository.get().asFile
                commandLine(
                    "git", "log", "--no-merges", "-150", "--format=%ct%x1f%B%x1e", "HEAD", "--",
                    ":(glob)app/src/main/**", ":(glob)provider-*/src/main/**", ":(glob)docs/provider-configs/**", ":(exclude,glob)**/*.md"
                )
                standardOutput = out
            }
            out.toString(Charsets.UTF_8)
        }
        val changes = log.split('\u001e').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { entry ->
            val time = entry.substringBefore('\u001f').trim().toLongOrNull() ?: return@mapNotNull null
            val message = entry.substringAfter('\u001f')
            if (message.lines().any { it.trim().equals("Changes: none", ignoreCase = true) }) return@mapNotNull null
            val text = message.trim().split(Regex("\\r?\\n\\s*\\r?\\n"), limit = 2).first().split(Regex("\\s+")).joinToString(" ")
            if (text.isEmpty()) null else mapOf("time" to time, "text" to text)
        }
        outputDir.get().file("changes.json").asFile.writeText(groovy.json.JsonOutput.toJson(mapOf("changes" to changes)))
    }

    companion object {
        const val NO_GIT = "none"
    }
}

val changesHead: String = runCatching { git("rev-parse", "HEAD") }.getOrNull()?.takeIf { it.isNotEmpty() } ?: ChangesTask.NO_GIT
androidComponents {
    onVariants { variant ->
        val task = tasks.register<ChangesTask>("generate${variant.name.replaceFirstChar { it.uppercase() }}Changes") {
            head.set(changesHead)
            repository.set(rootDir)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, ChangesTask::outputDir)
    }
}

// The pictures of the app for the README and the docs (app/src/androidTest), taken on the emulator or phone that's
// connected (adb), and copied to app/build/screenshots (light/ and dark/), even when some failed (a failed one leaves
// failed-*.png there to show why). CI starts a Pixel 8 emulator for them; locally, start one first
val copyScreenshots = tasks.register<Copy>("copyScreenshots") {
    from(layout.buildDirectory.dir("outputs/connected_android_test_additional_output")) {
        include("**/screenshots/**/*.png")
        // From .../screenshots/light/files.png to light/files.png
        eachFile { path = path.substringAfter("screenshots/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("screenshots"))
}
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach { finalizedBy(copyScreenshots) }
tasks.register("recordScreenshots") {
    group = "verification"
    description = "Takes the screenshots of the app (app/src/androidTest) and puts them in app/build/screenshots."
    dependsOn("connectedDebugAndroidTest")
}
