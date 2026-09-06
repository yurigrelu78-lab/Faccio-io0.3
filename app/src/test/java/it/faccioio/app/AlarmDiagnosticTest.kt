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

    @Test
    fun `ripristina quando manca almeno un collegamento`() {
        assertEquals(
            true,
            shouldRepairFutureAutomations(
                hasMissingPendingIntent = true,
                earliestExpectedAlarm = 1_000L,
                nextAndroidAlarm = 1_000L
            )
        )
    }

    @Test
    fun `ripristina quando Android non espone la sveglia attesa`() {
        assertEquals(
            true,
            shouldRepairFutureAutomations(
                hasMissingPendingIntent = false,
                earliestExpectedAlarm = 1_000L,
                nextAndroidAlarm = null
            )
        )
    }

    @Test
    fun `non ripristina quando la pianificazione e coerente`() {
        assertEquals(
            false,
            shouldRepairFutureAutomations(
                hasMissingPendingIntent = false,
                earliestExpectedAlarm = 1_000L,
                nextAndroidAlarm = 1_000L
            )
        )
    }

    @Test
    fun `accetta una sveglia di sistema precedente a quella dell app`() {
        assertEquals(
            false,
            shouldRepairFutureAutomations(
                hasMissingPendingIntent = false,
                earliestExpectedAlarm = 2_000L,
                nextAndroidAlarm = 1_000L
            )
        )
    }
}
