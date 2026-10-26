plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Voxara SHARED UI — what both apps draw the same way: the Voxara icon set, the fonts (Plus
 * Jakarta Sans, JetBrains Mono), the zone words and the template-style visuals both apps draw
 * (wave lines, capsules, level-reactive accent; docs/design/STYLE.md). Compose UI and foundation
 * only (no Material), so the watch (Wear Material 3) and the phone (Material 3) each keep their own
 * components on top of it.
 */
android {
    namespace = "com.example.voxara.designsystem"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":shared"))
    implementation(platform(libs.compose.bom))
    api(libs.ui)
    api(libs.ui.graphics)
    api(libs.compose.foundation.phone)
    implementation(libs.lifecycle.runtime.compose)
}
