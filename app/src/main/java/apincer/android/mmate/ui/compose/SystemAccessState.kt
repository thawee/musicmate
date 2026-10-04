package apincer.android.mmate.ui.compose

enum class SystemAccessSummary {
    STORAGE_NEEDED,
    OPTIONAL_ACCESS_OFF,
    READY
}

enum class SystemAccessCapability {
    NONE,
    STORAGE,
    EXTERNAL_PLAYERS
}

enum class ExternalPlayerListenerAction {
    REGISTER,
    UNREGISTER,
    NONE
}

object ExternalPlayerAccessPolicy {
    @JvmStatic
    fun nextAction(isRegistered: Boolean, hasAccess: Boolean): ExternalPlayerListenerAction =
        when {
            hasAccess && !isRegistered -> ExternalPlayerListenerAction.REGISTER
            !hasAccess && isRegistered -> ExternalPlayerListenerAction.UNREGISTER
            else -> ExternalPlayerListenerAction.NONE
        }
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
