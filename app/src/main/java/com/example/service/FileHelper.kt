package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.model.ReceivedFileRecord
import com.example.model.SelectedFileItem
import com.example.model.StorageSummary
import com.example.network.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

class FileHelper(private val context: Context) {

    fun getDropLinkAppDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "DropLink").apply { if (!exists()) mkdirs() }
    }

    fun getTempIncomingDir(): File {
        return File(context.cacheDir, "droplink_incoming").apply { if (!exists()) mkdirs() }
    }

    suspend fun resolveSelectedFiles(uris: List<Uri>): List<SelectedFileItem> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> resolveSingleUri(uri) }
    }

    private fun resolveSingleUri(uri: Uri): SelectedFileItem? {
        return try {
            var name = "file_${System.currentTimeMillis()}"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
            if (size <= 0L) {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) size = afd.length
                }
            }
            val mime = context.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            SelectedFileItem(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = HashUtils.sanitizeFileName(name),
                sizeBytes = size,
                mimeType = mime,
                category = HashUtils.categorizeFile(name, mime)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveFolderTree(treeUri: Uri): List<SelectedFileItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SelectedFileItem>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            traverseTree(treeUri, docId, result, depth = 0)
        } catch (_: Exception) {
        }
        result
    }

    private fun traverseTree(
        treeUri: Uri,
        parentDocId: String,
        out: MutableList<SelectedFileItem>,
        depth: Int
    ) {
        if (depth > 4 || out.size >= 200) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                if (out.size >= 200) break
                val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "file" else "file"
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "application/octet-stream" else "application/octet-stream"
                val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    traverseTree(treeUri, docId, out, depth + 1)
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val cleanName = HashUtils.sanitizeFileName(name)
                    out.add(
                        SelectedFileItem(
                            id = UUID.randomUUID().toString(),
                            uri = fileUri,
                            name = cleanName,
                            sizeBytes = size,
                            mimeType = mime,
                            category = HashUtils.categorizeFile(cleanName, mime)
                        )
                    )
                }
            }
        }
    }

    /**
     * Publishes a completed temp file into the user's configured destination:
     * 1) Custom SAF folder if set
     * 2) MediaStore.Downloads/DropLink on Android 10+
     * 3) App's external Downloads/DropLink folder on Android 7-9
     */
    suspend fun publishReceivedTempFile(
        tempFile: File,
        fileName: String,
        mimeType: String,
        customTreeUriString: String?
    ): Uri? = withContext(Dispatchers.IO) {
        val cleanName = HashUtils.sanitizeFileName(fileName)
        val effectiveMime = mimeType.ifBlank { "application/octet-stream" }

        if (!customTreeUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customTreeUriString)
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    effectiveMime,
                    cleanName
                )
                if (createdUri != null) {
                    context.contentResolver.openOutputStream(createdUri)?.use { out ->
                        FileInputStream(tempFile).use { input -> input.copyTo(out, 64 * 1024) }
                    }
                    tempFile.delete()
                    return@withContext createdUri
                }
            } catch (_: Exception) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                    put(MediaStore.Downloads.MIME_TYPE, effectiveMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DropLink")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        FileInputStream(tempFile).use { input -> input.copyTo(out, 64 * 1024) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    tempFile.delete()
                    return@withContext itemUri
                }
            } catch (_: Exception) {
            }
        }

        // Fallback to app external Downloads/DropLink
        return@withContext try {
            val destDir = getDropLinkAppDir()
            val destFile = uniqueDestFile(destDir, cleanName)
            FileInputStream(tempFile).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            tempFile.delete()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun uniqueDestFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var idx = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_($idx)$ext")
            idx++
        }
        return candidate
    }

    suspend fun getStorageSummary(customTreeUriString: String?): StorageSummary = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = (total - free).coerceAtLeast(0L)
        val files = listReceivedFiles(customTreeUriString)
        val dlBytes = files.sumOf { it.sizeBytes }
        StorageSummary(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used,
            dropLinkBytes = dlBytes,
            dropLinkFileCount = files.size
        )
    }

    suspend fun listReceivedFiles(customTreeUriString: String?): List<ReceivedFileRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ReceivedFileRecord>()

        // 1. Custom SAF folder if configured
        if (!customTreeUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customTreeUriString)
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext()) {
                        val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "file" else "file"
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "application/octet-stream" else "application/octet-stream"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                        val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                        val mod = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        list.add(
                            ReceivedFileRecord(
                                id = docId,
                                name = name,
                                sizeBytes = size,
                                mimeType = mime,
                                uri = uri,
                                lastModifiedMs = mod,
                                category = HashUtils.categorizeFile(name, mime)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. MediaStore Downloads/DropLink on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.MIME_TYPE,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_MODIFIED
                )
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
                val args = arrayOf("%DropLink%")
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Downloads.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
                    val modCol = cursor.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "file" else "file"
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "application/octet-stream" else "application/octet-stream"
                        val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                        val mod = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) * 1000L else 0L
                        val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                        list.add(
                            ReceivedFileRecord(
                                id = "ms-$id",
                                name = name,
                                sizeBytes = size,
                                mimeType = mime,
                                uri = uri,
                                lastModifiedMs = mod,
                                category = HashUtils.categorizeFile(name, mime)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. App external Downloads/DropLink fallback
        val appDir = getDropLinkAppDir()
        appDir.listFiles()?.filter { it.isFile }?.forEach { file ->
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            list.add(
                ReceivedFileRecord(
                    id = "app-${file.name}",
                    name = file.name,
                    sizeBytes = file.length(),
                    mimeType = mime,
                    uri = uri,
                    lastModifiedMs = file.lastModified(),
                    category = HashUtils.categorizeFile(file.name, mime)
                )
            )
        }

        list.sortedByDescending { it.lastModifiedMs }
    }

    fun openFile(uri: Uri, mimeType: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareFile(uri: Uri, mimeType: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openSaveFolder(customTreeUriString: String?): Boolean {
        if (!customTreeUriString.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(customTreeUriString), DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    fun openInputStream(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)
}
