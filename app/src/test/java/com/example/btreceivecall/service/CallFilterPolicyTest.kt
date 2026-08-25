package com.example.btreceivecall.service

import org.junit.Assert.assertEquals
import org.junit.Test

class CallFilterPolicyTest {
    @Test
    fun testAutoAnswerDelays() {
        // Verify delay values
        assertEquals(400L, CallControlPolicy.LOCKED_AUTO_ANSWER_DELAY_MS)
        assertEquals(3000L, CallControlPolicy.UNLOCKED_AUTO_ANSWER_DELAY_MS)
    }
}
