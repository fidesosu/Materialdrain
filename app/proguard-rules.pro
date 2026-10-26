# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# SMB (jCIFS-ng) finds its protocol classes, name services and crypto provider by name at runtime, so R8 must keep them
-keep class jcifs.** { *; }
-keep interface jcifs.** { *; }
-dontwarn jcifs.**
# BouncyCastle, for jCIFS-ng: its ASN.1 and key derivation classes jCIFS-ng uses directly, so R8 keeps those like any code.
# It also asks BouncyCastle's provider for MD4 (NTLM sign-in) and AESCMAC (SMB 3 signing) by name, and the provider finds
# each algorithm's classes by name as well, skipping those that aren't there: only those are kept by name. Keeping all of
# BouncyCastle made it most of the APK
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD4* { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.AES* { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

# The debug and verbose lines of the log are for development: a release build leaves them out (with what they'd say,
# such as the requests made), so they can't end up in a bug report
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# WorkManager's database (the daily update check, see UpdateWorker) is Room's, which makes it by reflection, by its
# constructor without arguments: R8 removed that, and the release build crashed as it started
-keep class * extends androidx.room.RoomDatabase { <init>(); }
