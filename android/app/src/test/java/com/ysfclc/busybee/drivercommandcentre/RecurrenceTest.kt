package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class RecurrenceTest {
    @Test fun weeklyScheduleMovesForwardSevenDays() {
        val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 5, 9, 0) }.timeInMillis
        val expected = Calendar.getInstance().apply { timeInMillis=start; add(Calendar.WEEK_OF_YEAR,1) }.timeInMillis
        assertEquals(expected, Recurrence.nextDue(start, "weekly"))
    }

    @Test fun monthlyScheduleMovesOneCalendarMonth() {
        val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 15, 9, 0) }.timeInMillis
        val expected = Calendar.getInstance().apply { timeInMillis=start; add(Calendar.MONTH,1) }.timeInMillis
        assertEquals(expected, Recurrence.nextDue(start, "monthly"))
    }

    @Test fun unknownCadenceDoesNotCreateAnotherPayment() {
        assertNull(Recurrence.nextDue(1760000000000L, "once"))
    }
}
