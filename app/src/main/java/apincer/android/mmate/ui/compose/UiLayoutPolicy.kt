package apincer.android.mmate.ui.compose

/** Pure layout decisions shared by the UI and unit tests. */
object UiLayoutPolicy {
    private const val ExpandedNavigationMinWidthDp = 840
    private const val StackedChoicesMaxWidthDp = 359
    private const val LargeTextScale = 1.3f

    fun useExpandedNavigation(windowWidthDp: Int): Boolean =
        windowWidthDp >= ExpandedNavigationMinWidthDp

    fun useTwoColumnSettings(windowWidthDp: Int): Boolean =
        windowWidthDp >= ExpandedNavigationMinWidthDp

    fun useMusicCenterSupportingPane(windowWidthDp: Int): Boolean =
        windowWidthDp >= ExpandedNavigationMinWidthDp

    fun showQueueDuration(windowWidthDp: Int): Boolean =
        windowWidthDp >= 400

    fun smartQueueSourceLabel(sourceLabel: String, queueSize: Int, capacity: Int = 20): String =
        "$sourceLabel · ${queueSize.coerceIn(0, capacity)}/$capacity"

    fun stackChoiceControls(windowWidthDp: Int, fontScale: Float): Boolean =
        windowWidthDp <= StackedChoicesMaxWidthDp || fontScale >= LargeTextScale

    fun transportControlsEnabled(hasTrack: Boolean): Boolean = hasTrack

    fun playControlsEnabled(hasTrack: Boolean, canStartPlayback: Boolean = false): Boolean =
        hasTrack || canStartPlayback

    fun showFloatingDock(isRequested: Boolean, hasTrack: Boolean): Boolean =
        isRequested && hasTrack
}
