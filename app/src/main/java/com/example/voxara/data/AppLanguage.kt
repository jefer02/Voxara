package com.example.voxara.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The two languages Voxara ships, plus the default: follow the watch. Anything the wearer has
 * not chosen explicitly stays SYSTEM, so a device set to Spanish speaks Spanish out of the box.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es"),
}

/**
 * Locale lives in SharedPreferences rather than in the DataStore ledger: it has to be read
 * synchronously from `attachBaseContext`, before a coroutine could ever have finished, and it is
 * one enum — not something worth a suspending read on the critical path of every surface.
 */
object LocaleStore {

    private const val FILE = "voxara_locale"
    private const val KEY = "app_language"

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)

    /** Emits on every change so the activity can re-create itself against the new resources. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun read(context: Context): AppLanguage {
        val stored = context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)
        val value = AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.SYSTEM
        _language.value = value
        return value
    }

    fun set(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.name)
            .apply()
        _language.value = language
    }

    /**
     * The locale actually used for resources. SYSTEM honours the watch when the watch is set to
     * a language we ship, and falls back to English when it is not — the same rule the resource
     * resolver would apply on its own, made explicit so voice can use it too.
     */
    fun resolve(context: Context, language: AppLanguage = read(context)): Locale =
        when (language) {
            AppLanguage.ENGLISH -> Locale.forLanguageTag("en")
            AppLanguage.SPANISH -> Locale.forLanguageTag("es")
            AppLanguage.SYSTEM -> {
                val system = LocaleList.getDefault().get(0) ?: Locale.ENGLISH
                if (system.language == "es") Locale.forLanguageTag("es")
                else Locale.forLanguageTag("en")
            }
        }

    /**
     * Wraps a context so every `getString` on it resolves in the chosen language. The activity
     * applies this in `attachBaseContext`; the service, tile and complication apply it per read,
     * because they are created by the system and never see the activity's context.
     */
    fun localized(context: Context, language: AppLanguage = read(context)): Context {
        val locale = resolve(context, language)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
