package com.example.barcodekeyboard

import com.example.barcodekeyboard.core.camera.BarcodeScanFilter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BarcodeScanFilterTest {

    private lateinit var filter: BarcodeScanFilter

    @Before
    fun setUp() {
        filter = BarcodeScanFilter(
            debounceDelayMs = 3500L,
            minIntervalBetweenScansMs = 1200L,
            requiredFrames = 2,
            pauseDurationMs = 900L
        )
    }

    @Test
    fun testMediumSensitivityRequiresTwoFrames() {
        filter.requiredFrames = 2
        val t0 = 10000L

        // Frame 1
        val res1 = filter.processCandidate("899276112345", t0)
        assertTrue(res1 is BarcodeScanFilter.FilterResult.AwaitingFrames)
        assertEquals(1, (res1 as BarcodeScanFilter.FilterResult.AwaitingFrames).current)
        assertEquals(2, res1.required)

        // Frame 2 (consecutive match)
        val res2 = filter.processCandidate("899276112345", t0 + 50)
        assertTrue(res2 is BarcodeScanFilter.FilterResult.Confirmed)
        assertEquals("899276112345", (res2 as BarcodeScanFilter.FilterResult.Confirmed).text)
    }

    @Test
    fun testHighSensitivityAcceptsSingleFrame() {
        filter.requiredFrames = 1
        val t0 = 10000L

        val res = filter.processCandidate("899276112345", t0)
        assertTrue(res is BarcodeScanFilter.FilterResult.Confirmed)
        assertEquals("899276112345", (res as BarcodeScanFilter.FilterResult.Confirmed).text)
    }

    @Test
    fun testLowSensitivityRequiresThreeFrames() {
        filter.requiredFrames = 3
        val t0 = 10000L

        val res1 = filter.processCandidate("899276112345", t0)
        assertTrue(res1 is BarcodeScanFilter.FilterResult.AwaitingFrames)
        assertEquals(1, (res1 as BarcodeScanFilter.FilterResult.AwaitingFrames).current)

        val res2 = filter.processCandidate("899276112345", t0 + 50)
        assertTrue(res2 is BarcodeScanFilter.FilterResult.AwaitingFrames)
        assertEquals(2, (res2 as BarcodeScanFilter.FilterResult.AwaitingFrames).current)

        val res3 = filter.processCandidate("899276112345", t0 + 100)
        assertTrue(res3 is BarcodeScanFilter.FilterResult.Confirmed)
    }

    @Test
    fun testNoiseInterruptionResetsCandidateCounter() {
        filter.requiredFrames = 2
        val t0 = 10000L

        // Frame 1: CODE_A
        val res1 = filter.processCandidate("CODE_A", t0)
        assertTrue(res1 is BarcodeScanFilter.FilterResult.AwaitingFrames)

        // Frame 2: Sudden noise/jitter to CODE_B
        val res2 = filter.processCandidate("CODE_B", t0 + 40)
        assertTrue(res2 is BarcodeScanFilter.FilterResult.AwaitingFrames)
        assertEquals("CODE_B", filter.candidateText)
        assertEquals(1, filter.candidateCount)

        // Frame 3: Confirms CODE_B
        val res3 = filter.processCandidate("CODE_B", t0 + 80)
        assertTrue(res3 is BarcodeScanFilter.FilterResult.Confirmed)
        assertEquals("CODE_B", (res3 as BarcodeScanFilter.FilterResult.Confirmed).text)
    }

    @Test
    fun testIdenticalBarcodeDebouncedWithin3500ms() {
        val t0 = 10000L
        // Confirm first scan
        filter.processCandidate("CODE_XYZ", t0)
        filter.processCandidate("CODE_XYZ", t0 + 50)

        // Immediate subsequent frame at t = 10,200ms is in pause cooldown
        val resPause = filter.processCandidate("CODE_XYZ", t0 + 200)
        assertTrue(resPause is BarcodeScanFilter.FilterResult.InPauseCooldown)

        // After pause ends (900ms, at t = 11,000ms), identical barcode should be debounced
        val resDebounce = filter.processCandidate("CODE_XYZ", t0 + 1000)
        assertTrue(resDebounce is BarcodeScanFilter.FilterResult.DuplicateDebounced)

        // After 3500ms debounce expires (at t0 + 3600ms = 13,600ms), barcode is allowed again
        val resRetry1 = filter.processCandidate("CODE_XYZ", t0 + 3600)
        assertTrue(resRetry1 is BarcodeScanFilter.FilterResult.AwaitingFrames)
        val resRetry2 = filter.processCandidate("CODE_XYZ", t0 + 3650)
        assertTrue(resRetry2 is BarcodeScanFilter.FilterResult.Confirmed)
    }

    @Test
    fun testMinIntervalBetweenDifferentBarcodesThrottled() {
        val t0 = 10000L
        // Confirm CODE_1
        filter.processCandidate("CODE_1", t0)
        filter.processCandidate("CODE_1", t0 + 50)

        // At t = 10,950ms (pause has ended after 900ms, but min interval 1200ms has not)
        val resThrottled = filter.processCandidate("CODE_2", t0 + 950)
        assertTrue(resThrottled is BarcodeScanFilter.FilterResult.MinIntervalThrottled)

        // At t = 11,300ms (1300ms > 1200ms min interval)
        val resAllowed1 = filter.processCandidate("CODE_2", t0 + 1300)
        assertTrue(resAllowed1 is BarcodeScanFilter.FilterResult.AwaitingFrames)
        val resAllowed2 = filter.processCandidate("CODE_2", t0 + 1350)
        assertTrue(resAllowed2 is BarcodeScanFilter.FilterResult.Confirmed)
        assertEquals("CODE_2", (resAllowed2 as BarcodeScanFilter.FilterResult.Confirmed).text)
    }

    @Test
    fun testResetStateClearsEverything() {
        val t0 = 10000L
        filter.processCandidate("CODE_1", t0)
        filter.processCandidate("CODE_1", t0 + 50)

        filter.resetState()

        assertFalse(filter.isPaused(t0 + 100))
        assertEquals("", filter.candidateText)
        assertEquals(0, filter.candidateCount)

        // Can immediately scan CODE_1 again because cache was cleared
        val res1 = filter.processCandidate("CODE_1", t0 + 100)
        assertTrue(res1 is BarcodeScanFilter.FilterResult.AwaitingFrames)
        val res2 = filter.processCandidate("CODE_1", t0 + 150)
        assertTrue(res2 is BarcodeScanFilter.FilterResult.Confirmed)
    }
}
