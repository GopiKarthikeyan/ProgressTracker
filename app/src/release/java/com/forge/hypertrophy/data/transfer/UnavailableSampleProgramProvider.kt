package com.forge.hypertrophy.data.transfer

import javax.inject.Inject

class UnavailableSampleProgramProvider @Inject constructor() : SampleProgramProvider {
    override val available: Boolean = false

    override suspend fun program(): ProgramJson {
        error("The sample program is not part of release builds")
    }
}
