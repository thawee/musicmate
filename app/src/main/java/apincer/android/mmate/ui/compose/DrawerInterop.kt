package apincer.android.mmate.ui.compose

import android.view.View
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import apincer.android.mmate.ui.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

object DrawerInterop {
    private val openDrawerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @JvmStatic
    fun openDrawer() {
        openDrawerFlow.tryEmit(Unit)
    }

    @JvmStatic
    fun getComposeView(
        activity: MainActivity,
        legacyView: View
    ): ComposeView {
        val composeView = ComposeView(activity)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            MaterialTheme {
                val state = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
                
                LaunchedEffect(Unit) {
                    openDrawerFlow.collect {
                        state.open()
                    }
                }

                MainScaffold(
                    activity = activity,
                    legacyView = legacyView,
                    drawerState = state
                )
            }
        }
        return composeView
    }
}
