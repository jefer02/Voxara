plugins {
    alias(libs.plugins.android.application)
}

/**
 * Voxara PHONE COMPANION (v1). Same applicationId and signing key as the watch app — the
 * Wearable Data Layer only connects an app to itself on the other device.
 */
android {
    namespace = "com.example.voxara.phone"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.voxara"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.9-phone"
    }

    buildTypes {
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
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
