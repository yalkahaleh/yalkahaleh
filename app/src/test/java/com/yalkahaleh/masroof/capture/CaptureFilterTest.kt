package com.yalkahaleh.masroof.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFilterTest {
    @Test fun walletAndSmsAppsAreAlwaysCaptured() {
        assertTrue(CaptureFilter.shouldCapture("com.samsung.android.spay", "hello", false))
        assertTrue(CaptureFilter.shouldCapture("com.samsung.android.messaging", "hello", false))
    }

    @Test fun moneyTextFromAnyAppIsCaptured() {
        assertTrue(CaptureFilter.shouldCapture("com.some.bank", "Purchase of SAR 45.00 at NOON FOOD", false))
        assertTrue(CaptureFilter.shouldCapture("com.some.bank", "شراء بمبلغ 45.00 ريال من نون", false))
        assertTrue(CaptureFilter.shouldCapture("com.some.bank", "POS 12.50 AED", false))
    }

    @Test fun ordinaryChatIsIgnoredUnlessCaptureAll() {
        assertFalse(CaptureFilter.shouldCapture("com.whatsapp", "See you at 5, bring the poster", false))
        assertFalse(CaptureFilter.shouldCapture("com.whatsapp", "I was surprised", false))
        assertTrue(CaptureFilter.shouldCapture("com.whatsapp", "See you at 5", true))
    }
}
