package fit.sigap.app.core.session

import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.AuthApi
import fit.sigap.app.core.network.PairRequest
import fit.sigap.app.core.network.RefreshRequest
import fit.sigap.app.core.network.TokenResponse
import fit.sigap.app.core.network.UserDto
import fit.sigap.app.core.network.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionUser(val id: String, val name: String, val role: DutyRole)

sealed interface SessionState {
    data object Loading : SessionState

    data class None(val ended: Boolean) : SessionState

    data class Active(val user: SessionUser) : SessionState

    data class Unreachable(val message: String) : SessionState
}

class RefreshFailedException(cause: Throwable) : Exception(cause)

class SessionRepository(private val authApi: AuthApi, private val store: TokenStore) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    var accessToken: String? = null
        private set

    private val refreshLock = Mutex()

    suspend fun hasStoredSession(): Boolean = store.readRefreshToken() != null

    suspend fun restore() {
        _state.value = SessionState.Loading
        if (store.readRefreshToken() == null) {
            _state.value = SessionState.None(ended = store.readSessionEnded())
            return
        }
        try {
            val token = refreshAfterUnauthorized(failedToken = accessToken) ?: return
            _state.value = SessionState.Active(apiCall { authApi.me(bearer(token)) }.data.toSessionUser())
        } catch (cause: RefreshFailedException) {
            _state.value = SessionState.Unreachable(unreachableMessage(cause.cause))
        } catch (cause: ApiException) {
            _state.value = SessionState.Unreachable(cause.message)
        }
    }

    suspend fun pair(pairingToken: String): SessionUser {
        val tokens = apiCall { authApi.pair(PairRequest(pairingToken)) }.data
        store.writeRefreshToken(tokens.refreshToken)
        store.writeSessionEnded(false)
        accessToken = tokens.accessToken
        val user = try {
            apiCall { authApi.me(bearer(tokens.accessToken)) }.data.toSessionUser()
        } catch (_: ApiException) {
            tokens.user.toSessionUser()
        }
        _state.value = SessionState.Active(user)
        return user
    }

    suspend fun logout() {
        val token = accessToken ?: tokenAfterRefresh(failedToken = null)
        if (token != null && !revoke(token)) {
            tokenAfterRefresh(failedToken = token)?.let { revoke(it) }
        }
        accessToken = null
        store.clearRefreshToken()
        store.writeSessionEnded(false)
        _state.value = SessionState.None(ended = false)
    }

    private suspend fun revoke(token: String): Boolean = try {
        apiCall { authApi.logout(bearer(token)) }
        true
    } catch (cause: ApiException) {
        if (cause.status != 401) throw cause
        false
    }

    private suspend fun tokenAfterRefresh(failedToken: String?): String? = try {
        refreshAfterUnauthorized(failedToken)
    } catch (cause: RefreshFailedException) {
        throw cause.cause as? ApiException ?: ApiException(ApiException.CODE_NETWORK, unreachableMessage(cause.cause))
    }

    suspend fun refreshAfterUnauthorized(failedToken: String?): String? = refreshLock.withLock {
        val current = accessToken
        if (current != null && current != failedToken) return@withLock current

        val refreshToken = store.readRefreshToken()
        if (refreshToken == null) {
            if (_state.value !is SessionState.None) endSession()
            return@withLock null
        }
        val tokens: TokenResponse = try {
            apiCall { authApi.refresh(RefreshRequest(refreshToken)) }.data
        } catch (cause: ApiException) {
            if (cause.status == 401 || cause.status == 403) {
                endSession()
                return@withLock null
            }
            throw RefreshFailedException(cause)
        }
        store.writeRefreshToken(tokens.refreshToken)
        accessToken = tokens.accessToken
        tokens.accessToken
    }

    private suspend fun endSession() {
        accessToken = null
        store.clearRefreshToken()
        store.writeSessionEnded(true)
        _state.value = SessionState.None(ended = true)
    }

    private fun bearer(token: String) = "Bearer $token"

    private fun unreachableMessage(cause: Throwable?): String =
        (cause as? ApiException)?.message ?: "Tidak dapat terhubung ke server. Periksa jaringan lalu coba lagi."
}

private fun UserDto.toSessionUser() = SessionUser(
    id = id,
    name = fullName,
    role = if ("HEALTHCARE_WORKER" in roles) DutyRole.NURSE else DutyRole.CODE_BLUE_TEAM,
)
