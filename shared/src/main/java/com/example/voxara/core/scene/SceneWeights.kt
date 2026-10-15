package com.example.voxara.core.scene

/**
 * SCENE WEIGHTS — the shape of a trained scene model, as [SceneModel] reads it.
 *
 * The only implementation, the generated `SceneModelWeights`, lives in `shared/src/debug` — it is
 * derived from non-commercial training data and must never be in a release APK. Each variant has
 * a `SceneModelSource` twin: debug points at those weights, release has none (`weights = null`), so
 * a release build does not contain the weights class at all. Pattern: Bench / DebugTools in :app.
 */
interface SceneWeights {
    val FEATURE_VERSION: Int
    val TRAINING_DATA: String
    val COMMERCIAL_USE_ALLOWED: Boolean

    /** Output classes (the dataset's scene labels), in output order. */
    val CLASSES: Array<String>

    /** Mode of each class, as an index into SceneModel.MODES. */
    val CLASS_MODE: IntArray

    /** Per-mode multiplier applied to the summed class probabilities before renormalising. */
    val MODE_WEIGHT: FloatArray
    val MEAN: FloatArray
    val SCALE: FloatArray

    /** Layer sizes, input first. */
    val SIZES: IntArray

    /** Row-major weights [in][out] and biases per layer. ReLU between layers, softmax at the end. */
    val WEIGHTS: Array<FloatArray>
    val BIASES: Array<FloatArray>
}
