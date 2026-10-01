package com.forge.hypertrophy.ui.screens.routine

internal fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T>? {
    if (from !in items.indices || to !in items.indices || from == to) return null
    val next = items.toMutableList()
    next.add(to, next.removeAt(from))
    return next
}
