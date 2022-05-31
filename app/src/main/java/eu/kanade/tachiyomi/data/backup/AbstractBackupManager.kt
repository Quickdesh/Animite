package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceManager
import eu.kanade.tachiyomi.animesource.model.toSEpisode
import eu.kanade.tachiyomi.data.animelib.CustomAnimeManager
import eu.kanade.tachiyomi.data.database.AnimeDatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.data.database.models.toAnimeInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.util.episode.syncEpisodesWithSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class AbstractBackupManager(protected val context: Context) {

    internal val animedb: AnimeDatabaseHelper = Injekt.get()
    internal val animesourceManager: AnimeSourceManager = Injekt.get()
    internal val trackManager: TrackManager = Injekt.get()
    protected val preferences: PreferencesHelper = Injekt.get()

    abstract fun createBackup(uri: Uri, flags: Int, isAutoBackup: Boolean): String

    /**
     * Returns anime
     *
     * @return [Anime], null if not found
     */
    internal fun getAnimeFromDatabase(anime: Anime): Anime? =
        animedb.getAnime(anime.url, anime.source).executeAsBlocking()

    /**
     * Fetches episode information.
     *
     * @param source source of anime
     * @param anime anime that needs updating
     * @param episodes list of episodes in the backup
     * @return Updated anime episodes.
     */
    internal suspend fun restoreEpisodes(source: AnimeSource, anime: Anime, episodes: List<Episode>): Pair<List<Episode>, List<Episode>> {
        val fetchedEpisodes = source.getEpisodeList(anime.toAnimeInfo())
            .map { it.toSEpisode() }
        val syncedEpisodes = syncEpisodesWithSource(animedb, fetchedEpisodes, anime, source)
        if (syncedEpisodes.first.isNotEmpty()) {
            episodes.forEach { it.anime_id = anime.id }
            updateEpisodes(episodes)
        }
        return syncedEpisodes
    }

    /**
     * Returns list containing anime from library
     *
     * @return [Anime] from library
     */
    protected fun getFavoriteAnime(): List<Anime> =
        animedb.getFavoriteAnimes().executeAsBlocking()

    /**
     * Inserts anime and returns id
     *
     * @return id of [Anime], null if not found
     */
    internal fun insertAnime(anime: Anime): Long? =
        animedb.insertAnime(anime).executeAsBlocking().insertedId()

    /**
     * Inserts list of episodes
     */
    protected fun insertEpisodes(episodes: List<Episode>) {
        animedb.insertEpisodes(episodes).executeAsBlocking()
    }

    /**
     * Updates a list of episodes
     */
    protected fun updateEpisodes(episodes: List<Episode>) {
        animedb.updateEpisodesBackup(episodes).executeAsBlocking()
    }

    /**
     * Updates a list of episodes with known database ids
     */
    protected fun updateKnownEpisodes(episodes: List<Episode>) {
        animedb.updateKnownEpisodesBackup(episodes).executeAsBlocking()
    }

    /**
     * Return number of backups.
     *
     * @return number of backups selected by user
     */
    protected fun numberOfBackups(): Int = preferences.numberOfBackups().get()
}
