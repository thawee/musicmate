package apincer.android.mmate.ui.compose

import android.content.Context
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
        context: Context,
        callbacks: MainScaffoldCallbacks? = null
    ): ComposeView {
        val composeView = ComposeView(context)
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
                    drawerState = state,
                    callbacks = callbacks,
                    activeItemId = activeItemIdState.intValue
                )
            }
        }
        return composeView
    }
}
