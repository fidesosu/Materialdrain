plugins {
    alias(libs.plugins.materialdrain.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tools.senko.materialdrain.provider.s3"
}

dependencies {
    implementation(project(":provider-api"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
