import java.util.Properties
import java.io.FileInputStream
import java.io.StringReader

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

    buildTypes {
        release {
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
