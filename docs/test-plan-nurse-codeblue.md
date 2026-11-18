# Test plan: nurse and Code Blue team roles

Status (2026-11-18): Phases 0, 1 and 2 landed, uncommitted. Phase 3 declined. See "What landed"
below; the original plan follows unchanged except for the decisions and parity rows.

## What landed (2026-11-18)

Verified on the `Pixel_9` emulator against `pnpm dev` in sigap:

| Gate | Result |
|---|---|
| `testStagingDebugUnitTest` | 91 pass (86 before + 5 new) |
| `connectedStagingDebugAndroidTest` | 53 pass |
| `lintStagingDebug` | passes |
| `scripts/maestro/run.sh all`, twice in a row | Passes, all 7 flow runs each time, on a boot from the third reboot (see below) |

The double run passed with no emulator-window touches and no reinstall during it (checked in
logcat). It was not on a pristine boot: earlier attempts on the same boot failed because of a stale
build (see the `clean` note under Phase 2), and the user chose to finish on that boot rather than
reboot a fourth time. A pristine-boot double run is still open. The gates were re-run after another
session stripped comments from the ritme sources.

**Phase 0.** `feature/common/DutyTabBarTest`. `androidTest` sees the `internal` fixtures in
`src/debug` (`PreviewData`, `PreviewTheme`, `PreviewNow`) without moving them. Instrumented test
names are camelCase: below API 30 (minSdk 26), D8 rejects spaces in method names.

**Phase 1.** Eight screen test files under `app/src/androidTest/java/fit/sigap/app/feature/`:
`NurseStandbyScreenTest` (9), `AlertScreenTest` (8, nurse and team), `StartDutyScreenTest` (5),
`AccountScreenTest` (4), `TeamStandbyScreenTest` (7), `IncidentScreenTest` (9),
`DocumentationScreenTest` (5), `AlarmScreenTest` (5). Double-tap cases flip the phase to `Sending`
from inside the callback, so they prove the first tap registers and the second is blocked.

Each test was checked against a failure: one run with 10 temporary production mutations (busy no
longer disabling the button, `canDecide`/`canRespond` ignored, banner checking the last waiting
alert, wrong blocker subject, overdue ignored, finish without confirmation, save sending empty
values, stopped alarm still offering the action, disconnected notice hidden). 18 tests failed, each
on its own mutation; the mutations were then reverted.

Extraction, test-first: `List<EarlyAlert>.firstWaiting()` and
`List<EarlyAlert>.outsideActiveCodeBlue(incidents)` in `core/model/EarlyAlert.kt`, used by
`AppNavigation.kt`. Five JVM cases in `EarlyAlertRulesTest`, watched failing against stubs first.
The filter now ignores handled incidents itself, so it no longer relies on the caller passing the
active list.

**Phase 2.** Maestro 2.12.1 from the `mobile-dev-inc/tap` Homebrew formula (arm64 Homebrew in
`/opt/homebrew`; the x64 one in `/usr/local` builds from source). Flows in `.maestro/`, wrapper in
`scripts/maestro/run.sh` with admin calls in `scripts/maestro/sigap_admin.py`:

```bash
./gradlew installStagingDebug -Psigap.apiBaseUrl=http://10.0.2.2:4000/api/v1/ -Psigap.pairOrigin=http://localhost:3000
SEED_DEFAULT_PASSWORD=... scripts/maestro/run.sh all   # or nurse | team
```

- Accounts: PRW-1001 Ns. Ani Rahmawati (IGD) and CB-2001 dr. Yoga Pradana (decision 2). Pairing
  revokes only paired devices, not password sessions, so Ani's nurse station login in Playwright is
  unaffected. The first pairing revoked a stale Android device of Yoga's (paired 2026-11-06, last
  seen 2026-11-09, push never confirmed).
- Patient: Ny. Lestari Handayani (IGD · Bay 02), which no Playwright spec uses. The documentation
  outcome is left empty, so the patient is never archived. Every run ends with its incidents
  `COMPLETED`, so the next run starts clean; `clean-patient` stops with a message if one is left.
- The wrapper ends the account's open shift, runs `pm clear`, pre-grants notifications, marks the
  camera as denied (user-fixed), allows the battery exemption and full-screen intent, then fires
  the debug pairing intent. Check-in is by typed code.
- `team_alarm.yaml` opens `AlarmActivity` from the alarm notification with the screen on. The
  lock-screen path is **not** automated: the emulator has a PIN, which a flow must not enter.
- Maestro traps: `hideKeyboard` sends Back and finishes `MainActivity`; the code field's label and
  its description share one text, so the field is `index: 1`.
- The first two fresh-boot double runs failed, each time from touches in the emulator window
  during the run, not from the flows or the app. Android cancels a touch in progress when another
  input device starts one, and logcat showed the emulator's touchscreen (`virtio_input_multi_touch`,
  device 12) next to Maestro's injected taps (device -1): a lost "Tidak kritis" tap at 15:49:03, an
  alarm acknowledged at 15:52:54, and an incident marked arrived and resolved at 16:04:29–33 while
  no flow was running. **Nothing may touch the emulator while the flows run**, including clicks in
  Android Studio's device view.
- Build with `clean installStagingDebug -Psigap...` whenever any build without those properties ran
  since (the Gradle gates, another session). `BuildConfig.PAIR_ORIGIN` is inlined into its callers,
  and an incremental build shipped a caller still holding the staging origin, so the app rejected
  every local pairing link ("QR ini bukan QR pairing SIGAP").
- Hardening added while diagnosing, kept because it is harmless: decision buttons are awaited with
  `enabled: true` (they also show disabled while a command is `Sending`), and taps that open a
  screen with an action button use `retryTapIfNoChange: false`, so a retried tap cannot press the
  next button.

### Findings (not fixed: a test task does not add or remove UI)

1. **No "Simulasi" labelling on native screens.** The PWA shows "Code Blue · Simulasi",
   "Waspada · Simulasi" and "Detail pasien simulasi" (`features/responder/*`); native shows
   "Code Blue", "Waspada" and "Detail pasien" (`IncidentScreen`, `TeamStandbyScreen`, `AlertScreen`).
2. **Checking in again from Akun stays on Akun.** After "Selesai bertugas" and a new check-in the
   app shows "Sedang bertugas" on the Akun tab, not the standby screen.
3. **Pasien and Riwayat tabs are missing natively** (decision 1: a gap, not out of scope).
4. The nurse banner's "Cek pasien" checks the oldest waiting alert in the ward, which is by design,
   so the flows open the Maestro patient's card instead.

Goal: native coverage for the two phone roles, matching what the sigap Playwright phone specs
(`tests/e2e/nurse-phone.spec.ts`, `tests/e2e/codeblue.spec.ts`) prove for the PWA, plus the
behaviour only the native app has (full-screen alarm, push handling).

---

## What is already true (checked 2026-11-18)

- `androidTest` dependencies are wired in `app/build.gradle.kts`: `AndroidJUnitRunner`,
  `ui-test-junit4` (Compose BOM), `espresso-core`, `androidx.junit`, and `ui-test-manifest` on debug.
  `app/src/androidTest` is empty.
- 86 JVM unit tests in 14 suites pass (`testStagingDebugUnitTest`), including ViewModel tests for
  Duty, Alarm, Documentation and Pair. They use `mockwebserver3` and `support/TestSupport.kt`.
- Every screen is stateless: a `UiState` (or plain params) plus an `Actions` interface or lambdas.
  That makes them testable with `createComposeRule()` and no ViewModel, network or DI.
- Preview fixtures exist in `app/src/debug/.../preview/PreviewFixtures.kt` (`PreviewData`,
  `PreviewNow`, `minutesAgo`, `PreviewTheme`). They are `internal`; first check that `androidTest`
  can see them, and reuse them rather than writing a second set of fixtures.
- No `testTag` anywhere in `main`. Select by visible text and content description.
- Role differences live in `AppNavigation.kt`, not in the screens:
  - `AlertScreen(canDecide = role == NURSE)`: only the nurse gets check, close or escalate.
  - `IncidentScreen(canRespond = role == CODE_BLUE_TEAM)`: only the team gets acknowledge, arrive,
    resolve or document.
  - `AccountScreen(uncheckedAlerts = …)` counts only for the nurse.
  - `TeamStandby` drops an alert card once that patient has an active Code Blue. This filter is
    inline in a private composable, so it can't be unit-tested yet.
- `AppContainer` builds its own dependencies from `BuildConfig.API_BASE_URL`, the Keystore and
  Firebase. There's no seam to swap in fakes, so full-app instrumented tests need a production
  change (Phase 3).
- `DutyTab` has only `STANDBY` ("Siaga") and `ACCOUNT` ("Akun"). The PWA has Pasien and Riwayat
  tabs as well.

---

## Phase 0: prove the pipeline (about 1 hour)

1. Start `Pixel_9` with `-gpu host` (not `-no-window`). Wait for guest load to drop. Unlock the
   screen and run `adb shell svc power stayon true`.
2. Turn off animations: `window_animation_scale`, `transition_animation_scale` and
   `animator_duration_scale` set to 0 via `adb shell settings put global …`.
3. Add one trivial Compose test and run:
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew connectedStagingDebugAndroidTest -Porg.gradle.java.installations.auto-detect=false "-Porg.gradle.java.installations.paths=$JAVA_HOME"
   ```
4. Confirm `androidTest` can use `PreviewData`. If `internal` blocks it, move the fixtures to a
   location both source sets can see, but don't duplicate them.

Emulator traps (ANR after install, screen lock after cold boot, stale `uiautomator` dumps, an old
`fit.sigap.app` package) are in the agent memory note `android-emulator-setup`. Rule those out
before treating a failure as an app bug.

## Phase 1: Compose screen tests (about 1 day)

`app/src/androidTest/java/fit/sigap/app/feature/…`, one file per screen, state in and callbacks
out. Write each test first and watch it fail against a deliberately wrong expectation before
trusting it to pass.

### Nurse

| File | Cases |
|---|---|
| `NurseStandbyScreenTest` | Name and shift header, Siaga and Akun tabs. Waiting alert shows the check banner, and tapping it calls `onCheckFirst` with the **first** waiting id. `checkPhase = Sending` blocks a second tap; `Failed` shows its message. Blocked readiness shows the "Alert pasien" blocker, which calls `onOpenReadiness`. An active Code Blue in the ward is visible and opens the incident. Loading state; error with retry calls `onRetry`. A disconnected connection is shown, so stale data doesn't look live. |
| `AlertScreenTest` (nurse, `canDecide = true`) | Unchecked alert: check calls `onCheck`. Checked alert: close sends the default reason through `onClose`, escalate through `onEscalate`. `CLOSED` shows "Alert ditutup: pasien dinilai tidak kritis." with no decision buttons. `Sending` stops a double tap from firing twice. |
| `StartDutyScreenTest` | A six-digit code calls `onSubmitCode` once; a `Failed` phase shows the error and `onClearError` clears it; sign out goes through confirmation. |
| `AccountScreenTest` (nurse) | `uncheckedAlerts > 0` is surfaced before finishing duty; confirm calls `onFinish`. |

### Code Blue team

| File | Cases |
|---|---|
| `TeamStandbyScreenTest` | Ready card when nothing is active; only Siaga and Akun tabs. An unacknowledged incident takes over the screen and acknowledge calls `onAcknowledge(id)`. An overdue id is marked. Early alerts show as information only: none of the nurse actions are present. Awaiting documentation opens through `onDocument`. Blocked readiness shows the "Code Blue" blocker. |
| `IncidentScreenTest` | With `canRespond = true`, the right single action shows for each status (acknowledge, then arrive, then resolve, then document). With `canRespond = false` (the nurse's view), no actions show. Overdue warning. `Sending` blocks a double tap. |
| `AlertScreenTest` (team, `canDecide = false`) | No check, close or escalate controls. |
| `DocumentationScreenTest` | Save draft and complete pass the entered values. A version conflict offers reload and overwrite. `canWrite = false` is read-only. |
| `AlarmScreenTest` | `CODE_BLUE` versus `EARLY_ALERT` primary action calls `onPrimary`; detail calls `onDetail`; `stoppedLine` shows the stopped state. Use `mainClock.autoAdvance = false` if the screen animates. |

### Small extraction (test-first)

Move the `TeamStandby` filter ("an alert disappears once its patient is in Code Blue") and the
nurse's `firstWaiting` choice out of `AppNavigation.kt` into pure functions next to the models.
Write the JVM test first, then move the code. This is the only production change in Phase 1.

### Rules for every assertion

- Keep the visible "Simulasi" labelling. If a screen the PWA labels as simulation has no such label
  natively, record it as a finding; don't add or remove UI from a test task.
- Never assert `DELIVERED` (or its Indonesian label) unless the fixture includes provider
  confirmation. `SENT`, `DELIVERED`, `OPENED` and `ACKNOWLEDGED` stay distinct in fixtures and
  assertions.
- No clinical wording in test names or fixtures beyond what the screens already show.

## Phase 2: Maestro flows against `pnpm dev` (1–2 days)

End-to-end on the installed `stagingDebug` APK, pointed at the local API:

```bash
./gradlew installStagingDebug -Psigap.apiBaseUrl=http://10.0.2.2:4000/api/v1/ -Psigap.pairOrigin=http://localhost:3000
```

1. **Install Maestro.** The installer is a script downloaded from the internet, so ask before
   running it. The Maestro CLI needs a JDK; use Android Studio's JBR.
2. **Wrapper script** (`scripts/maestro/run.sh`). Maestro YAML can't call adb or the admin API, so
   the wrapper does the setup:
   - Log in as the seed Administrator (`SEED_DEFAULT_PASSWORD` from the shell, never written to a
     file).
   - Get a pairing link and fire the debug pairing intent:
     `adb shell am start -S -n fit.sigap.app.staging/fit.sigap.app.MainActivity -a android.intent.action.VIEW -d '<QR>'`.
   - Get the ward's 6-digit code (admin `GET /wards/:id/check-in-code`) and pass it to the flow
     through `-e`.
   - Create a test early alert or `POST /incidents/simulation-trigger`. Deliver the push with the
     `DebugPushReceiver` broadcast, because the local API uses the mock provider.
3. **Flows** (`.maestro/`):
   - `nurse_pair_checkin.yaml`
   - `nurse_alert_check_close.yaml`
   - `nurse_alert_escalate.yaml`
   - `team_codeblue_ack_arrive_resolve_document.yaml`
   - `team_alarm.yaml`: the full-screen `AlarmActivity`. If Maestro can't reach it over the lock
     screen, use a UI Automator test for this one.
4. **Done when** every flow passes twice in a row on a freshly booted emulator.

Accounts: pairing revokes the account's other devices (ADR 0033, risks table). If Maestro pairs
the same seed nurse and team accounts as Playwright's `global.setup.ts`, each suite logs the other
out. Use separate seed accounts or run the suites one after the other. This is decision 2 below.

The admin test-data reset deletes every alert and incident on the current Neon branch.

## Phase 3 (optional): full-app instrumented tests

Only if Maestro proves too flaky or slow. This needs a seam in `AppContainer` (injectable base URL,
`TokenStore`, push token source) and a test `Application` set through a custom runner, so
`MainActivity` can run against `MockWebServer` with fixtures from `TestSupport.kt`. It changes
production wiring, so agree on it before starting.

Separately, smoke-test an R8 build on the main flows. R8 has crashed the app twice when every debug
check passed.

---

## Parity with the Playwright phone specs

| Playwright case | Native coverage |
|---|---|
| Nurse: standby shows officer and four tabs | `NurseStandbyScreenTest` (native has two tabs; a gap, decision 1) |
| Nurse: Pasien tab loads ward patients | **Gap** (decision 1). Native shows only patient counts on Siaga |
| Nurse / team: Riwayat and Akun tabs open | Akun: `AccountScreenTest`. Riwayat: **gap** (decision 1) |
| Nurse: real-time alert, check, close as not critical | `AlertScreenTest` and `nurse_alert_check_close.yaml` |
| Nurse / team: first tap on an opened item registers | `Sending` double-tap cases in `AlertScreenTest` and `IncidentScreenTest` |
| Nurse: active Code Blue shows on standby | `NurseStandbyScreenTest` |
| Nurse / team: no Administrator navigation | Not applicable: the app has no admin routes |
| Nurse / team: 403 from admin, simulation and alert APIs | Backend authorization. Stays in Playwright and API tests; native only checks affordances are hidden |
| Nurse: finish duty, check in again with 6-digit code | `AccountScreenTest`, `StartDutyScreenTest`, `nurse_pair_checkin.yaml` |
| Team: early alert as information without nurse actions | `TeamStandbyScreenTest` and team `AlertScreenTest` |
| Team: acknowledge, arrive, resolve, document | `IncidentScreenTest`, `DocumentationScreenTest`, team Maestro flow |
| Team: resolved incident appears in Riwayat | **Gap**: no native Riwayat (decision 1) |
| Native only: full-screen alarm and push handling | `AlarmScreenTest`, `team_alarm.yaml`, existing `AlarmViewModelTest` and `PushMessageTest` |

## Decisions (answered 2026-11-18)

1. **Tabs.** A gap: the missing Pasien and Riwayat tabs are a missing native feature, recorded
   here; no UI was added by this test work.
2. **Maestro accounts.** Separate seed accounts: PRW-1001 and CB-2001.
3. **Phase 3.** No. Stop at Compose tests plus Maestro; revisit only if Maestro proves flaky.
4. **Commits.** The user commits with `/commit` when they choose.

## Skills to load in the next session

| Skill | Use it for |
|---|---|
| `android-testing` | Test-first rule, semantics-first selectors, `StandardTestDispatcher` default, test-shape table, animation and clock rules |
| `android-dev` | Baseline Android and Kotlin rules; load alongside the others |
| `compose` | Compose test APIs, semantics, `mainClock` |
| `kotlin-coroutines`, `kotlin-flows` | Any ViewModel test touched while extracting logic (two-schedulers trap) |
| `test-driven-development` | The `AppNavigation` extraction: red, then green, then refactor |
| `android-debugging` | Logcat, ANR and instrumentation failures on the emulator |
| `systematic-debugging` | A flaky test: find the root cause before adding waits or retries |
| `verification-before-completion` | Run `connectedStagingDebugAndroidTest`, `testStagingDebugUnitTest` and `lintStagingDebug` before reporting done |
| `android-ux` (optional) | When a test finds a control with no accessible label |

`senior-qa` targets React, Jest and Playwright, so it helps only on the Playwright parity side.
