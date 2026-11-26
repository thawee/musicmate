package apincer.android.mmate.ui.compose

enum class SystemAccessSummary {
    STORAGE_NEEDED,
    READY
}

enum class SystemAccessCapability {
    NONE,
    STORAGE
}

data class SystemAccessState(
    val hasStorageAccess: Boolean = false
) {
    val summary: SystemAccessSummary
        get() = when {
            !hasStorageAccess -> SystemAccessSummary.STORAGE_NEEDED
            else -> SystemAccessSummary.READY
        }
}
