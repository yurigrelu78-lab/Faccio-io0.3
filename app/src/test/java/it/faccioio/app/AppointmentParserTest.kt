package it.faccioio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AppointmentParserTest {
    @Test
    fun recognizesTodayWithPastTime() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 29, 9, 21, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val parsed = parseAppointment(
            "Appuntamento con Valerio oggi alle ore 9:00",
            now
        )

        assertEquals("Appuntamento con Valerio", parsed?.title)
        assertTrue(parsed?.timeInPast == true)
    }

    @Test
    fun recognizesTomorrowWithoutTime() {
        val parsed = parseAppointment("Domani ricordarsi Zippo")

        assertEquals("Zippo", parsed?.title)
        assertTrue(parsed?.dateOnly == true)
    }

    @Test
    fun recognizesTodayWithoutTime() {
        val parsed = parseAppointment("Oggi comprare il pane")

        assertEquals("Comprare il pane", parsed?.title)
        assertTrue(parsed?.dateOnly == true)
    }

    @Test
    fun recognizesWorkArrivalWithoutDateOrAddress() {
        assertEquals(
            PersonalArrivalCommand("work", "Verificare il test"),
            parsePersonalArrivalCommand(
                "quando arrivo al lavoro, ricordami di verificare il test"
            )
        )
    }

    @Test
    fun recognizesHomeArrivalWithoutDateOrAddress() {
        assertEquals(
            PersonalArrivalCommand("home", "Prendere le medicine"),
            parsePersonalArrivalCommand(
                "Quando arrivo a casa ricordami di prendere le medicine"
            )
        )
    }

    @Test
    fun recognizesArrivalClauseAfterTheReminderText() {
        assertEquals(
            PersonalArrivalCommand("work", "Parlare con Francesco"),
            parsePersonalArrivalCommand(
                "Ricordarsi di parlare con Francesco quando arrivo al lavoro"
            )
        )
    }

    @Test
    fun ignoresOrdinaryMentionsOfHomeAndWork() {
        assertNull(parsePersonalArrivalCommand("Domani lavoro da casa alle 9"))
    }
}
