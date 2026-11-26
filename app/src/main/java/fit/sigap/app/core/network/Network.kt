package fit.sigap.app.core.network

import fit.sigap.app.BuildConfig
import fit.sigap.app.core.session.RefreshFailedException
import fit.sigap.app.core.session.SessionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}

private val UserAgent = Interceptor { chain ->
    chain.proceed(
        chain.request().newBuilder()
            .header("User-Agent", "SIGAP-Android/${BuildConfig.VERSION_NAME}")
            .build(),
    )
}

private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .addInterceptor(UserAgent)

private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
    .build()

fun createAuthApi(baseUrl: String): AuthApi = retrofit(baseUrl, baseClient().build()).create(AuthApi::class.java)

fun createSigapApi(baseUrl: String, session: SessionRepository, clock: ServerClock? = null): SigapApi {
    val client = baseClient()
        .apply { if (clock != null) addNetworkInterceptor(clock.interceptor) }
        .addInterceptor { chain ->
            val token = session.accessToken
            val request = if (token != null) chain.request().withBearer(token) else chain.request()
            chain.proceed(request)
        }
        .authenticator(RefreshAuthenticator(session))
        .build()
    return retrofit(baseUrl, client).create(SigapApi::class.java)
}

private fun Request.withBearer(token: String): Request = newBuilder().header("Authorization", "Bearer $token").build()

internal class RefreshAuthenticator(private val session: SessionRepository) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = try {
            runBlocking { session.refreshAfterUnauthorized(failedToken) }
        } catch (cause: RefreshFailedException) {
            throw IOException(cause)
        } ?: return null
        return response.request.withBearer(fresh)
    }
}
