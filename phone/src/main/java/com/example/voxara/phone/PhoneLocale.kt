package com.example.voxara.phone

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.example.voxara.settings.LanguageChoice
import java.util.Locale

/**
 * The phone app's language: the synced setting (English, Spanish, or the phone's). minSdk 28
 * predates the per-app locale API, so the activity's base context is wrapped, like on the watch.
 */
object PhoneLocale {

    private val shipped = setOf("en", "es")

    fun resolve(context: Context, choice: LanguageChoice = PhonePrefs.language(context)): Locale = when (choice) {
        LanguageChoice.ENGLISH -> Locale.ENGLISH
        LanguageChoice.SPANISH -> Locale("es")
        LanguageChoice.SYSTEM -> {
            val system = LocaleList.getDefault().get(0) ?: Locale.ENGLISH
            if (system.language in shipped) system else Locale.ENGLISH
        }
    }

    fun localized(context: Context, choice: LanguageChoice = PhonePrefs.language(context)): Context {
        val locale = resolve(context, choice)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
