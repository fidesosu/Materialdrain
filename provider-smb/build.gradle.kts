plugins {
    alias(libs.plugins.materialdrain.android.library)
}

android {
    namespace = "tools.senko.materialdrain.provider.smb"
}

dependencies {
    implementation(project(":provider-api"))
    // SMB client (LGPL-3.0): the SMB 2/3 protocol, with NTLM sign-in and SMB 3 encryption
    implementation(libs.jcifs.ng)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
