package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.workout.WorkoutModule
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertSame
import org.junit.Test

class WorkoutInteractorsTest {
    @Test
    fun moduleProvidesTheSameThreeUseCases() {
        val clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
        val start = StartWorkoutUseCase(
            unused(),
            unused(),
            unused(),
            clock,
        )
        val logSet = LogSetUseCase(unused(), clock)
        val complete = CompleteWorkoutUseCase(
            unused(),
            unused(),
            unused(),
            unused(),
            clock,
        )

        val provided = WorkoutModule.provideWorkoutInteractors(start, logSet, complete)

        assertSame(start, provided.start)
        assertSame(logSet, provided.logSet)
        assertSame(complete, provided.complete)
    }
}

private inline fun <reified T : Any> unused(): T {
    val type = T::class.java
    return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, _, _ ->
        error("unused")
    } as T
}
