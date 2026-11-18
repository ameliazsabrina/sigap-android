#!/usr/bin/env python3
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

API = os.environ.get("SIGAP_API", "http://localhost:4000/api/v1")
ADMIN_EMAIL = os.environ.get("SIGAP_ADMIN_EMAIL", "dimas.prasetyo@rs-sigap.test")
FINISHED_ALERT = {"CLOSED", "ESCALATED", "FAILED"}
HANDLED_INCIDENT = {"RESOLVED", "DOCUMENTATION_PENDING", "COMPLETED", "CANCELLED"}


def die(message):
    print(f"sigap_admin: {message}", file=sys.stderr)
    sys.exit(1)


def request(method, path, token=None, body=None):
    req = urllib.request.Request(API + path, method=method)
    req.add_header("content-type", "application/json")
    if token:
        req.add_header("authorization", f"Bearer {token}")
    if method != "GET":
        req.add_header("idempotency-key", str(uuid.uuid4()))
    data = None if body is None else json.dumps(body).encode()
    try:
        with urllib.request.urlopen(req, data=data, timeout=30) as response:
            return json.loads(response.read() or b"{}").get("data")
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:300]
        die(f"{method} {path}: HTTP {error.code} {detail}")
    except urllib.error.URLError as error:
        die(f"{method} {path}: {error.reason}. Is `pnpm dev` running in sigap?")


def login():
    password = os.environ.get("SEED_DEFAULT_PASSWORD")
    if not password:
        die("SEED_DEFAULT_PASSWORD is not set in the shell environment.")
    return request("POST", "/auth/login", body={"email": ADMIN_EMAIL, "password": password})["access_token"]


def token():
    return os.environ.get("SIGAP_ADMIN_TOKEN") or login()


def get(path):
    return request("GET", path, token())


def post(path, body=None):
    return request("POST", path, token(), {} if body is None else body)


def staff(number):
    for row in get("/admin/responders"):
        if row.get("staff_number") == number:
            return row
    die(f"staff {number} is not in the seed")


def ward(code):
    for row in get("/wards"):
        if row.get("code") == code:
            return row
    die(f"ward {code} is not in the seed")


def patient(name):
    for row in get("/patient-simulations"):
        if row.get("display_name") == name:
            if row.get("archive"):
                die(f"patient {name} is archived; restore it or pick another patient")
            return row
    die(f"patient {name} is not in the seed")


def open_alerts(patient_id):
    return [
        a for a in get("/early-alerts")
        if a["patient"]["id"] == patient_id and a["alert_status"] not in FINISHED_ALERT
    ]


def active_incidents(patient_id):
    return [
        i for i in get("/incidents")
        if i["patient"]["id"] == patient_id and i["incident_status"] not in HANDLED_INCIDENT
    ]


def cmd_login():
    print(login())


def cmd_end_shift(number):
    shift = staff(number).get("shift")
    if shift:
        post(f"/shifts/{shift['id']}/end")
        print(f"ended shift {shift['id']}", file=sys.stderr)


def cmd_pairing_link(number):
    print(post(f"/admin/responders/{staff(number)['id']}/pairing-links")["url"])


def cmd_checkin_code(ward_code):
    print(get(f"/wards/{ward(ward_code)['id']}/check-in-code")["code"])


def cmd_clean_patient(name):
    pid = patient(name)["id"]
    for alert in open_alerts(pid):
        if alert["checked_at"] is None:
            post(f"/early-alerts/{alert['id']}/check")
        post(f"/early-alerts/{alert['id']}/close", {"reason": "Dibersihkan wrapper Maestro (simulasi)."})
    left = active_incidents(pid)
    if left:
        ids = ", ".join(f"{i['incident_number']} ({i['incident_status']})" for i in left)
        die(f"{name} still has an active Code Blue: {ids}. Finish it in the app, or reset test data.")


def cmd_test_alert(name, kind):
    result = post("/admin/test-alerts", {"patient_id": patient(name)["id"], "kind": kind})
    if result.get("outcome") != "CREATED" or not result.get("alert"):
        die(f"test alert for {name} not created: {result.get('outcome')}")
    print(result["alert"]["id"])


def cmd_incident(name):
    result = post("/admin/test-alerts", {"patient_id": patient(name)["id"], "kind": "HIGH"})
    if result.get("outcome") != "CREATED" or not result.get("alert"):
        die(f"test alert for {name} not created: {result.get('outcome')}")
    alert_id = result["alert"]["id"]
    post(f"/early-alerts/{alert_id}/check")
    incident = post(f"/early-alerts/{alert_id}/escalate", {"reason": "Dibuat wrapper Maestro (simulasi)."})
    print(incident["id"])


def cmd_active_incident(name):
    found = active_incidents(patient(name)["id"])
    print(found[0]["id"] if found else "")


def cmd_other_unacknowledged(ward_code, name):
    ward_id = ward(ward_code)["id"]
    pid = patient(name)["id"]
    for i in get("/incidents"):
        if (i["location"].get("ward_id") == ward_id and i["patient"]["id"] != pid
                and i["incident_status"] not in HANDLED_INCIDENT and i["acknowledged_at"] is None):
            print(f"{i['incident_number']} {i['patient']['display_name']}")


COMMANDS = {
    "login": cmd_login,
    "end-shift": cmd_end_shift,
    "pairing-link": cmd_pairing_link,
    "checkin-code": cmd_checkin_code,
    "clean-patient": cmd_clean_patient,
    "test-alert": cmd_test_alert,
    "incident": cmd_incident,
    "active-incident": cmd_active_incident,
    "other-unacknowledged": cmd_other_unacknowledged,
}

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        die(f"usage: sigap_admin.py <{'|'.join(COMMANDS)}> [args]")
    COMMANDS[sys.argv[1]](*sys.argv[2:])
