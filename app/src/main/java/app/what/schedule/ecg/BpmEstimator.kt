package app.what.schedule.ecg

import kotlin.math.abs

/**
 * BPM estimator using derivative threshold and refractory period.
 * Tracks absolute sample index to compute RR in milliseconds.
 */
class BpmEstimator(
    private val samplingRateHz: Int,
) {
    private val refractoryMs: Int = 400
    private val refractorySamples: Int = (refractoryMs * samplingRateHz) / 1000
    private var samplesSincePeak: Int = refractorySamples

    private var lastSample: Float = 0f
    private var emaAbsDiff: Float = 0f
    private var diffThreshold: Float = 0.0f

    private var totalSamples: Long = 0
    private var lastPeakSampleIndex: Long = -refractorySamples.toLong()

    private var lastRrMs: Int? = null

    fun updateAndEstimateBpm(batch: FloatArray): Int? {
        if (batch.isEmpty()) return null
        var detectedBpm: Int? = null

        for (i in batch.indices) {
            val sample = batch[i]
            val diff = sample - lastSample
            lastSample = sample

            // Update EMA of absolute derivative to adapt threshold
            val absDiff = abs(diff)
            emaAbsDiff = if (emaAbsDiff == 0f) absDiff else (0.9f * emaAbsDiff + 0.1f * absDiff)
            diffThreshold = 3.0f * emaAbsDiff

            samplesSincePeak++
            val canDetect = samplesSincePeak >= refractorySamples

            if (canDetect && diff > diffThreshold) {
                val currentSampleIndex = totalSamples
                val deltaSamples = (currentSampleIndex - lastPeakSampleIndex).toInt()
                if (deltaSamples >= refractorySamples) {
                    val rrMs = (deltaSamples * 1000) / samplingRateHz
                    if (rrMs in 300..2500) { // 24-200 bpm range
                        lastRrMs = rrMs
                        detectedBpm = 60000 / rrMs
                    }
                    lastPeakSampleIndex = currentSampleIndex
                    samplesSincePeak = 0
                }
            }

            totalSamples++
        }
        return detectedBpm
    }

    fun lastRrMilliseconds(): Int? = lastRrMs
}


