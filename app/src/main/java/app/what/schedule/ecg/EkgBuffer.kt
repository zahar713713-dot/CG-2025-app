package app.what.schedule.ecg

import kotlin.math.min

/**
 * A simple lock-free circular buffer for ECG samples.
 * The buffer stores the latest [capacity] samples, dropping the oldest when full.
 */
class EkgBuffer(
    private val capacity: Int
) {
    private val data: FloatArray = FloatArray(capacity)
    private var nextWriteIndex: Int = 0
    private var currentSize: Int = 0

    fun append(samples: FloatArray) {
        if (samples.isEmpty()) return
        var remaining = samples.size
        var srcIndex = 0
        while (remaining > 0) {
            val spaceToEnd = capacity - nextWriteIndex
            val toCopy = min(remaining, spaceToEnd)
            System.arraycopy(samples, srcIndex, data, nextWriteIndex, toCopy)
            nextWriteIndex = (nextWriteIndex + toCopy) % capacity
            srcIndex += toCopy
            remaining -= toCopy
            currentSize = min(capacity, currentSize + toCopy)
        }
    }

    /**
     * Returns a copy of current contents in chronological order (oldest -> newest).
     */
    fun snapshot(): FloatArray {
        if (currentSize == 0) return FloatArray(0)
        val result = FloatArray(currentSize)
        val startIndex = if (currentSize == capacity) nextWriteIndex else 0
        val firstChunk = min(currentSize, capacity - startIndex)
        System.arraycopy(data, startIndex, result, 0, firstChunk)
        if (firstChunk < currentSize) {
            System.arraycopy(data, 0, result, firstChunk, currentSize - firstChunk)
        }
        return result
    }
}


