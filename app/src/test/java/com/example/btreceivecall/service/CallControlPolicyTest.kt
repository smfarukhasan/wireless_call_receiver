package com.example.btreceivecall.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallControlPolicyTest {
    @Test
    fun lockedAutoAnswerLeavesRoomInsideOneSecond() {
        assertEquals(400L, CallControlPolicy.autoAnswerDelayMs(isLocked = true))
        assertTrue(CallControlPolicy.autoAnswerDelayMs(isLocked = true) < 1_000L)
    }

    @Test
    fun unlockedAutoAnswerUsesThreeSeconds() {
        assertEquals(3_000L, CallControlPolicy.autoAnswerDelayMs(isLocked = false))
    }

    @Test
    fun duplicateCallbacksFromOnePhysicalPressAreIgnored() {
        assertTrue(CallControlPolicy.isDuplicateEvent(1_000L, 1_120L))
        assertFalse(CallControlPolicy.isDuplicateEvent(1_000L, 1_121L))
        assertFalse(CallControlPolicy.isDuplicateEvent(0L, 50L))
    }

    @Test
    fun twoDistinctPressesInsideWindowTriggerDoublePress() {
        assertTrue(CallControlPolicy.isDoublePress(1_000L, 1_800L))
        assertFalse(CallControlPolicy.isDoublePress(1_000L, 1_801L))
        assertFalse(CallControlPolicy.isDoublePress(0L, 500L))
    }
}
