plugins {
    alias(libs.plugins.android.library)
}

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
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
