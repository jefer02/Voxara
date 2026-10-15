import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Voxara PHONE COMPANION (v2). Same applicationId and signing key as the watch app — the
 * Wearable Data Layer only connects an app to itself on the other device.
 *
 * AI settings come from the untracked local.properties, like the watch: the key is for
 * development only and release builds get an empty string.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: default).trim()
fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.voxara.phone"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.voxara"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "0.9.2-phone"

        buildConfigField("String", "AI_BASE_URL", quoted(localProp("AI_BASE_URL", "https://api.deepseek.com")))
        buildConfigField("String", "AI_MODEL", quoted(localProp("AI_MODEL", "deepseek-flash")))
        buildConfigField("String", "AI_API_KEY", quoted(""))
    }

    buildTypes {
        debug {
            buildConfigField("String", "AI_API_KEY", quoted(localProp("AI_API_KEY")))
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            // Robolectric screenshot tests render real resources.
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "2g"
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Release APK must carry no scene model weights (same check as :app; :phone depends on :shared).
apply(from = rootProject.file("gradle/scene-weights-check.gradle.kts"))

dependencies {
    implementation(project(":shared"))
    implementation(project(":shared-ui"))
    implementation(libs.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.wear.remote.interactions)
    implementation(libs.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation.phone)
    implementation(libs.m3)
    implementation(libs.m3.navigation.suite)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.glance.appwidget)
    // Play services brings Fragment 1.1 transitively; 1.3+ is required with the Activity Result APIs.
    implementation(libs.fragment)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.ui.test.junit4)
}
