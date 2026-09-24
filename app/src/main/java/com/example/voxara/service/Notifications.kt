package com.example.voxara.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voxara.R
import com.example.voxara.data.LocaleStore
import com.example.voxara.presentation.MainActivity

/** Channel and ids shared by the dosimeter and the resume prompt. */
object Notifications {

    const val CHANNEL_ID = "voxara_dosimeter"
    const val DOSIMETER_ID = 4181
    private const val RESUME_ID = 4182

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    LocaleStore.localized(context).getString(R.string.channel_dosimeter),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }
    }

    /**
     * Android 14+ will not let a microphone foreground service start from the background or
     * from BOOT_COMPLETED, so the wearer gets one quiet tap-to-resume prompt instead.
     */
    fun postResumePrompt(context: Context, afterReboot: Boolean = true) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val res = LocaleStore.localized(context)
        val open = PendingIntent.getActivity(
            context, RESUME_ID, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voxara_status)
            .setContentTitle(res.getString(R.string.resume_title))
            .setContentText(
                res.getString(if (afterReboot) R.string.resume_text else R.string.resume_text_paused)
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(RESUME_ID, n)
    }

    fun cancelResumePrompt(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(RESUME_ID)
    }
}
