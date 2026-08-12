plugins {
    // Trick to declare plugins in root project without applying them
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.datetime.get()}")
    }
}

/**
 * Keeps iosApp/Info.plist in sync with the root version.properties.
 * Runs automatically before Xcode builds (via embedAndSignAppleFrameworkForXcode).
 */
val syncIosVersion = tasks.register("syncIosVersion") {
    val versionFile = layout.projectDirectory.file("version.properties")
    val infoPlist = layout.projectDirectory.file("iosApp/iosApp/Info.plist")
    inputs.file(versionFile)
    outputs.file(infoPlist)
    doLast {
        val props = java.util.Properties().apply {
            versionFile.asFile.inputStream().use { load(it) }
        }
        val versionName = props.getProperty("VERSION_NAME") ?: error("VERSION_NAME missing in version.properties")
        val versionCode = props.getProperty("VERSION_CODE") ?: error("VERSION_CODE missing in version.properties")
        var text = infoPlist.asFile.readText()
        text = text.replace(
            Regex("(<key>CFBundleShortVersionString</key>\\s*<string>)[^<]*(</string>)"),
            "$1$versionName$2"
        )
        text = text.replace(
            Regex("(<key>CFBundleVersion</key>\\s*<string>)[^<]*(</string>)"),
            "$1$versionCode$2"
        )
        infoPlist.asFile.writeText(text)
        println("syncIosVersion: Info.plist -> $versionName ($versionCode)")
    }
}

gradle.projectsEvaluated {
    subprojects.forEach { p ->
        p.tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach {
            dependsOn(syncIosVersion)
        }
    }
}
