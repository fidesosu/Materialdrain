import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * What every module of the app's hosts (provider-*) is built with: an Android library for the same Android versions as
 * the app (compileSdk and minSdk, from the version catalog, which the app reads too), on Java 11. A module's own
 * build file then only has its name and what it uses.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun version(name: String) = versions.findVersion(name).get().requiredVersion.toInt()

        extensions.configure<LibraryExtension> {
            compileSdk = version("compileSdk")
            defaultConfig.minSdk = version("minSdk")
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}
