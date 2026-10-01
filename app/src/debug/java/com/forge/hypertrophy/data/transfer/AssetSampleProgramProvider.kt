package com.forge.hypertrophy.data.transfer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetSampleProgramProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SampleProgramProvider {
    override val available: Boolean = true

    override suspend fun program(): ProgramJson = withContext(Dispatchers.IO) {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            parse(reader.readText())
        }
    }

    companion object {
        const val ASSET_NAME: String = "program.json"

        fun parse(json: String): ProgramJson = ProgramJsonFormat.decodeFromString(json)
    }
}
