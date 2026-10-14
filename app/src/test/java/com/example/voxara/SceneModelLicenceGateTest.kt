package com.example.voxara

import com.example.voxara.core.scene.SceneModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SCENE MODEL LICENCE GATE. The scene model's weights come from TUT Acoustic Scenes 2017, whose
 * EULA forbids use in commercial products (SceneModelWeights.COMMERCIAL_USE_ALLOWED = false).
 * This fails the build check whenever a RELEASE build would run that model — SCENE_MODEL_ENABLED=true
 * in local.properties — without the explicit internal-testing opt-in ALLOW_NONCOMMERCIAL_MODEL=true.
 *
 * Gradle passes what a release build would get as system properties (app/build.gradle.kts), so the
 * check runs in testDebugUnitTest too. The release build itself is also stopped by the
 * checkSceneModelLicence task before preReleaseBuild.
 */
class SceneModelLicenceGateTest {

    private val releaseEnabled = System.getProperty("voxara.sceneModelInRelease")?.toBoolean() ?: false
    private val allowNoncommercial = System.getProperty("voxara.allowNoncommercialModel")?.toBoolean() ?: false

    @Test
    fun `a release build never runs a non-commercial scene model unless explicitly allowed`() {
        if (releaseEnabled && !SceneModel.commercialUseAllowed) {
            assertTrue(
                "SCENE_MODEL_ENABLED=true for release, but the scene model's training data is " +
                    "non-commercial (${SceneModel.trainingData}). Remove it from local.properties, or set " +
                    "ALLOW_NONCOMMERCIAL_MODEL=true for internal testing only.",
                allowNoncommercial,
            )
        }
    }

    @Test
    fun `this variant's BuildConfig follows the gate`() {
        if (BuildConfig.DEBUG) {
            assertTrue("debug builds always run the scene model", BuildConfig.SCENE_MODEL_ENABLED)
        } else {
            assertEquals(releaseEnabled, BuildConfig.SCENE_MODEL_ENABLED)
            if (BuildConfig.SCENE_MODEL_ENABLED && !SceneModel.commercialUseAllowed) assertTrue(allowNoncommercial)
        }
    }

    @Test
    fun `the gradle properties reached the test`() {
        // Guards against the check silently passing because the build stopped passing them.
        assertTrue(System.getProperty("voxara.sceneModelInRelease") != null)
        assertTrue(System.getProperty("voxara.allowNoncommercialModel") != null)
    }
}
