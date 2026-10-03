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

    /** Narrow screens and large text use the single combined badge, so duration stays on the badge line. */
    fun compactTrackBadges(screenWidthDp: Int, fontScale: Float): Boolean =
        screenWidthDp < 390 || fontScale >= LargeTextScale

    /** Large text leaves no room for the track count beside a labeled Play pill. */
    fun iconOnlyPlayResultsPills(fontScale: Float): Boolean =
        fontScale >= LargeTextScale

    fun stackChoiceControls(windowWidthDp: Int, fontScale: Float): Boolean =
        windowWidthDp <= StackedChoicesMaxWidthDp || fontScale >= LargeTextScale

    fun transportControlsEnabled(hasTrack: Boolean): Boolean = hasTrack

    fun playControlsEnabled(hasTrack: Boolean, canStartPlayback: Boolean = false): Boolean =
        hasTrack || canStartPlayback

    fun showFloatingDock(isRequested: Boolean, hasTrack: Boolean): Boolean =
        isRequested && hasTrack
}
