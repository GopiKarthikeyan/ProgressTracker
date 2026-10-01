package com.forge.hypertrophy.data.transfer

/**
 * Debug builds can load the bundled sample. Release builds report
 * [available] false and do not read an asset.
 *
 * [program] returns the parsed [ProgramJson], not the raw string.
 */
interface SampleProgramProvider {
    val available: Boolean

    suspend fun program(): ProgramJson
}
