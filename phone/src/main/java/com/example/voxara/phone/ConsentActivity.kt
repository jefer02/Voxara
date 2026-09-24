package com.example.voxara.phone

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * The companion's only screen: what it does, exactly what it sends, the opt-in, and the status.
 * Plain views: the companion stays small and has no UI dependencies.
 */
class ConsentActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(res: Int, size: Float) = TextView(this).apply {
            setText(res)
            textSize = size
            setPadding(0, pad / 2, 0, pad / 2)
        }
        column.addView(text(R.string.phone_title, 22f))
        column.addView(text(R.string.phone_what, 16f))
        column.addView(text(R.string.phone_sends, 16f))
        column.addView(text(R.string.phone_never, 16f))
        column.addView(text(R.string.phone_limits, 14f))
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        column.addView(toggle)
        status = TextView(this).apply { gravity = Gravity.START; textSize = 14f }
        column.addView(status)
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun onToggle() {
        val on = !PhonePrefs.enabled(this)
        PhonePrefs.setEnabled(this, on)
        if (on) {
            // Needed on Android 12+ to receive Bluetooth connection broadcasts.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            ListeningSampler.schedule(this)
        } else {
            ListeningSampler.cancel(this)
        }
        render()
    }

    private fun render() {
        val on = PhonePrefs.enabled(this)
        toggle.setText(if (on) R.string.phone_turn_off else R.string.phone_turn_on)
        val last = PhonePrefs.lastSyncMs(this)
        status.text = when {
            !on -> getString(R.string.phone_status_off)
            last == 0L -> getString(R.string.phone_status_waiting)
            else -> getString(
                R.string.phone_status_synced,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)),
            )
        }
    }
}
