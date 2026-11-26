package fit.sigap.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fit.sigap.app.core.designsystem.component.BackLink
import fit.sigap.app.core.designsystem.component.ErrorState
import fit.sigap.app.core.designsystem.component.LoadingState
import fit.sigap.app.core.designsystem.component.SigapLogo
import fit.sigap.app.core.device.batteryExemptionIntent
import fit.sigap.app.core.device.fullScreenSettingsIntent
import fit.sigap.app.core.device.notificationSettingsIntent
import fit.sigap.app.core.device.openSettings
import fit.sigap.app.core.format.formatDateTime
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.IncidentStatus
import fit.sigap.app.core.model.PatientVitals
import fit.sigap.app.core.model.ResusAction
import fit.sigap.app.core.model.CodeBlueForm
import fit.sigap.app.core.model.ResusSheet
import fit.sigap.app.core.model.draftFromResusLog
import fit.sigap.app.core.model.toSheet
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import fit.sigap.app.core.model.Rhythm
import fit.sigap.app.core.model.activeSorted
import fit.sigap.app.core.model.firstWaiting
import fit.sigap.app.core.model.openSorted
import fit.sigap.app.core.model.outsideActiveCodeBlue
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.session.SessionState
import fit.sigap.app.core.session.SessionUser
import fit.sigap.app.feature.account.AccountScreen
import fit.sigap.app.feature.alert.AlertActions
import fit.sigap.app.feature.alert.AlertScreen
import fit.sigap.app.feature.alert.AlertUiState
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.feature.common.DutyTab
import fit.sigap.app.feature.common.rememberNow
import fit.sigap.app.feature.documentation.DocumentationActions
import fit.sigap.app.feature.documentation.DocumentationScreen
import fit.sigap.app.feature.documentation.DocumentationUiState
import fit.sigap.app.feature.documentation.DocumentationViewModel
import fit.sigap.app.feature.duty.DutyState
import fit.sigap.app.feature.duty.DutyViewModel
import fit.sigap.app.feature.duty.StartDutyScreen
import fit.sigap.app.feature.incident.IncidentScreen
import fit.sigap.app.feature.pair.PairRoute
import fit.sigap.app.feature.pair.PairViewModel
import fit.sigap.app.feature.readiness.PushTestUiState
import fit.sigap.app.feature.readiness.ReadinessScreen
import fit.sigap.app.feature.resus.ResusAccess
import fit.sigap.app.feature.resus.ResusFinish
import fit.sigap.app.feature.resus.ResusLogActions
import fit.sigap.app.feature.resus.ResusLogScreen
import fit.sigap.app.feature.resus.ResusLogUiState
import fit.sigap.app.feature.resus.ResusLogViewModel
import fit.sigap.app.feature.standby.NurseStandbyActions
import fit.sigap.app.feature.standby.NurseStandbyScreen
import fit.sigap.app.feature.standby.NurseStandbyUiState
import fit.sigap.app.feature.standby.TeamStandbyActions
import fit.sigap.app.feature.standby.TeamStandbyScreen
import fit.sigap.app.feature.standby.TeamStandbyUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable private data object StandbyRoute
@Serializable private data object AccountRoute
@Serializable private data object ReadinessRoute
@Serializable private data class IncidentRoute(val id: String)
@Serializable private data class DocumentationRoute(val id: String)
@Serializable private data class AlertRoute(val id: String)
@Serializable private data class ResusLogRoute(val id: String)

private const val VITALS_POLL_MS = 10_000L
private const val RESUS_TICK_MS = 1_000L

@Composable
fun AppRoot(container: AppContainer, debugPairingQr: String? = null) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val pairViewModel: PairViewModel = viewModel(
        factory = factoryOf { PairViewModel(container.session, BuildConfig.PAIR_ORIGIN) },
    )
    val pair by pairViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { container.session.restore() }
    var pendingDebugQr by rememberSaveable { mutableStateOf(debugPairingQr) }
    LaunchedEffect(session) {
        val qr = pendingDebugQr
        if (session is SessionState.None && qr != null) {
            pendingDebugQr = null
            pairViewModel.onScanned(qr)
        }
    }

    when (val current = session) {
        SessionState.Loading -> AppScreen {
            SigapLogo(withName = true)
            LoadingState(label = "Memeriksa sesi HP ini")
        }
        is SessionState.None -> PairRoute(pairViewModel)
        is SessionState.Unreachable -> AppScreen {
            SigapLogo(withName = true)
            ErrorState(current.message, onRetry = { scope.launch { container.session.restore() } })
        }
        is SessionState.Active ->
            if (pair.inSetup) {
                PairRoute(pairViewModel)
            } else {
                key(current.user.id) { DutyNavigation(container, current.user) }
            }
    }
}

@Composable
private fun DutyNavigation(container: AppContainer, user: SessionUser) {
    val context = LocalContext.current
    val viewModel: DutyViewModel = viewModel(
        key = "duty-${user.id}",
        factory = factoryOf {
            DutyViewModel(
                user = user,
                repository = container.duty,
                push = container.push,
                registerPush = container.pushRegistrar::register,
                cancelSettledAlarms = container::settleAlarms,
                logout = container::signOut,
                readReadiness = container.readiness::read,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val now by rememberNow()

    LifecycleStartEffect(viewModel) {
        viewModel.startPolling()
        onStopOrDispose { viewModel.stopPolling() }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshReadiness()
        onPauseOrDispose {}
    }

    if (!state.shiftKnown) {
        AppScreen {
            SigapLogo(withName = true)
            val error = state.shiftError
            if (error != null) ErrorState(error, onRetry = viewModel::retry) else LoadingState(label = "Memeriksa status tugas")
        }
        return
    }

    NavHost(navController = nav, startDestination = StandbyRoute) {
        composable<StandbyRoute> {
            if (state.shift == null) {
                StartDuty(user, state, viewModel)
            } else if (user.role == DutyRole.NURSE) {
                NurseStandby(user, state, now, viewModel, nav)
            } else {
                TeamStandby(user, state, now, viewModel, nav)
            }
        }
        composable<AccountRoute> {
            val shift = state.shift
            if (shift != null) {
                AccountScreen(
                    userName = user.name,
                    role = user.role,
                    shift = shift,
                    uncheckedAlerts = if (user.role == DutyRole.NURSE) state.alerts.count { it.isUnchecked } else 0,
                    phase = state.dutyPhase,
                    onFinish = viewModel::finishDuty,
                    onOpenReadiness = { nav.navigate(ReadinessRoute) },
                    onSelectTab = { tab -> selectTab(nav, tab) },
                )
            } else {
                StartDuty(user, state, viewModel)
            }
        }
        composable<ReadinessRoute> {
            ReadinessScreen(
                readiness = state.readiness,
                pushTest = PushTestUiState(phase = state.pushTestPhase, sent = state.pushTestSent),
                onSendPushTest = viewModel::sendPushTest,
                onConfirmPushTest = viewModel::confirmPushTest,
                onBack = nav::popBackStack,
                onFixNotifications = { context.openSettings(context.notificationSettingsIntent()) },
                onFixBattery = { context.openSettings(context.batteryExemptionIntent()) },
                onFixFullScreen = { context.openSettings(context.fullScreenSettingsIntent()) },
                onFixVolume = { context.openSettings(Intent(Settings.ACTION_SOUND_SETTINGS)) },
            )
        }
        composable<IncidentRoute> { entry ->
            val id = entry.toRoute<IncidentRoute>().id
            val incident = state.incidents.firstOrNull { it.id == id }
            if (incident == null) {
                NotFound("Incident", state, nav)
            } else {
                IncidentScreen(
                    incident = incident,
                    now = now,
                    overdue = incident.isArrivalOverdue(now, state.arrivalLimit),
                    canRespond = user.role == DutyRole.CODE_BLUE_TEAM,
                    phase = state.phases[id] ?: CommandPhase.Idle,
                    onBack = nav::popBackStack,
                    onAcknowledge = { viewModel.acknowledge(id) },
                    onArrive = { viewModel.arrive(id) },
                    onResolve = { viewModel.resolve(id) },
                    onDocument = { nav.navigate(DocumentationRoute(id)) },
                    onOpenResusLog = if (BuildConfig.RESUS_LOG_ENABLED && incident.isAcknowledged) {
                        { nav.navigate(ResusLogRoute(id)) }
                    } else {
                        null
                    },
                )
            }
        }
        composable<ResusLogRoute> { entry ->
            val id = entry.toRoute<ResusLogRoute>().id
            val incident = state.incidents.firstOrNull { it.id == id }
            if (incident == null) {
                NotFound("Incident", state, nav)
            } else {
                ResusLog(container, user, incident, state, viewModel, nav)
            }
        }
        composable<DocumentationRoute> { entry ->
            val id = entry.toRoute<DocumentationRoute>().id
            val incident = state.incidents.firstOrNull { it.id == id }
            if (incident == null) {
                NotFound("Incident", state, nav)
            } else {
                val documentation: DocumentationViewModel = viewModel(
                    factory = factoryOf { DocumentationViewModel(id, container.documentation, viewModel::retry) },
                )
                val doc by documentation.state.collectAsStateWithLifecycle()
                val logSheet by produceState<LogSheet>(if (BuildConfig.RESUS_LOG_ENABLED) LogSheet.Loading else LogSheet.Ready(null), id) {
                    if (value is LogSheet.Ready) return@produceState
                    try {
                        container.resusLog.refresh(id)
                    } catch (_: ApiException) {
                    }
                    value = LogSheet.Ready(container.resusLog.observe(id).first().entries.toSheet())
                }
                val loadError = doc.loadError
                when {
                    doc.loading || logSheet == LogSheet.Loading -> AppScreen {
                        BackLink("Incident", nav::popBackStack)
                        LoadingState(label = "Memuat dokumentasi")
                    }
                    loadError != null -> AppScreen {
                        BackLink("Incident", nav::popBackStack)
                        ErrorState(loadError, onRetry = documentation::load)
                    }
                    else -> key(doc.generation) {
                        val latest = doc.latest
                        val sheet = (logSheet as? LogSheet.Ready)?.sheet
                        val drafted = latest == null && sheet != null &&
                            CodeBlueForm().draftFromResusLog(sheet, ZoneId.systemDefault()) != CodeBlueForm()
                        DocumentationScreen(
                            state = DocumentationUiState(
                                incident = incident,
                                statusLine = when {
                                    latest?.completed == true -> latest.completedAt
                                        ?.let { "Dokumentasi selesai · ${formatDateTime(it)}" }
                                        ?: "Dokumentasi selesai"
                                    latest != null -> "Draft"
                                    else -> "Belum dimulai"
                                },
                                canWrite = latest?.completed != true && !incident.isClosed,
                                pending = doc.pending,
                                message = doc.message,
                                error = doc.error,
                                versionConflict = doc.conflict,
                                draftedFromLog = drafted,
                            ),
                            initialValues = latest?.values ?: DocumentationValues().let { empty ->
                                if (sheet == null) empty else empty.copy(form = empty.form.draftFromResusLog(sheet, ZoneId.systemDefault()))
                            },
                            actions = object : DocumentationActions {
                                override fun onBack() {
                                    nav.popBackStack()
                                }
                                override fun onSave(values: DocumentationValues) = documentation.save(values)
                                override fun onComplete(values: DocumentationValues) = documentation.complete(values)
                                override fun onReloadAfterConflict() = documentation.reloadAfterConflict()
                                override fun onOverwriteAfterConflict(values: DocumentationValues) =
                                    documentation.overwriteAfterConflict(values)
                            },
                        )
                    }
                }
            }
        }
        composable<AlertRoute> { entry ->
            val id = entry.toRoute<AlertRoute>().id
            val alert = state.alerts.firstOrNull { it.id == id }
            if (alert == null) {
                NotFound("Alert", state, nav)
            } else {
                val patientId = alert.patient.id
                val vitals by produceState(PatientVitals(emptyList()), patientId) {
                    while (true) {
                        try {
                            value = viewModel.vitals(patientId)
                        } catch (_: ApiException) {
                        }
                        delay(VITALS_POLL_MS)
                    }
                }
                AlertScreen(
                    state = AlertUiState(
                        alert = alert,
                        incident = state.incidents
                            .filter { it.patient.id == alert.patient.id && it.createdAt >= alert.createdAt }
                            .maxByOrNull { it.createdAt },
                        vitals = vitals,
                        canDecide = user.role == DutyRole.NURSE,
                        phase = state.phases[id] ?: CommandPhase.Idle,
                    ),
                    now = now,
                    actions = object : AlertActions {
                        override fun onBack() {
                            nav.popBackStack()
                        }
                        override fun onCheck() = viewModel.check(id)
                        override fun onClose(reason: String) = viewModel.close(id, reason)
                        override fun onEscalate(reason: String) = viewModel.escalate(id, reason)
                    },
                )
            }
        }
    }
}

@Composable
private fun ResusLog(
    container: AppContainer,
    user: SessionUser,
    incident: Incident,
    state: DutyState,
    duty: DutyViewModel,
    nav: NavHostController,
) {
    val log: ResusLogViewModel = viewModel(
        factory = factoryOf { ResusLogViewModel(incident.id, user, container.resusLog, container.serverClock::now) },
    )
    val logState by log.state.collectAsStateWithLifecycle()
    val outboxError by container.resusOutbox.lastError.collectAsStateWithLifecycle()
    val now by produceState(container.serverClock.now()) {
        while (true) {
            delay(RESUS_TICK_MS)
            value = container.serverClock.now()
        }
    }
    LifecycleStartEffect(log) {
        log.startPolling()
        onStopOrDispose { log.stopPolling() }
    }
    val team = user.role == DutyRole.CODE_BLUE_TEAM
    ResusLogScreen(
        state = ResusLogUiState(
            locationName = incident.location.name,
            t0 = incident.createdAt,
            log = logState,
            access = resusAccess(team, incident),
            outboxError = outboxError,
            canMarkArrived = team && incident.status == IncidentStatus.ACKNOWLEDGED && incident.arrivedAt == null,
            arrivePhase = state.phases[incident.id] ?: CommandPhase.Idle,
            finish = if (team) resusFinish(incident) else null,
            resolvePhase = state.phases[incident.id] ?: CommandPhase.Idle,
        ),
        now = now,
        actions = object : ResusLogActions {
            override fun onBack() {
                nav.popBackStack()
            }
            override fun onRhythm(rhythm: Rhythm) = log.recordRhythm(rhythm)
            override fun onRecord(action: ResusAction) = log.record(action)
            override fun onConfirmPrompt() = log.confirmPrompt()
            override fun onDismissPrompt() = log.dismissPrompt()
            override fun onUndo() = log.undo()
            override fun onRequestVoid(entryId: String) = log.requestVoid(entryId)
            override fun onArrive() = duty.arrive(incident.id)
            override fun onResolve() = duty.resolve(incident.id)
            override fun onDocument() {
                nav.navigate(DocumentationRoute(incident.id))
            }
            override fun onRetry() = log.retry()
        },
    )
}

private sealed interface LogSheet {
    data object Loading : LogSheet

    data class Ready(val sheet: ResusSheet?) : LogSheet
}

private fun resusFinish(incident: Incident): ResusFinish? = when {
    incident.isClosed -> ResusFinish.VIEW_DOCUMENTATION
    incident.awaitsDocumentation -> ResusFinish.DOCUMENT
    incident.status != IncidentStatus.ACKNOWLEDGED -> null
    incident.arrivedAt == null -> ResusFinish.ARRIVE_FIRST
    else -> ResusFinish.RESOLVE
}

private fun resusAccess(team: Boolean, incident: Incident): ResusAccess = when {
    !team -> ResusAccess.ReadOnly("Hak perawat menulis log belum ditetapkan. Log hanya dapat dibaca.")
    incident.status == IncidentStatus.ACKNOWLEDGED -> ResusAccess.Write
    incident.isHandled -> ResusAccess.ReadOnly("Log ditutup karena respons sudah dinyatakan selesai.")
    else -> ResusAccess.ReadOnly("Log dapat ditulis setelah ada yang menuju lokasi.")
}

@Composable
private fun StartDuty(user: SessionUser, state: DutyState, viewModel: DutyViewModel) {
    StartDutyScreen(
        userName = user.name,
        role = user.role,
        phase = state.dutyPhase,
        onSubmitCode = viewModel::checkIn,
        onClearError = viewModel::clearDutyError,
        signOutPhase = state.signOutPhase,
        onSignOut = viewModel::signOut,
        onDismissSignOut = viewModel::clearSignOutError,
    )
}

@Composable
private fun NotFound(subject: String, state: DutyState, nav: NavHostController) {
    AppScreen {
        BackLink("Kembali", nav::popBackStack)
        if (state.loaded) {
            Text("$subject tidak ditemukan.")
        } else {
            LoadingState(label = "Memuat data")
        }
    }
}

@Composable
private fun TeamStandby(user: SessionUser, state: DutyState, now: Instant, viewModel: DutyViewModel, nav: NavHostController) {
    val shift = state.shift ?: return
    val active = state.incidents.activeSorted()
    TeamStandbyScreen(
        state = TeamStandbyUiState(
            userName = user.name,
            shift = shift,
            connection = state.connection,
            readiness = state.readiness,
            incidents = active,
            awaitingDocumentation = state.incidents.filter { it.awaitsDocumentation },
            alerts = state.alerts.openSorted().outsideActiveCodeBlue(state.incidents),
            overdueIds = state.incidents.filter { it.isArrivalOverdue(now, state.arrivalLimit) }.map { it.id }.toSet(),
            phases = state.phases,
            loading = !state.loaded && state.error == null,
            error = state.error.takeUnless { state.loaded },
        ),
        now = now,
        actions = object : TeamStandbyActions {
            override fun onOpenIncident(id: String) = nav.navigate(IncidentRoute(id))
            override fun onAcknowledge(id: String) = viewModel.acknowledge(id)
            override fun onArrive(id: String) = viewModel.arrive(id)
            override fun onResolve(id: String) = viewModel.resolve(id)
            override fun onDocument(id: String) = nav.navigate(DocumentationRoute(id))
            override fun onOpenAlert(id: String) = nav.navigate(AlertRoute(id))
            override fun onOpenReadiness() = nav.navigate(ReadinessRoute)
            override fun onRetry() = viewModel.retry()
            override fun onSelectTab(tab: DutyTab) = selectTab(nav, tab)
        },
    )
}

@Composable
private fun NurseStandby(user: SessionUser, state: DutyState, now: Instant, viewModel: DutyViewModel, nav: NavHostController) {
    val shift = state.shift ?: return
    val open = state.alerts.openSorted()
    val firstWaiting = open.firstWaiting()
    NurseStandbyScreen(
        state = NurseStandbyUiState(
            userName = user.name,
            shift = shift,
            connection = state.connection,
            readiness = state.readiness,
            alerts = open,
            incidents = state.incidents.filterNot { it.isHandled },
            patientCount = state.wardPatients.total,
            monitorDisconnected = state.wardPatients.monitorDisconnected,
            checkPhase = firstWaiting?.let { state.phases[it.id] } ?: CommandPhase.Idle,
            loading = !state.loaded && state.error == null,
            error = state.error.takeUnless { state.loaded },
        ),
        now = now,
        actions = object : NurseStandbyActions {
            override fun onCheckFirst(id: String) = viewModel.check(id) { nav.navigate(AlertRoute(id)) }
            override fun onOpenAlert(id: String) = nav.navigate(AlertRoute(id))
            override fun onOpenIncident(id: String) = nav.navigate(IncidentRoute(id))
            override fun onOpenReadiness() = nav.navigate(ReadinessRoute)
            override fun onRetry() = viewModel.retry()
            override fun onSelectTab(tab: DutyTab) = selectTab(nav, tab)
        },
    )
}

private fun selectTab(nav: NavHostController, tab: DutyTab) {
    val route: Any = if (tab == DutyTab.STANDBY) StandbyRoute else AccountRoute
    nav.navigate(route) {
        popUpTo<StandbyRoute>()
        launchSingleTop = true
    }
}
