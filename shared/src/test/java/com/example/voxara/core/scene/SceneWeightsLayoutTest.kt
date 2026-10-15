package com.example.voxara.core.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WEIGHTS STAY OUT OF RELEASE — source-level half of the check (the APK half is
 * :app:verifyReleaseHasNoSceneWeights, which scans the release dex after assembleRelease).
 * The generated weights may only exist in src/debug; the release twin of SceneModelSource must
 * carry no weight data and say so. Unit tests run with the module directory as working directory.
 */
class SceneWeightsLayoutTest {

    private val pkg = "java/com/example/voxara/core/scene"
    private fun src(set: String, name: String) = File("src/$set/$pkg/$name")

    @Test
    fun `the weights live only in the debug source set`() {
        assertTrue("debug weights missing", src("debug", "SceneModelWeights.kt").isFile)
        for (set in listOf("main", "release")) {
            assertFalse("weights must not be in src/$set", src(set, "SceneModelWeights.kt").exists())
        }
        // Nothing else outside src/debug may define or reference the weights object.
        val offenders = listOf("main", "release").flatMap { set ->
            File("src/$set").walkTopDown().filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains("object SceneModelWeights") || it.readText().contains("= SceneModelWeights") }
                .map { it.path }.toList()
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the release twin has no weight data`() {
        val release = src("release", "SceneModelSource.kt").readText()
        assertTrue(release.contains("val weights: SceneWeights? = null"))
        assertTrue(release.contains("const val COMMERCIAL_USE_ALLOWED = false"))
        for (forbidden in listOf("floatArrayOf", "doubleArrayOf", "SceneModelWeights =", "Zenodo")) {
            assertFalse("release twin contains $forbidden", release.contains(forbidden))
        }
    }

    @Test
    fun `the release twin's Concert threshold is music-derived, nothing from the scene model's dataset`() {
        val release = src("release", "SceneModelSource.kt").readText()
        assertTrue(release.contains("Derived ONLY from freely"))
        assertTrue(release.contains("derive_concert_threshold.py"))
        for (forbidden in listOf("TUT", "Acoustic Scenes", "DCASE", "train.py: 0.5")) {
            assertFalse("release twin mentions $forbidden", release.contains(forbidden))
        }
        // Both twins carry the same, single threshold.
        val value = Regex("""MUSIC_TONALITY_MAX_DB = (-?[0-9.]+)""")
        assertEquals(
            value.find(src("debug", "SceneModelSource.kt").readText())!!.groupValues[1],
            value.find(release)!!.groupValues[1],
        )
    }

    @Test
    fun `both twins have the same API`() {
        fun api(set: String) = Regex("""(?m)^\s+(?:const )?val (\w+)""")
            .findAll(src(set, "SceneModelSource.kt").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(api("debug"), api("release"))
        assertTrue(api("release").containsAll(setOf("weights", "COMMERCIAL_USE_ALLOWED", "MUSIC_TONALITY_MAX_DB")))
    }

    @Test
    fun `this (debug) test variant does have the model`() {
        assertTrue(SceneModel.available)
        assertEquals(SceneModelWeights.CLASSES.size, SceneModel.classes.size)
    }
}
