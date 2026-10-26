plugins {
    alias(libs.plugins.materialdrain.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tools.senko.materialdrain.provider.pixeldrain"
}

dependencies {
    implementation(project(":provider-api"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
