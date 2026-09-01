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

    @Test
    fun keepsMultipleAlarmTypesIncludingAnnualRecurrence() {
        val zone = TimeZone.getTimeZone("Europe/Rome")
        val now = time(zone, 2026, Calendar.AUGUST, 31, 20, 0)
        val birthdayTime = time(zone, 2026, Calendar.SEPTEMBER, 1, 7, 0)
        val routineTime = time(zone, 2026, Calendar.AUGUST, 31, 21, 50)
        val alarms = futureAutomationAlarms(
            listOf(
                TaskItem(
                    title = "Compleanno Claudia Rossi",
                    reminderTime = birthdayTime,
                    appointmentTime = birthdayTime,
                    alarmEnabled = true,
                    recurrence = "Ogni anno"
                ),
                TaskItem(
                    title = "Routine della sera",
                    reminderTime = routineTime,
                    appointmentTime = routineTime,
                    alarmEnabled = true,
                    recurrence = "Personalizzata",
                    recurrenceWeekdays = listOf(Calendar.MONDAY)
                )
            ),
            now
        )

        assertEquals(2, alarms.size)
        assertTrue(alarms.all { it.isAlarm })
        assertEquals(setOf(birthdayTime, routineTime), alarms.map { it.time }.toSet())
    }

    @Test
    fun expiredRecurringRoutineProducesItsNextAlarmWithoutBeingCompleted() {
        val zone = TimeZone.getTimeZone("Europe/Rome")
        val sunday = time(zone, 2026, Calendar.AUGUST, 30, 21, 50)
        val afterSundayAlarm = time(zone, 2026, Calendar.AUGUST, 30, 21, 51)
        val monday = time(zone, 2026, Calendar.AUGUST, 31, 21, 50)
        val task = TaskItem(
            title = "Routine della sera",
            reminderTime = sunday,
            appointmentTime = sunday,
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

        val alarm = futureAutomationAlarms(listOf(task), afterSundayAlarm).single()

        assertEquals(monday, alarm.time)
        assertTrue(alarm.isAlarm)
    }

    @Test
    fun expiredSingleAlarmIsNeverRepeated() {
        val zone = TimeZone.getTimeZone("Europe/Rome")
        val alarmTime = time(zone, 2026, Calendar.AUGUST, 30, 21, 50)
        val afterAlarm = time(zone, 2026, Calendar.AUGUST, 30, 21, 51)
        val task = TaskItem(
            title = "Sveglia singola",
            reminderTime = alarmTime,
            appointmentTime = alarmTime,
            alarmEnabled = true,
            recurrence = "Mai"
        )

        assertTrue(futureAutomationAlarms(listOf(task), afterAlarm).isEmpty())
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
