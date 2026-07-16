import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
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
    implementation("androidx.core:core-splashscreen:1.0.1")
}
