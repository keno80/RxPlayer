plugins { id("com.android.application") }
android {
    namespace = "com.keno.rxplayer.m1probe"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.keno.rxplayer.m1probe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "m1-probe"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}
dependencies {
    implementation(fileTree(providers.gradleProperty("m1RuntimeDir").get()) { include("*.jar") })
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
