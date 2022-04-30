package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import rx.Observable

open class SourceManager(private val context: Context) {

    private val sourcesMap = mutableMapOf<Long, Source>()
    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    private val _catalogueSources: MutableStateFlow<List<CatalogueSource>> = MutableStateFlow(listOf())
    val catalogueSources: Flow<List<CatalogueSource>> = _catalogueSources
    val onlineSources: Flow<List<HttpSource>> =
        _catalogueSources.map { sources -> sources.filterIsInstance<HttpSource>() }

    init {
        createInternalSources().forEach { registerSource(it) }
    }

    open fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            StubSource(sourceKey)
        }
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    internal fun registerSource(source: Source) {
        if (!sourcesMap.containsKey(source.id)) {
            sourcesMap[source.id] = source
        }
        if (!stubSourcesMap.containsKey(source.id)) {
            stubSourcesMap[source.id] = StubSource(source.id)
        }
        triggerCatalogueSources()
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
        triggerCatalogueSources()
    }

    private fun triggerCatalogueSources() {
        _catalogueSources.update {
            sourcesMap.values.filterIsInstance<CatalogueSource>()
        }
    }

    private fun createInternalSources(): List<Source> = listOf(
        LocalSource(context),
    )

    inner class StubSource(override val id: Long) : Source {

        override val name: String
            get() = id.toString()

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            return name
        }

        private fun getSourceNotInstalledException(): SourceNotInstalledException {
            return SourceNotInstalledException(id)
        }
    }

    inner class SourceNotInstalledException(val id: Long) :
        Exception(context.getString(R.string.source_not_installed, id.toString()))
}