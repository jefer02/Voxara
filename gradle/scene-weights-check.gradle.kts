import java.util.Properties
import java.util.zip.ZipFile

/**
 * RELEASE APKS CARRY NO SCENE MODEL WEIGHTS. Applied by :app and :phone (both depend on :shared,
 * whose generated weights live in its debug source set only).
 *
 * Registers `verifyReleaseHasNoSceneWeights` in the applying module and runs it after that
 * module's `assembleRelease`: it opens every classes*.dex of the module's release APK and fails if
 * it finds the weights class (SceneModelWeights) or the training-data marker. Skipped only for
 * the explicit internal-testing opt-in in local.properties (SCENE_MODEL_ENABLED=true AND
 * ALLOW_NONCOMMERCIAL_MODEL=true), which builds release with the weights on purpose.
 */
val sceneCheckProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun sceneCheckFlag(name: String) = (sceneCheckProps.getProperty(name) ?: "false").trim().toBoolean()

val verifyReleaseHasNoSceneWeights by tasks.registering {
    group = "verification"
    description = "Fails if this module's release APK contains the scene model weights."
    val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
    val module = project.path
    val optIn = sceneCheckFlag("SCENE_MODEL_ENABLED") && sceneCheckFlag("ALLOW_NONCOMMERCIAL_MODEL")
    doLast {
        if (optIn) {
            logger.warn("$module: release built WITH the non-commercial scene model (internal testing opt-in).")
            return@doLast
        }
        val apks = apkDir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
        if (apks.isEmpty()) throw GradleException("$module: no release APK in $apkDir")
        val markers = listOf(
            "Lcom/example/voxara/core/scene/SceneModelWeights;",
            "Zenodo 400515",
        ).map { it.toByteArray(Charsets.UTF_8) }
        fun ByteArray.contains(m: ByteArray): Boolean {
            outer@ for (i in 0..size - m.size) {
                for (j in m.indices) if (this[i + j] != m[j]) continue@outer
                return true
            }
            return false
        }
        for (apk in apks) ZipFile(apk).use { zip ->
            val dexes = zip.entries().asSequence().filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }.toList()
            if (dexes.isEmpty()) throw GradleException("$module ${apk.name}: no dex files")
            for (dex in dexes) {
                val bytes = zip.getInputStream(dex).use { it.readBytes() }
                for (m in markers) if (bytes.contains(m)) {
                    throw GradleException("$module ${apk.name}/${dex.name} contains scene model weight data (${String(m)})")
                }
            }
            logger.lifecycle("$module ${apk.name}: ${dexes.size} dex files, no scene model weights")
        }
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(verifyReleaseHasNoSceneWeights) }
