package com.forge.hypertrophy.data.backup

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BackupFolders {
    fun persist(uri: String)

    suspend fun write(treeUri: String, displayName: String, body: suspend (OutputStream) -> Unit)
}

@Singleton
class SafBackupFolders @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupFolders {
    override fun persist(uri: String) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri.toUri(), flags)
    }

    override suspend fun write(treeUri: String, displayName: String, body: suspend (OutputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            val tree = treeUri.toUri()
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                "application/zip",
                displayName,
            ) ?: error("Could not create the backup in the chosen folder")
            val stream = context.contentResolver.openOutputStream(created)
                ?: error("Could not open the backup in the chosen folder")
            stream.use { body(it) }
        }
    }
}
