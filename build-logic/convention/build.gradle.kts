plugins {
    `kotlin-dsl`
}

dependencies {
    // Only to compile against: the build itself brings these plugins (see the root build.gradle.kts)
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "materialdrain.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
