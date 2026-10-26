package com.multistream.core.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircularBufferTest {

    private var now = 1_000_000L

    @Test
    fun `add stores items with timestamp`() {
        val buffer = CircularBuffer<String>(clock = { now })

        buffer.add("chunk", now - 500)

        assertEquals(1, buffer.size)
        assertEquals(
            CircularBuffer.TimedItem("chunk", now - 500),
            buffer.items().single()
        )
    }

    @Test
    fun `cleanup removes old items`() {
        val buffer = CircularBuffer<String>(maxAgeSeconds = 5, clock = { now })
        buffer.add("old", now)

        now += 6_000
        buffer.add("fresh", now)

        assertEquals(listOf("fresh"), buffer.items().map { it.item })
    }

    @Test
    fun `lastKeyframe finds most recent keyframe`() {
        val buffer = CircularBuffer<String>(clock = { now })
        buffer.add("I1", now)
        buffer.add("P1", now)
        buffer.add("I2", now)
        buffer.add("P2", now)

        val keyframe = buffer.lastKeyframe { it.startsWith("I") }

        assertEquals("I2", keyframe?.item)
        assertEquals(now, keyframe?.timestamp)
    }

    @Test
    fun `from returns items starting from specified item`() {
        val buffer = CircularBuffer<String>(clock = { now })
        buffer.add("I1", now)
        buffer.add("P1", now)
        buffer.add("I2", now)
        buffer.add("P2", now)

        val keyframe = buffer.lastKeyframe { it.startsWith("I") }!!

        assertEquals(listOf("I2", "P2"), buffer.from(keyframe))
    }

    @Test
    fun `from returns empty list for unknown item`() {
        val buffer = CircularBuffer<String>(clock = { now })
        buffer.add("I1", now)

        val unknown = CircularBuffer.TimedItem("missing", now)

        assertTrue(buffer.from(unknown).isEmpty())
    }

    @Test
    fun `empty buffer returns null for lastKeyframe`() {
        val buffer = CircularBuffer<String>(clock = { now })

        assertNull(buffer.lastKeyframe { true })
    }

    @Test
    fun `buffer respects maxAgeSeconds`() {
        val buffer = CircularBuffer<String>(maxAgeSeconds = 5, clock = { now })
        buffer.add("a", now)

        now += 3_000
        buffer.add("b", now)

        now += 3_000
        buffer.add("c", now)

        // "a" is 6s old at this point; "b" (3s) and "c" (0s) are still inside the window.
        assertEquals(listOf("b", "c"), buffer.items().map { it.item })
    }

    @Test
    fun `buffer evicts oldest items when maxSizeMb is exceeded`() {
        val halfMb = 512 * 1024
        val buffer = CircularBuffer<String>(
            maxSizeMb = 1,
            maxAgeSeconds = 60,
            sizeOf = { halfMb },
            clock = { now }
        )

        buffer.add("a", now)
        buffer.add("b", now)
        buffer.add("c", now)

        assertEquals(listOf("b", "c"), buffer.items().map { it.item })
    }
}
