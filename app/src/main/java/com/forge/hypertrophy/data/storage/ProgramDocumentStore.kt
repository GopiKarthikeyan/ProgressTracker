package com.forge.hypertrophy.data.storage

import android.content.Context
import androidx.core.net.toUri
import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.ProgramJsonFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/** Reads and writes a program document at a URI the user picked. */
interface ProgramDocumentStore {
    suspend fun read(uri: String): ProgramJson

    suspend fun write(uri: String, document: ProgramJson)
}

/**
 * One-shot access through the content resolver. The stream is decoded and
 * encoded directly; no persistable URI permission is taken.
 */
@Singleton
class ContentResolverProgramDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProgramDocumentStore {
    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun read(uri: String): ProgramJson = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri.toUri())
            ?: error("Could not open $uri for reading")
        stream.use { ProgramJsonFormat.decodeFromStream<ProgramJson>(it) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun write(uri: String, document: ProgramJson) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt")
            ?: error("Could not open $uri for writing")
        stream.use { ProgramJsonFormat.encodeToStream(document, it) }
    }
}
