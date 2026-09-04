package it.faccioio.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDiagnosticTest {
    @Test
    fun `rileva la scomparsa di un collegamento prima presente`() {
        assertEquals("SCOMPARSA DA ANDROID", diagnosticAlarmState(hadPending = true, pending = false))
    }

    @Test
    fun `non segnala scomparsa se il collegamento non era mai presente`() {
        assertEquals("CONTROLLO ASSENTE", diagnosticAlarmState(hadPending = false, pending = false))
    }

    @Test
    fun `conferma un collegamento ancora presente`() {
        assertEquals("CONTROLLO PRESENTE", diagnosticAlarmState(hadPending = true, pending = true))
    }
}
