package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionPreferences
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.util.BackupUtil
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.CustomAnimeInfo
import tachiyomi.domain.entries.manga.model.CustomMangaInfo
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
) {

    private var backupManager = BackupManager(context)

    private var restoreAmount = 0
    private var restoreProgress = 0

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()
    private var animeSourceMapping: Map<Long, String> = emptyMap()

    private val errors = mutableListOf<Pair<Date, String>>()

    suspend fun syncFromBackup(uri: Uri, sync: Boolean): Boolean {
        val startTime = System.currentTimeMillis()
        restoreProgress = 0
        errors.clear()

        if (!performRestore(uri, sync)) {
            return false
        }

        val endTime = System.currentTimeMillis()
        val time = endTime - startTime

        val logFile = writeErrorLog()

        if (sync) {
            notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name, contentTitle = context.getString(R.string.library_sync_complete))
        } else {
            notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name)
        }
        return true
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("kuukiyomi_restore.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun performRestore(uri: Uri, sync: Boolean): Boolean {
        val backup = BackupUtil.decodeBackup(context, uri)

        restoreAmount = backup.backupManga.size + backup.backupAnime.size + 2 // +2 for categories

        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        if (backup.backupAnimeCategories.isNotEmpty()) {
            restoreAnimeCategories(backup.backupAnimeCategories)
        }

        // Store source mapping for error messages
        val backupMaps = backup.backupBrokenSources.map { BackupSource(it.name, it.sourceId) } + backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        val backupAnimeMaps = backup.backupBrokenAnimeSources.map { BackupAnimeSource(it.name, it.sourceId) } + backup.backupAnimeSources
        animeSourceMapping = backupAnimeMaps.associate { it.sourceId to it.name }

        return coroutineScope {
            // Restore individual manga
            backup.backupManga.forEach {
                if (!isActive) {
                    return@coroutineScope false
                }

                restoreManga(it, backup.backupCategories, sync)
            }

            backup.backupAnime.forEach {
                if (!isActive) {
                    return@coroutineScope false
                }

                restoreAnime(it, backup.backupAnimeCategories, sync)
            }

            if (backup.backupPreferences.isNotEmpty()) {
                restorePreferences(
                    backup.backupPreferences,
                    PreferenceManager.getDefaultSharedPreferences(context),
                )
            }

            if (backup.backupExtensionPreferences.isNotEmpty()) {
                restoreExtensionPreferences(backup.backupExtensionPreferences)
            }

            if (backup.backupExtensions.isNotEmpty()) {
                restoreExtensions(backup.backupExtensions)
            }

            // TODO: optionally trigger online library + tracker update
            true
        }
    }

    private suspend fun restoreCategories(backupCategories: List<BackupCategory>) {
        backupManager.restoreCategories(backupCategories)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.manga_categories), context.getString(R.string.restoring_backup))
    }

    private suspend fun restoreAnimeCategories(backupCategories: List<BackupCategory>) {
        backupManager.restoreAnimeCategories(backupCategories)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.anime_categories), context.getString(R.string.restoring_backup))
    }

    private suspend fun restoreManga(backupManga: BackupManga, backupCategories: List<BackupCategory>, sync: Boolean) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories.map { it.toInt() }
        val history =
            backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead, it.readDuration) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()
        // SY -->
        val customManga = backupManga.getCustomMangaInfo()
        // SY <--

        try {
            val dbManga = backupManager.getMangaFromDatabase(manga.url, manga.source)
            if (dbManga == null) {
                // Manga not in database
                restoreExistingManga(manga, chapters, categories, history, tracks, backupCategories, customManga)
            } else {
                // Manga in database
                // Copy information from manga already in database
                val updateManga = backupManager.restoreExistingManga(manga, dbManga)
                // Fetch rest of manga information
                restoreNewManga(updateManga, chapters, categories, history, tracks, backupCategories, customManga)
            }
        } catch (e: Exception) {
            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }

        restoreProgress += 1
        if (sync) {
            showRestoreProgress(restoreProgress, restoreAmount, manga.title, context.getString(R.string.syncing_library))
        } else {
            showRestoreProgress(restoreProgress, restoreAmount, manga.title, context.getString(R.string.restoring_backup))
        }
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private suspend fun restoreExistingManga(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<MangaTrack>,
        backupCategories: List<BackupCategory>,
        // SY -->
        customManga: CustomMangaInfo?,
        // SY <--
    ) {
        val fetchedManga = backupManager.restoreNewManga(manga)
        backupManager.restoreChapters(fetchedManga, chapters)
        restoreExtras(fetchedManga, categories, history, tracks, backupCategories, customManga)
    }

    private suspend fun restoreNewManga(
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<MangaTrack>,
        backupCategories: List<BackupCategory>,
        // SY -->
        customManga: CustomMangaInfo?,
        // SY <--
    ) {
        backupManager.restoreChapters(backupManga, chapters)
        restoreExtras(backupManga, categories, history, tracks, backupCategories, customManga)
    }

    private suspend fun restoreExtras(
        manga: Manga,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<MangaTrack>,
        backupCategories: List<BackupCategory>,
        // SY -->
        customManga: CustomMangaInfo?,
        // SY <--
    ) {
        backupManager.restoreCategories(manga, categories, backupCategories)
        backupManager.restoreHistory(history)
        backupManager.restoreTracking(manga, tracks)
        // SY -->
        backupManager.restoreEditedMangaInfo(customManga?.copy(id = manga.id))
        // SY <--
    }

    private suspend fun restoreAnime(backupAnime: BackupAnime, backupCategories: List<BackupCategory>, sync: Boolean) {
        val anime = backupAnime.getAnimeImpl()
        val episodes = backupAnime.getEpisodesImpl()
        val categories = backupAnime.categories.map { it.toInt() }
        val history =
            backupAnime.brokenHistory.map { BackupAnimeHistory(it.url, it.lastSeen) } + backupAnime.history
        val tracks = backupAnime.getTrackingImpl()
        // SY -->
        val customAnime = backupAnime.getCustomAnimeInfo()
        // SY <--

        try {
            val dbAnime = backupManager.getAnimeFromDatabase(anime.url, anime.source)
            if (dbAnime == null) {
                // Anime not in database
                restoreExistingAnime(anime, episodes, categories, history, tracks, backupCategories, customAnime)
            } else {
                // Anime in database
                // Copy information from anime already in database
                val updateAnime = backupManager.restoreExistingAnime(anime, dbAnime)
                // Fetch rest of anime information
                restoreNewAnime(updateAnime, episodes, categories, history, tracks, backupCategories, customAnime)
            }
        } catch (e: Exception) {
            val sourceName = sourceMapping[anime.source] ?: anime.source.toString()
            errors.add(Date() to "${anime.title} [$sourceName]: ${e.message}")
        }

        restoreProgress += 1
        if (sync) {
            showRestoreProgress(restoreProgress, restoreAmount, anime.title, context.getString(R.string.syncing_library))
        } else {
            showRestoreProgress(restoreProgress, restoreAmount, anime.title, context.getString(R.string.restoring_backup))
        }
    }

    /**
     * Fetches anime information
     *
     * @param anime anime that needs updating
     * @param episodes episodes of anime that needs updating
     * @param categories categories that need updating
     */
    private suspend fun restoreExistingAnime(
        anime: Anime,
        episodes: List<Episode>,
        categories: List<Int>,
        history: List<BackupAnimeHistory>,
        tracks: List<AnimeTrack>,
        backupCategories: List<BackupCategory>,
        // SY -->
        customAnime: CustomAnimeInfo?,
        // SY <--
    ) {
        val fetchedAnime = backupManager.restoreNewAnime(anime)
        backupManager.restoreEpisodes(fetchedAnime, episodes)
        restoreExtras(fetchedAnime, categories, history, tracks, backupCategories, customAnime)
    }

    private suspend fun restoreNewAnime(
        backupAnime: Anime,
        episodes: List<Episode>,
        categories: List<Int>,
        history: List<BackupAnimeHistory>,
        tracks: List<AnimeTrack>,
        backupCategories: List<BackupCategory>,
        // SY -->
        customAnime: CustomAnimeInfo?,
        // SY <--
    ) {
        backupManager.restoreEpisodes(backupAnime, episodes)
        restoreExtras(backupAnime, categories, history, tracks, backupCategories, customAnime)
    }

    private suspend fun restoreExtras(
        anime: Anime,
        categories: List<Int>,
        history: List<BackupAnimeHistory>,
        tracks: List<AnimeTrack>,
        backupCategories: List<BackupCategory>,
        // SY -->
        customAnime: CustomAnimeInfo?,
        // SY <--
    ) {
        backupManager.restoreAnimeCategories(anime, categories, backupCategories)
        backupManager.restoreAnimeHistory(history)
        backupManager.restoreAnimeTracking(anime, tracks)
        // SY -->
        backupManager.restoreEditedAnimeInfo(customAnime?.copy(id = anime.id))
        // SY <--
    }

    private fun restorePreferences(preferences: List<BackupPreference>, sharedPrefs: SharedPreferences) {
        preferences.forEach { pref ->
            when (pref.value) {
                is IntPreferenceValue -> {
                    if (sharedPrefs.all[pref.key] is Int?) {
                        sharedPrefs.edit().putInt(pref.key, pref.value.value).apply()
                    }
                }
                is LongPreferenceValue -> {
                    if (sharedPrefs.all[pref.key] is Long?) {
                        sharedPrefs.edit().putLong(pref.key, pref.value.value).apply()
                    }
                }
                is FloatPreferenceValue -> {
                    if (sharedPrefs.all[pref.key] is Float?) {
                        sharedPrefs.edit().putFloat(pref.key, pref.value.value).apply()
                    }
                }
                is StringPreferenceValue -> {
                    if (sharedPrefs.all[pref.key] is String?) {
                        sharedPrefs.edit().putString(pref.key, pref.value.value).apply()
                    }
                }
                is BooleanPreferenceValue -> {
                    if (sharedPrefs.all[pref.key] is Boolean?) {
                        sharedPrefs.edit().putBoolean(pref.key, pref.value.value).apply()
                    }
                }
                is StringSetPreferenceValue -> {
                    if (sharedPrefs.all[pref.key] is Set<*>?) {
                        sharedPrefs.edit().putStringSet(pref.key, pref.value.value).apply()
                    }
                }
            }
        }
    }

    private fun restoreExtensionPreferences(prefs: List<BackupExtensionPreferences>) {
        prefs.forEach {
            val sharedPrefs = context.getSharedPreferences(it.name, 0x0)
            restorePreferences(it.prefs, sharedPrefs)
        }
    }

    private fun restoreExtensions(extensions: List<BackupExtension>) {
        extensions.forEach {
            if (context.packageManager.getInstalledPackages(0).none { pkg -> pkg.packageName == it.pkgName }) {
                logcat { it.pkgName }
                // save apk in files dir and open installer dialog
                val file = File(context.cacheDir, "${it.pkgName}.apk")
                file.writeBytes(it.apk)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(file.getUriCompat(context), "application/vnd.android.package-archive")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    /**
     * Called to update dialog in [BackupConst]
     *
     * @param progress restore progress
     * @param amount total restoreAmount of anime and manga
     * @param title title of restored anime and manga
     */
    private fun showRestoreProgress(progress: Int, amount: Int, title: String, contentTitle: String) {
        notifier.showRestoreProgress(title, contentTitle, progress, amount)
    }
}
