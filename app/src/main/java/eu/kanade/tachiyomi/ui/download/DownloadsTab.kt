package eu.kanade.tachiyomi.ui.download

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.permissions.PermissionRequestHelper
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.download.anime.animeDownloadTab
import eu.kanade.tachiyomi.ui.download.manga.mangaDownloadTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data class DownloadsTab(
    private val isManga: Boolean = false,
) : Tab() {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 6u,
                title = stringResource(MR.strings.label_download_queue),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        TabbedScreen(
            titleRes = MR.strings.label_download_queue,
            tabs = persistentListOf(
                animeDownloadTab(),
                mangaDownloadTab(),
            ),
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }

        // For local source
        PermissionRequestHelper.requestStoragePermission()
    }
}
