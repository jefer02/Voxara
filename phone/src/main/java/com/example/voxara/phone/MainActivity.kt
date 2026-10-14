package com.example.voxara.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.voxara.phone.ui.PhoneApp
import com.example.voxara.phone.ui.PhoneEvent
import com.example.voxara.phone.ui.PhoneViewModel
import com.example.voxara.phone.ui.theme.VoxaraPhoneTheme

/**
 * The phone app's only activity (replaces the v1 consent screen): theme, permissions asked in
 * context, the share sheet and hand-offs to other apps. Everything else is in [PhoneViewModel].
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
                        PhoneEvent.RequestNotifications ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        PhoneEvent.RequestBluetooth ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        is PhoneEvent.LanguageChanged -> if (e.language != language) recreate()
                    }
                }
            }
            VoxaraPhoneTheme(reduceMotion = reduceMotion()) {
                PhoneApp(state, vm.actions, snackbar)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun reduceMotion(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    private fun export() {
        runCatching { startActivity(vm.exportIntent()) }
            .onFailure { android.widget.Toast.makeText(this, R.string.export_failed, android.widget.Toast.LENGTH_SHORT).show() }
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
