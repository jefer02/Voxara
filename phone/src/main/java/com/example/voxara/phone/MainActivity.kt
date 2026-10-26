package com.example.voxara.phone

import android.Manifest
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.data.ThemeMode
import com.example.voxara.phone.ui.PhoneApp
import com.example.voxara.phone.ui.PhoneEvent
import com.example.voxara.phone.ui.PhoneViewModel
import com.example.voxara.phone.ui.theme.VoxaraPhoneTheme

/**
 * The phone app's only activity (replaces the v1 consent screen): theme and appearance (applied
 * live, system bars included), permissions asked in context, the share sheet and hand-offs to
 * other apps. Everything else is in [PhoneViewModel].
 */
class MainActivity : ComponentActivity() {

    private val vm: PhoneViewModel by viewModels()
    private val language by lazy { PhonePrefs.language(this) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(PhoneLocale.localized(newBase))
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshLocal() }

    /** BLUETOOTH_CONNECT: only to receive headphone connect/disconnect broadcasts (API 31+). */
    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshLocal() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        language // read before any change can arrive
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                vm.events.collect { e ->
                    when (e) {
                        is PhoneEvent.Message -> snackbar.showSnackbar(getString(e.text))
                        PhoneEvent.Export -> export()
                        PhoneEvent.OpenWearableApp -> openWearableApp()
                        PhoneEvent.OpenBatterySettings -> openBatterySettings()
                        PhoneEvent.RequestNotifications ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        PhoneEvent.RequestBluetooth ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        is PhoneEvent.LanguageChanged -> if (e.language != language) recreate()
                    }
                }
            }
            // Appearance (Settings → Appearance). Until the store is read, the system's night mode
            // stands in: on Android 12+ it already is the app's own mode (setApplicationNightMode).
            val systemDark = isSystemInDarkTheme()
            val dark = if (state.loaded) state.themeMode.isDark(systemDark) else systemDark
            LaunchedEffect(dark, state.themeMode, state.loaded) { applyAppearance(dark, state.themeMode.takeIf { state.loaded }) }
            VoxaraPhoneTheme(
                darkTheme = dark,
                accent = state.accent,
                reduceMotion = reduceMotion(),
                levelZone = RiskZone.ofPercent(state.head.dosePercent),
            ) {
                // test tags become resource ids, so the Baseline Profile journeys (:baselineprofile) find the tabs and lists
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.semantics { testTagsAsResourceId = true },
                ) { PhoneApp(state, vm.actions, snackbar) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }

    /**
     * Where the wearer sets Voxara to "Unrestricted" so the system does not stop it in the background (on Samsung:
     * Settings → Apps → Voxara → Battery → Unrestricted). The app's own page first (it has that row on every
     * phone), the system's battery-optimisation list as the fallback. Voxara never changes it for them.
     */
    private fun openBatterySettings() {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        for (i in intents) {
            try { startActivity(i); return } catch (_: ActivityNotFoundException) { }
        }
    }

    /**
     * System bars, the window background behind Compose, and (Android 12+) the app's night mode so
     * the splash screen of the next launch matches. [mode] null = the store is not read yet.
     */
    private fun applyAppearance(dark: Boolean, mode: ThemeMode?) {
        window.setBackgroundDrawable((if (dark) Palette.DARK_BG else Palette.LIGHT_BG).toInt().toDrawable())
        val scrim = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (mode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                when (mode) {
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                }
            )
        }
    }

    private fun reduceMotion(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    /** Builds the files off the main thread (a year of minutes is hundreds of thousands of rows), then opens the share sheet. */
    private fun export() {
        lifecycleScope.launch {
            runCatching {
                val intent = withContext(Dispatchers.IO) { vm.exportIntent() }
                startActivity(intent)
            }.onFailure {
                if (it is CancellationException) throw it
                android.widget.Toast.makeText(this@MainActivity, R.string.export_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** The app that pairs the watch: Galaxy Wearable, else the Wear OS app, else Bluetooth settings. */
    private fun openWearableApp() {
        val candidates = listOf(GALAXY_WEARABLE, PIXEL_WATCH_APP, WEAR_OS_APP).mapNotNull { packageManager.getLaunchIntentForPackage(it) }
        val intent = candidates.firstOrNull() ?: Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private companion object {
        const val GALAXY_WEARABLE = "com.samsung.android.app.watchmanager"
        const val PIXEL_WATCH_APP = "com.google.android.apps.wear.companion"
        const val WEAR_OS_APP = "com.google.android.wearable.app"
    }
}
