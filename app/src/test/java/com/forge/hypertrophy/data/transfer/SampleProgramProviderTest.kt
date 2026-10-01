package com.forge.hypertrophy.data.transfer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class SampleProgramProviderTest {
    @Test
    fun debugProviderParsesTheSampleProgram() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = AssetSampleProgramProvider(context)
        assertTrue(provider.available)
        val fromAsset = runCatching { context.assets.open(AssetSampleProgramProvider.ASSET_NAME) }
        println("sample asset readable from unit test: ${fromAsset.isSuccess}")
        val json = fromAsset.map {
            it.bufferedReader().use { reader -> reader.readText() }
        }.getOrElse {
            repoFile("docs/program.json").readText()
        }

        val program = AssetSampleProgramProvider.parse(json)

        assertEquals(3, program.skills.size)
        assertEquals(50, program.exercises.size)
        assertEquals(7, program.days.size)
        assertEquals(57, program.days.sumOf { it.slots.size })
        assertEquals(1, program.days.sumOf { day -> day.slots.sumOf { it.alternativeExerciseKeys.size } })
        assertEquals(3, program.days.sumOf { it.cardio.size })
        assertEquals(
            35,
            program.days.sumOf { day ->
                (day.prep?.items?.size ?: 0) + (day.cooldown?.items?.size ?: 0)
            },
        )
    }
}
