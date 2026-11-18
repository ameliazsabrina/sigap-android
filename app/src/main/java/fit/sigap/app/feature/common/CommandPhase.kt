package fit.sigap.app.feature.common

sealed interface CommandPhase {
    data object Idle : CommandPhase
    data object Opening : CommandPhase
    data object Sending : CommandPhase
    data class Failed(val message: String) : CommandPhase

    val isBusy: Boolean get() = this is Opening || this is Sending
}
