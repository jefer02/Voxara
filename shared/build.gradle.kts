import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

/**
 * SCENE MODEL WEIGHTS ARE DEBUG-ONLY. The generated weights (non-commercial training data) live in
 * src/debug; src/release has a weightless SceneModelSource twin, so a release build does not contain
 * them at all (checked on the APK by :app:verifyReleaseHasNoSceneWeights). Only the explicit
 * internal-testing opt-in in local.properties (SCENE_MODEL_ENABLED=true AND
 * ALLOW_NONCOMMERCIAL_MODEL=true, the same pair :app's licence gate reads) builds release from the
 * debug twin instead.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localFlag(name: String) = (localProps.getProperty(name) ?: "false").trim().toBoolean()
val sceneModelInRelease = localFlag("SCENE_MODEL_ENABLED") && localFlag("ALLOW_NONCOMMERCIAL_MODEL")

/**
 * Voxara SHARED — the logic the watch and the phone must agree on: the dose math, zones, minute
 * ledger, alert engine, AdviceGrammar, Tono's privacy gate and coach, and the Data Layer contract.
 * No UI and no Compose, so everything here is unit-tested on the JVM.
 */
android {
    namespace = "com.example.voxara.shared"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The phone companion's minimum; the watch (30) is above it.
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        // Internal testing only: release gets the debug twin (with weights) instead of src/release.
        if (sceneModelInRelease) getByName("release").java.setSrcDirs(listOf("src/debug/java"))
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
