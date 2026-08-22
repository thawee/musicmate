package apincer.android.mmate.ui.compose

import android.view.View
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import apincer.android.mmate.ui.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import apincer.android.mmate.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object DrawerInterop {
    private var drawerStateInstance: DrawerState? = null
    private var coroutineScopeInstance: CoroutineScope? = null
    private val activeItemIdState = mutableIntStateOf(R.id.menu_library_all_songs)

    @JvmStatic
    fun openDrawer() {
        coroutineScopeInstance?.launch {
            drawerStateInstance?.open()
        }
    }

    @JvmStatic
    fun closeDrawer() {
        coroutineScopeInstance?.launch {
            drawerStateInstance?.close()
        }
    }

    @JvmStatic
    fun isDrawerOpen(): Boolean {
        return drawerStateInstance?.isOpen == true
    }

    @JvmStatic
    fun updateActiveItem(itemId: Int) {
        activeItemIdState.intValue = itemId
    }

    @JvmStatic
    fun getComposeView(
        activity: MainActivity,
        legacyView: View
    ): ComposeView {
        val composeView = ComposeView(activity)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            MusicMateTheme {
                val state = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                SideEffect {
                    drawerStateInstance = state
                    coroutineScopeInstance = scope
                }

                MainScaffold(
                    activity = activity,
                    legacyView = legacyView,
                    drawerState = state,
                    activeItemId = activeItemIdState.intValue
                )
            }
        }
        return composeView
    }
}
