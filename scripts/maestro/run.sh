#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FLOWS="$ROOT/.maestro"
ADMIN=(python3 -I "$ROOT/scripts/maestro/sigap_admin.py")
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
MAESTRO="${MAESTRO:-maestro}"
APP="fit.sigap.app.staging"
PAIR_ORIGIN="${SIGAP_PAIR_ORIGIN:-http://localhost:3000}"

NURSE_STAFF="PRW-1001"
NURSE_NAME="Ns. Ani Rahmawati"
TEAM_STAFF="CB-2001"
TEAM_NAME="dr. Yoga Pradana"
WARD_CODE="IGD"
WARD_NAME="Instalasi Gawat Darurat"
PATIENT="Ny. Lestari Handayani"
LOCATION="IGD · Bay 02"

: "${SEED_DEFAULT_PASSWORD:?Set SEED_DEFAULT_PASSWORD in the shell (never in a file).}"
export SEED_DEFAULT_PASSWORD

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

log() { printf '\n==> %s\n' "$*"; }

adb_shell() { "$ADB" shell "$@"; }

flow() {
  local file="$1"; shift
  local env_args=()
  for kv in "$@"; do env_args+=(-e "$kv"); done
  log "maestro $file"
  "$MAESTRO" test "${env_args[@]}" "$FLOWS/$file"
}

push() {
  local quoted=() arg
  for arg in "$@"; do
    [[ "$arg" != *"'"* ]] || { echo "push argument may not contain a single quote: $arg" >&2; exit 1; }
    quoted+=("'$arg'")
  done
  adb_shell am broadcast -n "$APP/fit.sigap.app.core.push.DebugPushReceiver" "${quoted[@]}" >/dev/null
}

prepare_device() {
  local staff="$1"
  log "ending open shift of $staff and pairing it to this emulator"
  "${ADMIN[@]}" end-shift "$staff"
  local url
  url="$("${ADMIN[@]}" pairing-link "$staff")"
  [[ "$url" == "$PAIR_ORIGIN"/pair/* ]] || { echo "pairing link $url is not on $PAIR_ORIGIN" >&2; exit 1; }
  adb_shell pm clear "$APP" >/dev/null
  adb_shell pm grant "$APP" android.permission.POST_NOTIFICATIONS
  adb_shell pm set-permission-flags "$APP" android.permission.CAMERA user-fixed
  adb_shell cmd deviceidle whitelist "+$APP" >/dev/null
  adb_shell appops set "$APP" USE_FULL_SCREEN_INTENT allow
  adb_shell svc power stayon true
  adb_shell input keyevent KEYCODE_WAKEUP
  adb_shell am start -S -W -n "$APP/fit.sigap.app.MainActivity" -a android.intent.action.VIEW -d "'$url'" >/dev/null
}

checkin_code() { "${ADMIN[@]}" checkin-code "$WARD_CODE"; }

run_nurse() {
  "${ADMIN[@]}" clean-patient "$PATIENT"
  prepare_device "$NURSE_STAFF"
  flow nurse_pair_checkin.yaml "NAME=$NURSE_NAME" "CODE=$(checkin_code)" "WARD=$WARD_NAME"

  local alert
  alert="$("${ADMIN[@]}" test-alert "$PATIENT" MEDIUM)"
  push --es kind EARLY_ALERT --es early_alert_id "$alert" --es title "Alert · $LOCATION" --es body "Alert uji (simulasi)"
  flow nurse_alert_check_close.yaml "LOCATION=$LOCATION"

  alert="$("${ADMIN[@]}" test-alert "$PATIENT" MEDIUM)"
  push --es kind EARLY_ALERT --es early_alert_id "$alert" --es title "Alert · $LOCATION" --es body "Alert uji (simulasi)"
  flow nurse_alert_escalate.yaml "LOCATION=$LOCATION"
}

warn_other_incidents() {
  local others
  others="$("${ADMIN[@]}" other-unacknowledged "$WARD_CODE" "$PATIENT")"
  if [[ -n "$others" ]]; then
    echo "Other unacknowledged Code Blue in $WARD_CODE may take the banner first:" >&2
    echo "$others" >&2
  fi
}

run_team() {
  prepare_device "$TEAM_STAFF"
  flow common/finish_setup_and_check_in.yaml "NAME=$TEAM_NAME" "CODE=$(checkin_code)"

  local incident
  incident="$("${ADMIN[@]}" active-incident "$PATIENT")"
  [[ -n "$incident" ]] || incident="$("${ADMIN[@]}" incident "$PATIENT")"
  warn_other_incidents
  flow team_codeblue_ack_arrive_resolve_document.yaml "LOCATION=$LOCATION"

  incident="$("${ADMIN[@]}" incident "$PATIENT")"
  warn_other_incidents
  local title="CODE BLUE · $LOCATION (simulasi)"
  adb_shell input keyevent KEYCODE_HOME
  push --es kind CODE_BLUE --es incident_id "$incident" --es title "$title" --es body "Simulasi Maestro"
  adb_shell cmd statusbar expand-notifications
  flow team_alarm.yaml "TITLE=$title" "LOCATION=$LOCATION"
}

export SIGAP_ADMIN_TOKEN
SIGAP_ADMIN_TOKEN="$("${ADMIN[@]}" login)"

case "${1:-all}" in
  nurse) run_nurse ;;
  team) run_team ;;
  all) run_nurse; run_team ;;
  *) echo "usage: $0 [nurse|team|all]" >&2; exit 2 ;;
esac
log "all flows passed"
