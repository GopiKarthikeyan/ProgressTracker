package com.forge.hypertrophy.cardio

class BatchBuffer<T>(
    private val size: Int,
    private val flush: suspend (List<T>) -> Unit,
) {
    private val pending = mutableListOf<T>()

    suspend fun add(item: T) {
        pending += item
        if (pending.size >= size) drain()
    }

    suspend fun drain() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        flush(batch)
    }
}
