package com.khubsoja.wirelesscallreceiver.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallFilterPolicyTest {

    private fun isPermitted(
        isMasterOn: Boolean,
        isLockedScreen: Boolean,
        isReceiveLockedOn: Boolean,
        isReceiveUnlockedOn: Boolean
    ): Boolean {
        if (!isMasterOn) return false
        return if (isLockedScreen) isReceiveLockedOn else isReceiveUnlockedOn
    }

    private fun shouldAutoAnswer(
        isMasterOn: Boolean,
        isAutoAnswerOn: Boolean,
        isLockedScreen: Boolean,
        isReceiveLockedOn: Boolean,
        isReceiveUnlockedOn: Boolean
    ): Boolean {
        if (!isAutoAnswerOn) return false
        return isPermitted(isMasterOn, isLockedScreen, isReceiveLockedOn, isReceiveUnlockedOn)
    }

    @Test
    fun testRule1_LockedOnly() {
        // Rule 1: Auto-Answer OFF, Locked ON, Unlocked OFF
        // Only locked accepts button clicks to answer
        assertTrue(isPermitted(isMasterOn = true, isLockedScreen = true, isReceiveLockedOn = true, isReceiveUnlockedOn = false))
        assertFalse(isPermitted(isMasterOn = true, isLockedScreen = false, isReceiveLockedOn = true, isReceiveUnlockedOn = false))
    }

    @Test
    fun testRule2_UnlockedOnly() {
        // Rule 2: Auto-Answer OFF, Locked OFF, Unlocked ON
        // Only unlocked accepts button clicks to answer
        assertFalse(isPermitted(isMasterOn = true, isLockedScreen = true, isReceiveLockedOn = false, isReceiveUnlockedOn = true))
        assertTrue(isPermitted(isMasterOn = true, isLockedScreen = false, isReceiveLockedOn = false, isReceiveUnlockedOn = true))
    }

    @Test
    fun testRule3_BothLockedAndUnlocked() {
        // Rule 3: Auto-Answer OFF, Locked ON, Unlocked ON
        // Both locked and unlocked accept button clicks
        assertTrue(isPermitted(isMasterOn = true, isLockedScreen = true, isReceiveLockedOn = true, isReceiveUnlockedOn = true))
        assertTrue(isPermitted(isMasterOn = true, isLockedScreen = false, isReceiveLockedOn = true, isReceiveUnlockedOn = true))
    }

    @Test
    fun testRule4_BothOff_NoAutoAnswer() {
        // Rule 4: Both Locked and Unlocked are OFF
        // Neither can receive and Auto-Answer cannot trigger
        assertFalse(isPermitted(isMasterOn = true, isLockedScreen = true, isReceiveLockedOn = false, isReceiveUnlockedOn = false))
        assertFalse(isPermitted(isMasterOn = true, isLockedScreen = false, isReceiveLockedOn = false, isReceiveUnlockedOn = false))

        assertFalse(shouldAutoAnswer(isMasterOn = true, isAutoAnswerOn = true, isLockedScreen = true, isReceiveLockedOn = false, isReceiveUnlockedOn = false))
        assertFalse(shouldAutoAnswer(isMasterOn = true, isAutoAnswerOn = true, isLockedScreen = false, isReceiveLockedOn = false, isReceiveUnlockedOn = false))
    }
}
