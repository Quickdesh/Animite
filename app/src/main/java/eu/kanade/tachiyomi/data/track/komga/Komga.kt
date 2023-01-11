package eu.kanade.tachiyomi.data.track.komga

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.MangaTrackService
import eu.kanade.tachiyomi.data.track.NoLoginTrackService
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source
import okhttp3.Dns
import okhttp3.OkHttpClient
import eu.kanade.domain.track.model.Track as DomainTrack

class Komga(private val context: Context, id: Long) : TrackService(id), EnhancedTrackService, NoLoginTrackService, MangaTrackService {

    companion object {
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
    }

    override val client: OkHttpClient =
        networkService.client.newBuilder()
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
            .build()

    val api by lazy { KomgaApi(client) }

    @StringRes
    override fun nameRes() = R.string.tracker_komga

    override fun getLogo() = R.drawable.ic_tracker_komga

    override fun getLogoColor() = Color.rgb(51, 37, 50)

    override fun getStatusList() = listOf(UNREAD, READING, COMPLETED)

    override fun getStatusListAnime() = listOf(UNREAD, READING, COMPLETED)

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            UNREAD -> getString(R.string.unread)
            READING -> getString(R.string.reading)
            COMPLETED -> getString(R.string.completed)
            else -> ""
        }
    }

    override fun getReadingStatus(): Int = READING

    override fun getWatchingStatus(): Int = throw Exception("Not used")

    override fun getRereadingStatus(): Int = -1

    override fun getRewatchingStatus(): Int = throw Exception("Not used")

    override fun getCompletionStatus(): Int = COMPLETED

    override fun getScoreList(): List<String> = emptyList()

    override fun displayScore(track: Track): String = ""
    override fun displayScore(track: AnimeTrack): String = throw Exception("Not used")

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = READING
                }
            }
        }

        return api.updateProgress(track)
    }

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack = throw Exception("Not used")

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        return track
    }

    override suspend fun bind(track: AnimeTrack, hasReadChapters: Boolean): AnimeTrack = throw Exception("Not used")

    override suspend fun search(query: String): List<TrackSearch> = throw Exception("Not used")

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = throw Exception("Not used")

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun refresh(track: AnimeTrack): AnimeTrack = throw Exception("Not used")

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    // TrackService.isLogged works by checking that credentials are saved.
    // By saving dummy, unused credentials, we can activate the tracker simply by login/logout
    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("eu.kanade.tachiyomi.extension.all.komga.Komga")

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override suspend fun match(anime: Anime) = throw Exception("Not used")
    
    override fun isTrackFrom(track: DomainTrack, manga: Manga, source: Source?): Boolean =
        track.remoteUrl == manga.url && source?.let { accept(it) } == true

    override fun migrateTrack(track: DomainTrack, manga: Manga, newSource: Source): DomainTrack? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }

    override fun isTrackFrom(track: DomainAnimeTrack, anime: DomainAnime, source: AnimeSource?): Boolean = false

    override fun migrateTrack(track: DomainAnimeTrack, anime: DomainAnime, newSource: AnimeSource) = throw Exception("Not used")
}
