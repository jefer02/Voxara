plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

/**
 * Voxara BASELINE PROFILE + MACROBENCHMARKS for the phone app (:phone). The generator walks the journeys that must be fast
 * (app start, Home and its scroll, Trends and its scroll, opening Today from the dose card) and writes the profile into
 * phone/src/main/generated/baselineProfiles; the benchmarks measure start-up with and without it and the frame timing of the
 * same journeys. Both need a device or emulator (API 28+; rooted, or API 33+):
 *
 *     ./gradlew :phone:generateBaselineProfile
 *     ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 */
android {
    namespace = "com.example.voxara.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":phone"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
