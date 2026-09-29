package apincer.android.mmate.ui.compose

enum class SystemAccessSummary {
    STORAGE_NEEDED,
    OPTIONAL_ACCESS_OFF,
    READY
}

data class SystemAccessState(
    val hasFullStorageAccess: Boolean = false,
    val hasExternalPlayerAccess: Boolean = false
) {
    val summary: SystemAccessSummary
        get() = when {
            !hasFullStorageAccess -> SystemAccessSummary.STORAGE_NEEDED
            !hasExternalPlayerAccess -> SystemAccessSummary.OPTIONAL_ACCESS_OFF
            else -> SystemAccessSummary.READY
        }
}
