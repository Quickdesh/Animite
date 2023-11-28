package tachiyomi.domain.storage.service

import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.provider.FolderProvider

class StoragePreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {

    fun baseStorageDirectory() = preferenceStore.getString("storage_dir", folderProvider.path())

    companion object {
        const val BACKUP_DIR = "backup"
        const val DOWNLOADS_DIR = "manga_downloads"
        const val DOWNLOADS_ANIME_DIR = "anime_downloads"
    }
}
