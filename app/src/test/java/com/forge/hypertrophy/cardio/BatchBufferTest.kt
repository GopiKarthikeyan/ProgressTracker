package com.forge.hypertrophy.cardio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchBufferTest {
    @Test
    fun flushesFullBatchesAndTheRemainder() = runBlocking {
        val flushed = mutableListOf<List<Int>>()
        val buffer = BatchBuffer<Int>(size = 20) { flushed += it }
        repeat(25) { buffer.add(it) }
        assertEquals(listOf((0 until 20).toList()), flushed)
        buffer.drain()
        assertEquals(listOf((0 until 20).toList(), (20 until 25).toList()), flushed)
    }
}
