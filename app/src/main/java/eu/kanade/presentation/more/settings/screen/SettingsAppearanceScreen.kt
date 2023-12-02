package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import org.xmlpull.v1.XmlPullParser
import tachiyomi.core.i18n.stringResource
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

object SettingsAppearanceScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        return listOf(
            getThemeGroup(context = context, uiPreferences = uiPreferences),
            getDisplayGroup(context = context, uiPreferences = uiPreferences),
        )
    }

    @Composable
    private fun getThemeGroup(
        context: Context,
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsState()

        val appThemePref = uiPreferences.appTheme()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        LaunchedEffect(themeMode) {
            setAppCompatDelegateThemeMode(themeMode)
        }

        LaunchedEffect(Unit) {
            merge(appThemePref.changes(), amoledPref.changes())
                .drop(2)
                .collectLatest { (context as? Activity)?.let { ActivityCompat.recreate(it) } }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = themeModePref,
                    title = stringResource(MR.strings.pref_theme_mode),
                    entries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mapOf(
                            ThemeMode.SYSTEM to stringResource(MR.strings.theme_system),
                            ThemeMode.LIGHT to stringResource(MR.strings.theme_light),
                            ThemeMode.DARK to stringResource(MR.strings.theme_dark),
                        )
                    } else {
                        mapOf(
                            ThemeMode.LIGHT to stringResource(MR.strings.theme_light),
                            ThemeMode.DARK to stringResource(MR.strings.theme_dark),
                        )
                    },
                ),
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) { item ->
                    val value by appThemePref.collectAsState()
                    AppThemePreferenceWidget(
                        title = item.title,
                        value = value,
                        amoled = amoled,
                        onItemClick = { appThemePref.set(it) },
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    pref = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        context: Context,
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val langs = remember { getLangs(context) }
        var currentLanguage by remember {
            mutableStateOf(
                AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "",
            )
        }
        val now = remember { Date().time }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        LaunchedEffect(currentLanguage) {
            val locale = if (currentLanguage.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(currentLanguage)
            }
            AppCompatDelegate.setApplicationLocales(locale)
        }

        val libraryPrefs = remember { Injekt.get<LibraryPreferences>() }

        LaunchedEffect(Unit) {
            libraryPrefs.bottomNavStyle().changes()
                .drop(1)
                .collectLatest { value ->
                    HomeScreen.tabs = when (value) {
                        0 -> HomeScreen.tabsNoHistory
                        1 -> HomeScreen.tabsNoUpdates
                        else -> HomeScreen.tabsNoManga
                    }
                    (context as? Activity)?.let {
                        ActivityCompat.recreate(it)
                    }
                }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPrefs.bottomNavStyle(),
                    title = stringResource(MR.strings.pref_bottom_nav_style),
                    entries = mapOf(
                        0 to stringResource(MR.strings.pref_bottom_nav_no_history),
                        1 to stringResource(MR.strings.pref_bottom_nav_no_updates),
                        2 to stringResource(MR.strings.pref_bottom_nav_no_manga),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPrefs.isDefaultHomeTabLibraryManga(),
                    title = stringResource(MR.strings.pref_default_home_tab_library),
                    enabled = libraryPrefs.bottomNavStyle().get() != 2,
                ),
                Preference.PreferenceItem.BasicListPreference(
                    value = currentLanguage,
                    title = stringResource(MR.strings.pref_app_language),
                    entries = langs,
                    onValueChanged = { newValue ->
                        currentLanguage = newValue
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.tabletUiMode(),
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    entries = TabletUiMode.entries.associateWith { stringResource(it.titleRes) },
                    onValueChanged = {
                        context.stringResource(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = uiPreferences.dateFormat(),
                    title = stringResource(MR.strings.pref_date_format),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
            ),
        )
    }
    private fun getLangs(context: Context): Map<String, String> {
        val langs = mutableListOf<Pair<String, String>>()
        val parser = context.resources.getXml(R.xml.locales_config)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                for (i in 0..<parser.attributeCount) {
                    if (parser.getAttributeName(i) == "name") {
                        val langTag = parser.getAttributeValue(i)
                        val displayName = LocaleHelper.getDisplayName(langTag)
                        if (displayName.isNotEmpty()) {
                            langs.add(Pair(langTag, displayName))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        langs.sortBy { it.second }
        langs.add(0, Pair("", context.stringResource(MR.strings.label_default)))

        return langs.toMap()
    }
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
