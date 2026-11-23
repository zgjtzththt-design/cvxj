package com.example.network

import com.example.model.FileCategory
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object HashUtils {

    fun sanitizeFileName(rawName: String?): String {
        val base = (rawName ?: "file_${System.currentTimeMillis()}")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\x00-\\x1F:*?\"<>|]"), "_")
            .trim()
        return when {
            base.isEmpty() || base == "." || base == ".." -> "droplink_file_${System.currentTimeMillis()}"
            else -> base.take(180)
        }
    }

    fun categorizeFile(name: String, mimeType: String?): FileCategory {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        val mime = (mimeType ?: "").lowercase(Locale.US)
        return when {
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg") ->
                FileCategory.IMAGE
            mime.startsWith("video/") || ext in setOf("mp4", "mkv", "mov", "avi", "webm", "3gp") ->
                FileCategory.VIDEO
            mime.startsWith("audio/") || ext in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus") ->
                FileCategory.AUDIO
            ext == "apk" || mime == "application/vnd.android.package-archive" ->
                FileCategory.APK
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") ||
                mime.contains("zip") || mime.contains("compressed") || mime.contains("archive") ->
                FileCategory.ARCHIVE
            mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") ||
                mime.contains("word") || mime.contains("excel") || mime.contains("powerpoint") ||
                ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml") ->
                FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format(Locale.US, if (digitGroups == 0) "%.0f %s" else "%.1f %s", value, units[digitGroups])
    }

    fun formatSpeed(bps: Long): String {
        if (bps <= 0L) return "0 B/s"
        return "${formatBytes(bps)}/s"
    }

    fun formatRemainingTime(seconds: Long): String {
        if (seconds <= 0L) return "0s"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun computeSha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
