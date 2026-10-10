package com.forge.hypertrophy.data.transfer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Built-in exercise and skill catalog. Ships in every build as
 * [ASSET_NAME]; it has no training days.
 */
interface LibraryCatalogProvider {
    suspend fun catalog(): ProgramJson
}

class AssetLibraryCatalogProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LibraryCatalogProvider {
    override suspend fun catalog(): ProgramJson = withContext(Dispatchers.IO) {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            parse(reader.readText())
        }
    }

    companion object {
        const val ASSET_NAME: String = "library.json"

        fun parse(json: String): ProgramJson = ProgramJsonFormat.decodeFromString(json)
    }
}
