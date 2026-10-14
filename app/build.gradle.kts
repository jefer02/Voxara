import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * AI settings come from the untracked local.properties, never from source. The key is for
 * development only: release builds get an empty string, so it can never ship inside an APK.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: default).trim()
fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/**
 * SCENE MODEL LICENCE GATE. The Auto-mode scene model is trained on TUT Acoustic Scenes 2017,
 * whose EULA forbids use in commercial products. Debug builds always run it; release builds only
 * when local.properties says SCENE_MODEL_ENABLED=true. Shipping it while its weights are flagged
 * non-commercial also needs ALLOW_NONCOMMERCIAL_MODEL=true (internal testing only): see
 * checkSceneModelLicence below and SceneModelLicenceGateTest.
 */
val sceneModelInRelease = localProp("SCENE_MODEL_ENABLED", "false").toBoolean()
val allowNoncommercialModel = localProp("ALLOW_NONCOMMERCIAL_MODEL", "false").toBoolean()

android {
    namespace = "com.example.voxara"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.voxara"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.9"

        // OpenAI-compatible endpoint (DeepSeek by default). Both are overridable per machine.
        buildConfigField("String", "AI_BASE_URL", quoted(localProp("AI_BASE_URL", "https://api.deepseek.com")))
        buildConfigField("String", "AI_MODEL", quoted(localProp("AI_MODEL", "deepseek-flash")))
        buildConfigField("String", "AI_API_KEY", quoted(""))
        buildConfigField("boolean", "SCENE_MODEL_ENABLED", "false")
    }

    buildTypes {
        debug {
            buildConfigField("String", "AI_API_KEY", quoted(localProp("AI_API_KEY")))
            buildConfigField("boolean", "SCENE_MODEL_ENABLED", "true")
        }
        release {
            buildConfigField("boolean", "SCENE_MODEL_ENABLED", sceneModelInRelease.toString())
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    testOptions {
        unitTests {
            // Robolectric screenshot tests render real resources.
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                // What a release build would get, so the licence gate test can check it from any variant.
                it.systemProperty("voxara.sceneModelInRelease", sceneModelInRelease.toString())
                it.systemProperty("voxara.allowNoncommercialModel", allowNoncommercialModel.toString())
                it.maxHeapSize = "2g"
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

/**
 * Fails any release build that would ship the non-commercial scene model without the explicit
 * internal-testing opt-in. Reads the generated weights' licence flag from source.
 */
val checkSceneModelLicence by tasks.registering {
    val weights = rootProject.file("shared/src/main/java/com/example/voxara/core/scene/SceneModelWeights.kt")
    val enabled = sceneModelInRelease
    val allow = allowNoncommercialModel
    inputs.file(weights)
    doLast {
        val commercial = weights.readText().contains("COMMERCIAL_USE_ALLOWED = true")
        if (enabled && !commercial && !allow) {
            throw GradleException(
                "SCENE_MODEL_ENABLED=true in a release build, but SceneModelWeights.COMMERCIAL_USE_ALLOWED " +
                    "is false (TUT Acoustic Scenes EULA: non-commercial). Remove SCENE_MODEL_ENABLED from " +
                    "local.properties, or set ALLOW_NONCOMMERCIAL_MODEL=true for internal testing only."
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkSceneModelLicence) }

dependencies {
    implementation(project(":shared"))
    implementation(project(":shared-ui"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.core.ktx)
    implementation(libs.guava)
    implementation(libs.play.services.wearable)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material3)
    implementation(libs.tiles)
    implementation(libs.tiles.tooling.preview)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.wear.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.datastore.preferences)
    implementation(libs.wear.ongoing)
    implementation(libs.wear)
    implementation(libs.concurrent.futures)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.ui.test.junit4)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.tiles.renderer)
    debugImplementation(libs.tiles.tooling)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
