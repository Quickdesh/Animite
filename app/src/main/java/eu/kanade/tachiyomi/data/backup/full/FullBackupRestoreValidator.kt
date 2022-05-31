package eu.kanade.tachiyomi.data.backup.full

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.AnimeSourceManager
import eu.kanade.tachiyomi.data.backup.AbstractBackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import okio.buffer
import okio.gzip
import okio.source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FullBackupRestoreValidator : AbstractBackupRestoreValidator() {

    private val sourceManager: SourceManager = Injekt.get()
    private val animesourceManager: AnimeSourceManager = Injekt.get()
    private val trackManager: TrackManager = Injekt.get()

    /**
     * Checks for critical backup file data.
     *
     * @throws Exception if manga cannot be found.
     * @return List of missing sources or missing trackers.
     */
    override fun validate(context: Context, uri: Uri): Results {
        val backupManager = FullBackupManager(context)

        val backup = try {
            val backupString =
                context.contentResolver.openInputStream(uri)!!.source().gzip().buffer()
                    .use { it.readByteArray() }
            backupManager.parser.decodeFromByteArray(BackupSerializer, backupString)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        if (backup.backupAnime.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.invalid_backup_file_missing_manga))
        }

        val animesources = backup.backupAnimeSources.associate { it.sourceId to it.name }
        val missingSources = animesources
            .filter { animesourceManager.get(it.key) == null }
            .values
            .sorted()

        val trackers = backup.backupAnime
            .flatMap { it.tracking }
            .map { it.syncId }
            .distinct() + backup.backupAnime
            .flatMap { it.tracking }
            .map { it.syncId }
            .distinct()

        val missingTrackers = trackers
            .mapNotNull { trackManager.getService(it) }
            .filter { !it.isLogged }
            .map { context.getString(it.nameRes()) }
            .sorted()

        return Results(missingSources, missingTrackers)
    }
}
