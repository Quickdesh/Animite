package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.browse.source.SourcesPresenter
import uy.kohesive.injekt.injectLazy
import java.util.Locale

/**
 * Utility class to change the application's language in runtime.
 */
object LocaleHelper {

    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Returns display name of a string language code.
     */
    fun getSourceDisplayName(lang: String?, context: Context): String {
        return when (lang) {
            SourcesPresenter.LAST_USED_KEY -> context.getString(R.string.last_used_source)
            SourcesPresenter.PINNED_KEY -> context.getString(R.string.pinned_sources)
            "other" -> context.getString(R.string.other_source)
            "all" -> context.getString(R.string.multi_lang)
            else -> getDisplayName(lang)
        }
    }

    /**
     * Returns display name of a string language code.
     *
     * @param lang empty for system language
     */
    fun getDisplayName(lang: String?): String {
        if (lang == null) {
            return ""
        }

        val locale = when (lang) {
            "" -> LocaleListCompat.getAdjustedDefault()[0]
            "zh-CN" -> Locale.forLanguageTag("zh-Hans")
            "zh-TW" -> Locale.forLanguageTag("zh-Hant")
            else -> Locale.forLanguageTag(lang)
        }
        return locale!!.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
    }

    /**
     * Return the default languages enabled for the sources.
     */
    fun getDefaultEnabledLanguages(): Set<String> {
        return setOf("all", "en", Locale.getDefault().language)
    }

    /**
     * Return English display string from string language code
     */
    fun getSimpleLocaleDisplay(lang: String): String {
        val sp = lang.split("_", "-")
        return Locale(sp[0]).getDisplayLanguage(getLocaleFromString(null))
    }

    /**
     * Returns the locale for the value stored in preferences, defaults to main system language.
     *
     * @param pref the string value stored in preferences.
     */
    private fun getLocaleFromString(pref: String?): Locale {
        if (pref.isNullOrEmpty()) {
            return LocaleListCompat.getDefault()[0]!!
        }
        return getLocale(pref)
    }
}
