package fit.sigap.app.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

@Serializable
data class ErrorDetail(val field: String = "", val reason: String = "")

@Serializable
private data class ErrorBody(val error: Inner? = null) {
    @Serializable
    data class Inner(val code: String? = null, val message: String? = null, val details: List<ErrorDetail> = emptyList())
}

class ApiException(
    val code: String,
    override val message: String,
    val status: Int? = null,
    val details: List<ErrorDetail> = emptyList(),
) : Exception(message) {
    val isNetwork: Boolean get() = code == CODE_NETWORK

    companion object {
        const val CODE_NETWORK = "NETWORK_ERROR"
        const val CODE_VERSION_CONFLICT = "VERSION_CONFLICT"
        const val CODE_CONTRACT = "CONTRACT_ERROR"
    }
}

suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (cause: HttpException) {
    throw cause.toApiException()
} catch (_: IOException) {
    throw ApiException(ApiException.CODE_NETWORK, "Tidak dapat terhubung ke server. Periksa jaringan lalu coba lagi.")
} catch (_: SerializationException) {
    throw contractError()
}

fun contractError() = ApiException(ApiException.CODE_CONTRACT, "Jawaban server tidak dikenali. Perbarui aplikasi.")

fun HttpException.toApiException(): ApiException {
    val body = runCatching {
        response()?.errorBody()?.string()?.let { ApiJson.decodeFromString<ErrorBody>(it) }
    }.getOrNull()?.error
    return ApiException(
        code = body?.code ?: "INTERNAL_ERROR",
        message = body?.message ?: "Terjadi kesalahan pada server. Coba lagi.",
        status = code(),
        details = body?.details.orEmpty(),
    )
}
