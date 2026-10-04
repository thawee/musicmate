package apincer.android.mmate.ui.compose

import android.content.Context
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import apincer.android.mmate.ui.navigation.LibraryDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object DrawerInterop {
    private var drawerStateInstance: DrawerState? = null
    private var coroutineScopeInstance: CoroutineScope? = null
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
    @JvmOverloads
    fun getComposeView(
        context: Context,
        callbacks: MainScaffoldCallbacks? = null,
        initialLibraryDestination: LibraryDestination = LibraryDestination.ALL_SONGS,
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
                    initialLibraryDestination = initialLibraryDestination
                )
            }
        }
        return composeView
    }
}
