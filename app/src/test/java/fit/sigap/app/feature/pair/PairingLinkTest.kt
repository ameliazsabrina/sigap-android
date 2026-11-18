package fit.sigap.app.feature.pair

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingLinkTest {
    private val origin = "https://app-staging.sigap.fit"
    private val token = "abcDEF123_-abcDEF123_-abcDEF123"

    private fun parse(raw: String) = parsePairingQr(raw, origin)

    @Test
    fun `token diambil dari QR pada origin build ini`() {
        assertEquals(ScannedPairing.Token(token), parse("$origin/pair/$token"))
    }

    @Test
    fun `host lain ditolak supaya token lingkungan lain tidak terpakai`() {
        assertEquals(ScannedPairing.NotSigap, parse("https://app.sigap.fit/pair/$token"))
        assertEquals(ScannedPairing.NotSigap, parse("https://app-staging.sigap.fit.evil.test/pair/$token"))
        assertEquals(ScannedPairing.NotSigap, parse("http://app-staging.sigap.fit/pair/$token"))
    }

    @Test
    fun `QR absen dikenali supaya pesannya menjelaskan QR yang benar`() {
        assertEquals(ScannedPairing.CheckInCode, parse("SIGAP-ABSEN:SIM-ICU-01:482913"))
    }

    @Test
    fun `isi yang bukan pairing ditolak`() {
        assertEquals(ScannedPairing.NotSigap, parse("$origin/patients/$token"))
        assertEquals(ScannedPairing.NotSigap, parse("$origin/pair/pendek"))
        assertEquals(ScannedPairing.NotSigap, parse("$origin/pair/$token/lagi"))
        assertEquals(ScannedPairing.NotSigap, parse("bukan url sama sekali"))
    }

    @Test
    fun `port origin lokal ikut dibandingkan`() {
        val local = "http://localhost:3000"
        assertEquals(ScannedPairing.Token(token), parsePairingQr("$local/pair/$token", local))
        assertEquals(ScannedPairing.NotSigap, parsePairingQr("http://localhost:4000/pair/$token", local))
    }
}
