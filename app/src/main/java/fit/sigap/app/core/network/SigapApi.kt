package fit.sigap.app.core.network

import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

private const val IDEMPOTENCY_KEY = "Idempotency-Key"

interface AuthApi {
    @POST("auth/pair")
    suspend fun pair(@Body body: PairRequest): Envelope<TokenResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Envelope<TokenResponse>

    @GET("auth/me")
    suspend fun me(@Header("Authorization") bearer: String): Envelope<UserDto>

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") bearer: String,
        @Body body: EmptyRequest = EmptyRequest(),
    ): Envelope<JsonElement?>
}

interface SigapApi {
    @GET("auth/push/status")
    suspend fun pushStatus(): Envelope<PushStatusDto>

    @POST("auth/push/native-registrations")
    suspend fun registerNativePush(@Body body: NativeRegistrationRequest): Envelope<PushRegistrationDto>

    @POST("auth/push/test")
    suspend fun sendPushTest(@Body body: EmptyRequest = EmptyRequest()): Envelope<PushTestDto>

    @POST("auth/push/test/confirm")
    suspend fun confirmPushTest(@Body body: EmptyRequest = EmptyRequest()): Envelope<JsonElement?>

    @POST("notifications/{id}/received")
    suspend fun markNotificationReceived(
        @Path("id") id: String,
        @Body body: EmptyRequest = EmptyRequest(),
    ): Envelope<JsonElement?>

    @GET("incidents/{id}")
    suspend fun incident(@Path("id") id: String): Envelope<IncidentDto>

    @GET("early-alerts/{id}")
    suspend fun earlyAlert(@Path("id") id: String): Envelope<EarlyAlertDto>

    @GET("shifts/me")
    suspend fun myShift(): Envelope<MyShiftDto>

    @POST("shifts/check-in")
    suspend fun checkIn(@Body body: CheckInRequest): Envelope<ShiftDto>

    @POST("shifts/check-out")
    suspend fun checkOut(@Body body: EmptyRequest = EmptyRequest()): Envelope<JsonElement?>

    @GET("settings/response")
    suspend fun responseSettings(): Envelope<List<ResponseSettingDto>>

    @GET("patient-simulations")
    suspend fun patients(): Envelope<List<PatientDto>>

    @GET("vital-observations")
    suspend fun vitals(@Query("patient_id") patientId: String, @Query("limit") limit: Int = 20): Envelope<List<VitalObservationDto>>

    @GET("incidents")
    suspend fun incidents(): Envelope<List<IncidentDto>>

    @POST("incidents/{id}/open")
    suspend fun openIncident(@Path("id") id: String, @Header(IDEMPOTENCY_KEY) key: String): Envelope<IncidentDto>

    @POST("incidents/{id}/acknowledge")
    suspend fun acknowledgeIncident(
        @Path("id") id: String,
        @Header(IDEMPOTENCY_KEY) key: String,
        @Body body: AcknowledgeRequest,
    ): Envelope<IncidentDto>

    @POST("incidents/{id}/arrive")
    suspend fun arriveIncident(@Path("id") id: String, @Header(IDEMPOTENCY_KEY) key: String): Envelope<IncidentDto>

    @POST("incidents/{id}/resolve")
    suspend fun resolveIncident(@Path("id") id: String, @Header(IDEMPOTENCY_KEY) key: String): Envelope<IncidentDto>

    @GET("early-alerts")
    suspend fun earlyAlerts(): Envelope<List<EarlyAlertDto>>

    @POST("early-alerts/{id}/check")
    suspend fun checkAlert(@Path("id") id: String, @Header(IDEMPOTENCY_KEY) key: String): Envelope<EarlyAlertDto>

    @POST("early-alerts/{id}/close")
    suspend fun closeAlert(
        @Path("id") id: String,
        @Header(IDEMPOTENCY_KEY) key: String,
        @Body body: ReasonRequest,
    ): Envelope<JsonElement?>

    @POST("early-alerts/{id}/escalate")
    suspend fun escalateAlert(
        @Path("id") id: String,
        @Header(IDEMPOTENCY_KEY) key: String,
        @Body body: ReasonRequest,
    ): Envelope<IncidentDto>

    @GET("incidents/{id}/documentation")
    suspend fun documentation(@Path("id") incidentId: String): Envelope<List<DocumentationDto>>

    @POST("incidents/{id}/documentation")
    suspend fun createDocumentation(
        @Path("id") incidentId: String,
        @Header(IDEMPOTENCY_KEY) key: String,
        @Body body: DocumentationRequest,
    ): Envelope<DocumentationDto>

    @PATCH("incidents/{id}/documentation/{documentationId}")
    suspend fun updateDocumentation(
        @Path("id") incidentId: String,
        @Path("documentationId") documentationId: String,
        @Body body: DocumentationRequest,
    ): Envelope<DocumentationDto>

    @POST("incidents/{id}/documentation/{documentationId}/complete")
    suspend fun completeDocumentation(
        @Path("id") incidentId: String,
        @Path("documentationId") documentationId: String,
        @Header(IDEMPOTENCY_KEY) key: String,
        @Body body: DocumentationRequest,
    ): Envelope<DocumentationDto>

    @GET("incidents/{id}/resus-log")
    suspend fun resusLog(@Path("id") incidentId: String, @Query("after_seq") afterSeq: Long?): Envelope<ResusLogDto>

    @POST("incidents/{id}/resus-log")
    suspend fun appendResusLog(@Path("id") incidentId: String, @Body body: ResusAppendRequest): Envelope<ResusLogDto>
}
