# Kontrak log tindakan resusitasi yang dipakai aplikasi Android

Status: **usulan dari sisi klien** untuk [ADR 0041](../../../sigap/docs/decisions/0041-real-time-resuscitation-log-with-rotating-recorder.md) (Proposed). ADR menetapkan endpoint dan kolom, tetapi belum menetapkan bentuk `payload` dan isi jawaban. Bagian di bawah adalah bentuk yang sudah diimplementasikan di `ritme`; `validateResusLogPayload` di `@sigap/shared` perlu menyamakannya atau mengubah dokumen ini lebih dulu.

Fitur tersembunyi di balik `BuildConfig.RESUS_LOG_ENABLED`. Staging membacanya dari `-Psigap.resusLog=true`; production selalu `false` sampai backend dan ADR siap.

## Endpoint

Semua jawaban memakai envelope `{"data": …}` seperti endpoint lain. Keduanya mengembalikan `ResusLog`.

Tidak ada pencatat aktif atau ambil alih (ADR 0041 Section 2.2, revisi 2026-11-23): pencatat berganti setiap siklus, dan setiap HP tim menulis langsung. Pencegah catatan ganda adalah peringatan `resus_duplicate_warning_seconds` di HP.

| Endpoint                                  | Catatan                                                                                  |
| ----------------------------------------- | ---------------------------------------------------------------------------------------- |
| `GET /incidents/:id/resus-log?after_seq=` | `after_seq` dihilangkan pada muatan pertama                                              |
| `POST /incidents/:id/resus-log`           | Body `{"entries": [EntryInput]}`; idempoten per `id`; jawaban hanya memuat entri batch itu |

```jsonc
// ResusLog
{
  "entries": [Entry],
  "last_seq": 42,                 // seq tertinggi incident ini
}

// Entry (dari server)
{
  "id": "uuid", "seq": 7, "entry_type": "DRUG_GIVEN", "payload": { … },
  "actor_user_id": "…", "actor_name": "Sari",
  "tapped_at": "ISO-8601", "tapped_at_suspect": false, "received_at": "ISO-8601",
  "voids_entry_id": null
}

// EntryInput (dari HP)
{ "id": "uuid", "entry_type": "…", "payload": { … }, "tapped_at": "ISO-8601", "clock_offset_ms": 0, "voids_entry_id": null }
```

Yang diandalkan klien:

- **Kursor polling hanya maju dari GET.** Jawaban POST tidak memuat entri HP lain di antaranya, jadi klien tidak memajukan `after_seq` darinya. Server harus membuat `seq` terlihat berurutan (misalnya dialokasikan di bawah kunci per incident), karena entri dengan `seq` lebih kecil yang baru terlihat belakangan akan terlewat.
- **4xx selain 401/408/429 berarti entri ditolak permanen.** Klien memecah batch dan mengirim satu per satu; entri yang tetap ditolak disimpan, ditandai "Ditolak server: <message>", dan tidak dikirim lagi. 5xx, 401, 408, 429, dan gangguan jaringan diulang dengan jeda 1, 2, 4 … 30 detik.
- Jam HP dikoreksi dari header `Date` jawaban API (presisi detik). `clock_offset_ms` adalah koreksi itu saat ketukan.

## `payload` per `entry_type`

Tidak ada dosis, joule, atau interval: ADR 0041 Section 2.6 melarang menampilkan nilai protokol tanpa sumber. Jumlah obat dan energi diisi manusia di formulir dokumentasi.

| `entry_type`        | `payload`                                                             |
| ------------------- | --------------------------------------------------------------------- |
| `RHYTHM_CHECKED`    | `{"rhythm": "VF"\|"VT"\|"ASYSTOLE"\|"PEA", "starts_cycle": false}`      |
| `CPR_CYCLE_STARTED` | `{}`                                                                  |
| `DRUG_GIVEN`        | `{"drug": "EPINEPHRINE"\|"AMIODARONE"}`                               |
| `SHOCK_DELIVERED`   | `{}`                                                                  |
| `PROCEDURE_DONE`    | `{"procedure": "ETT"\|"IV_IO_ACCESS"\|"OTHER", "text": "…"}` (`text` untuk `OTHER`) |
| `NOTE_ADDED`        | `{"text": "…"}`                                                       |
| `OUTCOME_RECORDED`  | `{"outcome": "ROSC"\|"MENINGGAL"}`                                    |
| `CORRECTION`        | `{"reason": "…"}` opsional; target di kolom `voids_entry_id`          |

Nilai `rhythm` dan `outcome` sama dengan `RHYTHM_OPTIONS` dan `OUTCOME_OPTIONS` formulir Code Blue, supaya draf dokumentasi (ADR 0041 Section 2.9) dapat menyalinnya tanpa pemetaan.

`starts_cycle` disimpan, bukan dihitung ulang, supaya setiap HP menghitung siklus sama. Selama interval siklus belum bersumber, aplikasi selalu mengirim `false`; siklus hanya bertambah dari `CPR_CYCLE_STARTED`.

Entri dengan `entry_type` atau `payload` yang tidak dikenali tetap ditampilkan sebagai "Entri tidak dikenali" dan tidak dihitung di rekap.

## Pengaturan respons yang dibaca

`resus_duplicate_warning_seconds` (bawaan 15), `resus_log_poll_seconds` (2), `resus_undo_seconds` (5) dari `GET /settings/response`. Bawaan dipakai bila kunci tidak ada.

## Dokumentasi akhir

Setelah status akhir dipilih, layar log menampilkan "Nyatakan penanganan selesai" (perintah `resolve` yang ada, setelah "Tim tiba") lalu "Isi dokumentasi akhir". Dokumentasi dikirim lewat endpoint dokumentasi yang ada; tidak ada PDF.

Saat formulir dibuka dan masih kosong, aplikasi mengisi draf dari log (ADR 0041 Section 2.9): `rhythm`, baris `rows` (jam, obat, irama, catatan; dosis dan joule kosong), `cpr_stopped_at` dari jam status akhir, dan `outcome`. Entri yang dibatalkan atau ditolak tidak disalin. Layar menandainya "diusulkan dari log tindakan" sampai disimpan; server tidak menyimpan penanda itu.

`code_blue_form` bertambah `cpr_stop_reason` dan `verified_by` (teks bebas, opsional, ≤ 500 karakter), sama dengan `@sigap/shared`.

## Belum dikerjakan di Android

- Pengingat protokol bertimer: menunggu protocol set bersumber dan endpoint untuk membacanya.
