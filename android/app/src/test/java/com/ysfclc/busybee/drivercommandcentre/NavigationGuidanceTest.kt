package com.ysfclc.busybee.drivercommandcentre

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGuidanceTest {
    @Test
    fun approachStageStartsAtFourHundredFiftyMetresAndDoesNotRepeat() {
        assertEquals(0, NavigationGuidance.nextAnnouncementStage(451.0, 0))
        assertEquals(1, NavigationGuidance.nextAnnouncementStage(450.0, 0))
        assertEquals(1, NavigationGuidance.nextAnnouncementStage(300.0, 1))
    }

    @Test
    fun nearStageStartsAtOneHundredTwentyMetres() {
        assertEquals(2, NavigationGuidance.nextAnnouncementStage(120.0, 1))
        assertEquals(2, NavigationGuidance.nextAnnouncementStage(80.0, 2))
    }

    @Test
    fun nowStageStartsAtThirtyFiveMetres() {
        assertEquals(3, NavigationGuidance.nextAnnouncementStage(35.0, 2))
        assertEquals(3, NavigationGuidance.nextAnnouncementStage(10.0, 3))
    }

    @Test
    fun gpsFixAlreadyNearTurnSkipsEarlierAnnouncementBands() {
        assertEquals(2, NavigationGuidance.nextAnnouncementStage(100.0, 0))
        assertEquals(3, NavigationGuidance.nextAnnouncementStage(20.0, 0))
    }

    @Test
    fun announcementTextChangesByApproachStage() {
        assertEquals(
            "In 310 metres, Turn left onto Długa",
            NavigationGuidance.announcement(310.0, "Turn left onto Długa", 1)
        )
        assertEquals(
            "Now, Turn left onto Długa",
            NavigationGuidance.announcement(12.0, "Turn left onto Długa", 3)
        )
    }
}
