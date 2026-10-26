plugins {
    alias(libs.plugins.materialdrain.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tools.senko.materialdrain.provider.genericrest"

    testOptions {
        // ProviderLog writes to android.util.Log, which in plain JVM tests only returns defaults
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":provider-api"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
