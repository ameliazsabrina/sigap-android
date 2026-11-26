package fit.sigap.app.core.network

import okhttp3.Interceptor
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ServerClock(private val phoneMillis: () -> Long = System::currentTimeMillis) {
    @Volatile
    var offsetMs: Long = 0
        private set

    @Volatile
    var synced: Boolean = false
        private set

    fun now(): Instant = Instant.ofEpochMilli(phoneMillis() + offsetMs)

    fun observe(serverDateMillis: Long, sentAtMillis: Long, receivedAtMillis: Long) {
        val midpoint = sentAtMillis + (receivedAtMillis - sentAtMillis) / 2
        offsetMs = serverDateMillis + HALF_SECOND - midpoint
        synced = true
    }

    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        response.header("Date")?.let(::parseHttpDate)?.let {
            observe(it, response.sentRequestAtMillis, response.receivedResponseAtMillis)
        }
        response
    }

    private fun parseHttpDate(value: String): Long? = try {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        const val HALF_SECOND = 500L
    }
}
