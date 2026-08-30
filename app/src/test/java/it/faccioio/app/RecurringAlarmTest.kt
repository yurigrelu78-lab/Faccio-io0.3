package it.faccioio.app

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringAlarmTest {
    @Test
    fun fridayEveningRoutineMovesToSundayAndKeepsAlarm() {
        val zone = TimeZone.getTimeZone("Europe/Rome")
        val friday = time(zone, 2026, Calendar.AUGUST, 28, 21, 30)
        val afterFridayAlarm = time(zone, 2026, Calendar.AUGUST, 28, 22, 0)
        val sunday = time(zone, 2026, Calendar.AUGUST, 30, 21, 30)
        val task = TaskItem(
            title = "Routine della sera",
            reminderTime = friday,
            appointmentTime = friday,
            alarmEnabled = true,
            recurrence = "Personalizzata",
            recurrenceWeekdays = listOf(
                Calendar.SUNDAY,
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY
            )
        )

        val next = nextRecurringOccurrence(task, afterFridayAlarm)
        val alarms = futureAutomationAlarms(listOf(next), afterFridayAlarm)

        assertEquals(sunday, next.appointmentTime)
        assertEquals(sunday, next.reminderTime)
        assertTrue(next.alarmEnabled)
        assertFalse(next.completed)
        assertEquals(1, alarms.size)
        assertEquals(sunday, alarms.single().time)
        assertTrue(alarms.single().isAlarm)
    }

    private fun time(
        zone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis
}
