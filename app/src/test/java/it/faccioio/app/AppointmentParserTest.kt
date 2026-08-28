package it.faccioio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppointmentParserTest {
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
