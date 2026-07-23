import java.util.Properties
import java.io.StringReader
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.amazecc.app.android"
        minSdk = 24
        targetSdk = 34
        versionCode = verCode
        versionName = verName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            }
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
}
