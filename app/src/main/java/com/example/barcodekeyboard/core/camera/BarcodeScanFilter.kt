package com.example.barcodekeyboard.core.camera

/**
 * Pure Kotlin decision filter for barcode scanning.
 * Encapsulates:
 * - Anti-duplicate LRU cache (prevents duplicate and triple scans)
 * - Multi-frame candidate stability (Sensitivity Throttling)
 * - Minimum interval between any scans
 * - Temporary pause/cooldown after a scan
 */
class BarcodeScanFilter(
    var debounceDelayMs: Long = DEFAULT_DEBOUNCE_INTERVAL_SAME_CODE_MS,
    var minIntervalBetweenScansMs: Long = MIN_INTERVAL_BETWEEN_SCANS_MS,
    var requiredFrames: Int = DEFAULT_REQUIRED_FRAMES,
    var pauseDurationMs: Long = DEFAULT_SUCCESS_PAUSE_DURATION_MS
) {
    companion object {
        const val DEFAULT_DEBOUNCE_INTERVAL_SAME_CODE_MS = 3500L
        const val MIN_INTERVAL_BETWEEN_SCANS_MS = 1200L
        const val DEFAULT_SUCCESS_PAUSE_DURATION_MS = 900L
        const val DEFAULT_REQUIRED_FRAMES = 2
        const val MAX_RECENT_CACHE_SIZE = 20
    }

    private val recentScans = LinkedHashMap<String, Long>()
    private var lastAnyScanTime: Long = 0L
    private var pauseUntilTime: Long = 0L

    var candidateText: String = ""
        private set
    var candidateCount: Int = 0
        private set

    sealed class FilterResult {
        object InPauseCooldown : FilterResult()
        object DuplicateDebounced : FilterResult()
        object MinIntervalThrottled : FilterResult()
        data class AwaitingFrames(val current: Int, val required: Int) : FilterResult()
        data class Confirmed(val text: String, val timestamp: Long) : FilterResult()
    }

    fun isPaused(currentTime: Long): Boolean = currentTime < pauseUntilTime

    fun pause(currentTime: Long, durationMs: Long = pauseDurationMs) {
        pauseUntilTime = currentTime + durationMs
        candidateText = ""
        candidateCount = 0
    }

    fun resetState() {
        recentScans.clear()
        lastAnyScanTime = 0L
        pauseUntilTime = 0L
        candidateText = ""
        candidateCount = 0
    }

    fun processCandidate(rawValue: String, currentTime: Long): FilterResult {
        if (isPaused(currentTime)) {
            return FilterResult.InPauseCooldown
        }

        if (rawValue.isBlank()) {
            candidateText = ""
            candidateCount = 0
            return FilterResult.AwaitingFrames(0, requiredFrames)
        }

        // 1. Check duplicate scan for this identical barcode
        val lastScannedTime = recentScans[rawValue] ?: 0L
        if (currentTime - lastScannedTime < debounceDelayMs) {
            return FilterResult.DuplicateDebounced
        }

        // 2. Check minimum interval between ANY scans
        if (currentTime - lastAnyScanTime < minIntervalBetweenScansMs) {
            return FilterResult.MinIntervalThrottled
        }

        // 3. Multi-frame candidate stability
        if (requiredFrames > 1) {
            if (rawValue == candidateText) {
                candidateCount++
            } else {
                candidateText = rawValue
                candidateCount = 1
            }

            if (candidateCount < requiredFrames) {
                return FilterResult.AwaitingFrames(candidateCount, requiredFrames)
            }
        }

        // 4. Confirmed!
        candidateText = ""
        candidateCount = 0

        recentScans[rawValue] = currentTime
        if (recentScans.size > MAX_RECENT_CACHE_SIZE) {
            val oldestKey = recentScans.keys.firstOrNull()
            if (oldestKey != null) {
                recentScans.remove(oldestKey)
            }
        }
        lastAnyScanTime = currentTime
        pauseUntilTime = currentTime + pauseDurationMs

        return FilterResult.Confirmed(rawValue, currentTime)
    }
}
