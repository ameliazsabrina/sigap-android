package fit.sigap.app.core.push

import com.google.firebase.messaging.FirebaseMessaging
import fit.sigap.app.core.data.toDomain
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.NativeRegistrationRequest
import fit.sigap.app.core.network.SigapApi
import fit.sigap.app.core.network.apiCall
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface PushTokenSource {
    suspend fun token(): String
}

object FirebasePushTokenSource : PushTokenSource {
    override suspend fun token(): String = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
            .addOnCanceledListener { continuation.cancel() }
    }

    suspend fun delete(): Unit = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().deleteToken()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
            .addOnCanceledListener { continuation.cancel() }
    }
}

data class PushStatus(val subscribed: Boolean, val confirmed: Boolean)

data class PushTestResult(val sent: Boolean, val reason: String?)

class PushRepository(private val api: SigapApi, private val tokens: PushTokenSource) {
    suspend fun register(): PushStatus {
        val token = try {
            tokens.token()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            throw ApiException(
                CODE_TOKEN_UNAVAILABLE,
                "HP ini belum mendapat pengenal push dari Google Play services. Periksa jaringan dan Google Play services, lalu coba lagi.",
            )
        }
        val row = apiCall { api.registerNativePush(NativeRegistrationRequest(token)) }.data
        return PushStatus(subscribed = true, confirmed = row.confirmed)
    }

    suspend fun status(): PushStatus = apiCall { api.pushStatus() }.data.let { PushStatus(it.subscribed, it.confirmed) }

    suspend fun sendTest(): PushTestResult = apiCall { api.sendPushTest() }.data.let { PushTestResult(it.sent, it.reason) }

    suspend fun confirmTest() {
        apiCall { api.confirmPushTest() }
    }

    suspend fun markReceived(notificationId: String) {
        apiCall { api.markNotificationReceived(notificationId) }
    }

    suspend fun alarmSettled(target: AlarmTarget): Boolean = try {
        when (target) {
            is AlarmTarget.IncidentTarget ->
                apiCall { api.incident(target.id) }.data.toDomain(currentUserId = "").silencesAlarm
            is AlarmTarget.EarlyAlertTarget ->
                apiCall { api.earlyAlert(target.id) }.data.toDomain().silencesAlarm
        }
    } catch (cause: ApiException) {
        if (cause.status == 404) true else throw cause
    }

    companion object {
        const val CODE_TOKEN_UNAVAILABLE = "PUSH_TOKEN_UNAVAILABLE"
    }
}
