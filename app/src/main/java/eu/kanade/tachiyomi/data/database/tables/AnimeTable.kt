package eu.kanade.tachiyomi.data.database.tables

object AnimeTable {

    const val TABLE = "animes"

    const val COL_ID = "_id"

    const val COL_SOURCE = "source"

    const val COL_URL = "url"

    const val COL_ARTIST = "artist"

    const val COL_AUTHOR = "author"

    const val COL_DESCRIPTION = "description"

    const val COL_GENRE = "genre"

    const val COL_TITLE = "title"

    const val COL_STATUS = "status"

    const val COL_THUMBNAIL_URL = "thumbnail_url"

    const val COL_FAVORITE = "favorite"

    // Not actually used anymore
    const val COL_LAST_UPDATE = "last_update"

    const val COL_NEXT_UPDATE = "next_update"

    const val COL_DATE_ADDED = "date_added"

    const val COL_INITIALIZED = "initialized"

    const val COL_VIEWER = "viewer"

    const val COL_EPISODE_FLAGS = "episode_flags"

    const val COL_CATEGORY = "category"

    const val COL_COVER_LAST_MODIFIED = "cover_last_modified"

    // Not an actual value but computed when created
    const val COMPUTED_COL_UNSEEN_COUNT = "unseen_count"

    const val COMPUTED_COL_SEEN_COUNT = "seen_count"
}
