package com.multistream.core.streaming

import kotlin.math.max

/**
 * Generic circular buffer of timestamped items, used to cache encoded chunks while a
 * connection is being re-established.
 *
 * Eviction happens on every [add]:
 *  - items older than [maxAgeSeconds] are dropped;
 *  - while the accumulated size reported by [sizeOf] exceeds [maxSizeMb], the oldest
 *    items are dropped.
 *
 * [timestamp] and [clock] must share the same time base. The default clock is
 * [System.currentTimeMillis], so callers passing media-relative timestamps must
 * provide a matching [clock].
 *
 * [sizeOf] defaults to reporting 0 bytes, which disables the byte cap and leaves only
 * age-based eviction. Pass a real measurer (for example `{ it.data.size }`) to honour
 * [maxSizeMb].
 */
class CircularBuffer<T>(
    private val maxSizeMb: Int = 10,
    private val maxAgeSeconds: Int = 5,
    private val sizeOf: (T) -> Int = { 0 },
    private val clock: () -> Long = System::currentTimeMillis
) {
    init {
        require(maxSizeMb > 0) { "maxSizeMb must be positive (was $maxSizeMb)" }
        require(maxAgeSeconds > 0) { "maxAgeSeconds must be positive (was $maxAgeSeconds)" }
    }

    private val maxSizeBytes = maxSizeMb.toLong() * 1024L * 1024L
    private val buffer = ArrayDeque<TimedItem<T>>()
    private var currentSizeBytes = 0L

    val size: Int
        @Synchronized get() = buffer.size

    @Synchronized
    fun add(item: T, timestamp: Long) {
        buffer.addLast(TimedItem(item, timestamp))
        currentSizeBytes += max(0, sizeOf(item))
        cleanupLocked()
    }

    @Synchronized
    fun lastKeyframe(isKeyframe: (T) -> Boolean): TimedItem<T>? =
        buffer.lastOrNull { isKeyframe(it.item) }

    /**
     * Returns the items from [item] (inclusive) to the end, or an empty list when
     * [item] is no longer in the buffer.
     */
    @Synchronized
    fun from(item: TimedItem<T>): List<T> {
        val index = buffer.indexOf(item)
        if (index < 0) return emptyList()
        return buffer.drop(index).map { it.item }
    }

    /** Snapshot of the buffered items, oldest first. */
    @Synchronized
    fun items(): List<TimedItem<T>> = buffer.toList()

    @Synchronized
    fun clear() {
        buffer.clear()
        currentSizeBytes = 0L
    }

    private fun cleanupLocked() {
        val now = clock()
        val maxAgeMillis = maxAgeSeconds * 1000L
        val expired = buffer.filter { now - it.timestamp > maxAgeMillis }
        if (expired.isNotEmpty()) {
            buffer.removeAll(expired)
            currentSizeBytes -= expired.sumOf { max(0, sizeOf(it.item)).toLong() }
        }
        while (buffer.isNotEmpty() && currentSizeBytes > maxSizeBytes) {
            currentSizeBytes -= max(0, sizeOf(buffer.removeFirst().item))
        }
        if (currentSizeBytes < 0L) currentSizeBytes = 0L
    }

    data class TimedItem<T>(val item: T, val timestamp: Long)
}
