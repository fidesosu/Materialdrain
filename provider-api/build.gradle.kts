plugins {
    alias(libs.plugins.materialdrain.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tools.senko.materialdrain.provider.api"

    testOptions {
        // ProviderLog writes to android.util.Log, which in plain JVM tests only returns defaults
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // OkHttp's types are part of HttpInterceptors, which every module's clients use
    api(libs.okhttp)
    testImplementation(libs.junit)
}
