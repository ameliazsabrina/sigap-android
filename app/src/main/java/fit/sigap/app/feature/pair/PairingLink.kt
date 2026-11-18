package fit.sigap.app.feature.pair

import java.net.URI
import java.net.URISyntaxException

sealed interface ScannedPairing {
    data class Token(val value: String) : ScannedPairing

    data object CheckInCode : ScannedPairing

    data object NotSigap : ScannedPairing
}

private val TokenPattern = Regex("^[A-Za-z0-9_-]{16,128}$")
private const val CHECK_IN_PREFIX = "SIGAP-ABSEN:"

fun parsePairingQr(raw: String, allowedOrigin: String): ScannedPairing {
    val value = raw.trim()
    if (value.startsWith(CHECK_IN_PREFIX)) return ScannedPairing.CheckInCode
    val uri = try {
        URI(value)
    } catch (_: URISyntaxException) {
        return ScannedPairing.NotSigap
    }
    val allowed = URI(allowedOrigin)
    val sameOrigin = uri.scheme.equals(allowed.scheme, ignoreCase = true) &&
        uri.host.equals(allowed.host, ignoreCase = true) &&
        uri.port == allowed.port
    if (!sameOrigin) return ScannedPairing.NotSigap
    val token = uri.path?.removePrefix("/pair/")?.takeIf { uri.path.startsWith("/pair/") }
        ?: return ScannedPairing.NotSigap
    return if (TokenPattern.matches(token)) ScannedPairing.Token(token) else ScannedPairing.NotSigap
}
