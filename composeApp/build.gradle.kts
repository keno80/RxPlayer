import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.security.MessageDigest
import java.net.URI
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.material3.expressive)
            implementation(libs.kotlinx.coroutines.core)
            implementation("io.ktor:ktor-client-core:3.3.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("androidx.sqlite:sqlite-bundled:2.7.1")
            implementation("com.materialkolor:material-color-utilities:3.0.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("io.ktor:ktor-client-mock:3.3.1")
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation("io.ktor:ktor-client-okhttp:3.3.1")
            implementation("androidx.media3:media3-exoplayer:1.8.0")
            implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                // Read Windows' animation accessibility preference; no settings are written.
                implementation("net.java.dev.jna:jna:5.17.0")
                implementation("io.ktor:ktor-client-okhttp:3.3.1")
            }
        }
    }
}

android {
    namespace = "com.keno.rxplayer.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    packageOfResClass = "com.keno.rxplayer.generated.resources"
}

compose.desktop {
    application {
        mainClass = "com.keno.rxplayer.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "RxPlayer"
            packageVersion = "0.1.0"
            modules("java.desktop")
            windows {
                menuGroup = "RxPlayer"
                shortcut = true
                upgradeUuid = "e8de954e-cb35-4e35-95bd-f3c028981af3"
            }
        }
    }
}

// Development runtime only. Final installers and license/source distribution remain a release gate.
val engineDirectory = layout.buildDirectory.dir("validated-engine/mpv-v0.41.0-dev-gdc4b285c9")
val prepareDesktopEngine by tasks.registering {
    val executable = engineDirectory.map { it.file("mpv.exe") }
    outputs.file(executable)
    doLast {
        fun digest(file: File) = MessageDigest.getInstance("SHA-256")
            .let { md -> file.inputStream().use { input -> val buffer = ByteArray(65536); var n = input.read(buffer); while (n > 0) { md.update(buffer, 0, n); n = input.read(buffer) } }; md.digest().joinToString("") { "%02x".format(it) } }
        val expected = "ecf988cdf821d08807cb45e7fd9864f3697383c730e39edb484265f3d6c4c765"
        val destination = executable.get().asFile
        destination.parentFile.mkdirs()
        val cached = File(System.getenv("TEMP"), "rxplayer-m1-runtime/mpv/mpv.exe")
        if (cached.isFile && digest(cached) == expected) {
            cached.copyTo(destination, overwrite = true)
            File(cached.parentFile, "LICENSE.GPL").takeIf { it.isFile }?.copyTo(File(destination.parentFile, "LICENSE.GPL"), overwrite = true)
        } else {
            val archive = File(destination.parentFile, "runtime.zip")
            URI("https://github.com/mpv-player/mpv/releases/download/git-release/mpv-v0.41.0-dev-gdc4b285c9-40753318951-x86_64-w64-mingw32-full.zip").toURL().openStream().use { input -> archive.outputStream().use(input::copyTo) }
            check(digest(archive) == "8d960b51c2e52081b6cae21555eed71c0a2e30c2e1948e49c96f0074bd903f30") { "Runtime archive checksum mismatch" }
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('/') in setOf("mpv.exe", "LICENSE.GPL") }.forEach { entry ->
                    zip.getInputStream(entry).use { input -> File(destination.parentFile, entry.name.substringAfterLast('/')).outputStream().use(input::copyTo) }
                }
            }
            archive.delete()
        }
        check(digest(destination) == expected) { "Runtime executable checksum mismatch" }
    }
}
tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        dependsOn(prepareDesktopEngine)
        systemProperty("rxplayer.mpv.path", engineDirectory.get().file("mpv.exe").asFile.absolutePath)
        systemProperty("java.net.useSystemProxies", "true")
    }
}
