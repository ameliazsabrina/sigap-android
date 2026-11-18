package fit.sigap.app.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushMessageTest {
    @Test
    fun `alert Code Blue berbunyi sebagai alarm dan memakai incident sebagai tag`() {
        val message = PushMessage.parse(
            mapOf(
                "kind" to "CODE_BLUE",
                "title" to "CODE BLUE (SIMULASI) · ICU Bed 01",
                "body" to "Simulasi SIGAP. Bukan kejadian klinis.",
                "notification_id" to "n1",
                "incident_id" to "i1",
                "sent_at" to "2026-11-12T08:00:00.000Z",
            ),
        ) as PushMessage.Alert

        assertTrue(message.alarm)
        assertEquals("i1", message.tag)
        assertEquals(AlarmTarget.IncidentTarget("i1"), message.alarmTarget)
        assertEquals("n1", message.notificationId)
    }

    @Test
    fun `Early Alert biasa bukan alarm dan memakai alert sebagai tag`() {
        val message = PushMessage.parse(
            mapOf("kind" to "EARLY_ALERT", "title" to "Early Alert", "early_alert_id" to "a1", "notification_id" to "n2"),
        ) as PushMessage.Alert

        assertFalse(message.alarm)
        assertEquals("a1", message.tag)
    }

    @Test
    fun `notifikasi uji tanpa id tetap ditampilkan tanpa tanda terima`() {
        val message = PushMessage.parse(mapOf("kind" to "TEST", "title" to "SIGAP · Notifikasi tes", "body" to "x")) as PushMessage.Alert

        assertNull(message.notificationId)
        assertEquals("TEST", message.tag)
    }

    @Test
    fun `kind yang belum dikenal tetap tampil tetapi tidak berbunyi sebagai alarm`() {
        val message = PushMessage.parse(mapOf("kind" to "SOMETHING_NEW")) as PushMessage.Alert

        assertFalse(message.alarm)
        assertEquals("SIGAP", message.title)
    }

    @Test
    fun `ALARM_STOP menyebut incident atau Early Alert`() {
        assertEquals(
            PushMessage.AlarmStop(AlarmTarget.IncidentTarget("i1")),
            PushMessage.parse(mapOf("kind" to "ALARM_STOP", "incident_id" to "i1")),
        )
        assertEquals(
            PushMessage.AlarmStop(AlarmTarget.EarlyAlertTarget("a1")),
            PushMessage.parse(mapOf("kind" to "ALARM_STOP", "early_alert_id" to "a1")),
        )
    }

    @Test
    fun `pesan tanpa kind atau ALARM_STOP tanpa sasaran diabaikan`() {
        assertNull(PushMessage.parse(mapOf("title" to "x")))
        assertNull(PushMessage.parse(mapOf("kind" to "ALARM_STOP")))
    }
}
