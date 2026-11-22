import java.util.Properties
import java.io.StringReader
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    val raw = versionPropsFile.readText(Charsets.UTF_8).replace("\uFEFF", "")
    versionProps.load(StringReader(raw))
}
val verName = versionProps["VERSION_NAME"]?.toString() ?: "1.0.0"
val verCode = versionProps["VERSION_CODE"]?.toString()?.toIntOrNull() ?: 1

val keystoreFile = rootProject.file("release.keystore")
val keystoreBase64File = rootProject.file("keystore_base64.txt")
val keystorePropsFile = rootProject.file("keystore.properties")

data class SigningConfigData(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

fun resolveSigningConfig(): SigningConfigData? {
    // 1) keystore.properties (most explicit)
    if (keystorePropsFile.exists()) {
        val props = Properties()
        props.load(keystorePropsFile.inputStream())
        val filePath = props.getProperty("storeFile") ?: return null
        val storeFile = rootProject.file(filePath).let { if (it.isAbsolute) it else rootProject.file(filePath) }
        if (!storeFile.exists()) return null
        return SigningConfigData(
            storeFile = storeFile,
            storePassword = props.getProperty("storePassword") ?: return null,
            keyAlias = props.getProperty("keyAlias") ?: return null,
            keyPassword = props.getProperty("keyPassword") ?: return null
        )
    }

    val envStorePassword = System.getenv("KEYSTORE_PASSWORD")
    val envKeyAlias = System.getenv("KEY_ALIAS")
    val envKeyPassword = System.getenv("KEY_PASSWORD")
    val hasEnvCreds = !envStorePassword.isNullOrEmpty() && !envKeyAlias.isNullOrEmpty() && !envKeyPassword.isNullOrEmpty()

    // 2) release.keystore + env vars
    if (keystoreFile.exists() && hasEnvCreds) {
        return SigningConfigData(keystoreFile, envStorePassword, envKeyAlias, envKeyPassword)
    }

    // 3) keystore_base64.txt + env vars
    if (keystoreBase64File.exists() && hasEnvCreds) {
        val rawBase64 = keystoreBase64File.readText()
        val decoded = Base64.getMimeDecoder().decode(rawBase64)
        val tempFile = layout.buildDirectory.file("intermediates/temp_keystore/release.keystore").get().asFile
        tempFile.parentFile.mkdirs()
        if (!tempFile.exists() || tempFile.length() != decoded.size.toLong()) {
            tempFile.writeBytes(decoded)
        }
        return SigningConfigData(tempFile, envStorePassword, envKeyAlias, envKeyPassword)
    }

    return null
}

val signingData = resolveSigningConfig()

android {
    namespace = "com.amazecc.app.android"
    // Compose 1.12.x AARs declare minCompileSdk 37; compileSdk is independent of
    // targetSdk (kept at 35 to preserve current edge-to-edge runtime behavior).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.amazecc.app.android"
        minSdk = 26
        targetSdk = 35
        versionCode = verCode
        versionName = verName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // D-174: the OpenCV AAR bundles native libs for every ABI (~117 MB unpacked). Shipping
        // only arm64-v8a + armeabi-v7a cuts that down sharply. Trade-off: the APK will not install
        // on other ABIs (including x86 emulators), and any native-library-dependent code — OpenCV
        // fallback AND on-device OCR/inference — is unavailable there. Kotlin-only paths (UI,
        // manual crop, share) are unaffected. Accept only if these two ABIs are the support target.
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            if (signingData != null) {
                storeFile = signingData.storeFile
                storePassword = signingData.storePassword
                keyAlias = signingData.keyAlias
                keyPassword = signingData.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (signingData != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            if (signingData != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // D-167 — geotagged camera. One CameraX version for every artifact; camera-extensions is the
    // supported OEM integration point (Night/Bokeh/Face retouch/HDR), not an unverified "codec" hack.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.play.services.location)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
