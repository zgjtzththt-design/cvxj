package com.example

import com.example.model.FileCategory
import com.example.network.HashUtils
import com.example.network.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DropLinkEngineTest {

    @Test
    fun sanitizeFileName_blocksTraversalAndIllegalChars() {
        assertEquals("secret.txt", HashUtils.sanitizeFileName("../../etc/secret.txt"))
        assertEquals("report_2026.pdf", HashUtils.sanitizeFileName("report:2026.pdf"))
        assertTrue(HashUtils.sanitizeFileName("..").startsWith("droplink_file_"))
    }

    @Test
    fun categorizeFile_mapsExtensionsAndMimeTypes() {
        assertEquals(FileCategory.APK, HashUtils.categorizeFile("app-release.apk", "application/octet-stream"))
        assertEquals(FileCategory.ARCHIVE, HashUtils.categorizeFile("backup.zip", "application/zip"))
        assertEquals(FileCategory.IMAGE, HashUtils.categorizeFile("photo.jpg", "image/jpeg"))
        assertEquals(FileCategory.VIDEO, HashUtils.categorizeFile("clip.mp4", "video/mp4"))
        assertEquals(FileCategory.DOCUMENT, HashUtils.categorizeFile("notes.pdf", "application/pdf"))
    }

    @Test
    fun computeSha256_producesExpectedDigest() {
        val input = ByteArrayInputStream("DropLink-Verify".toByteArray(Charsets.UTF_8))
        val hash1 = HashUtils.computeSha256(input)
        val hash2 = HashUtils.computeSha256(ByteArrayInputStream("DropLink-Verify".toByteArray(Charsets.UTF_8)))
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
    }

    @Test
    fun constantTimeEquals_checksTokenCorrectly() {
        assertTrue(HashUtils.constantTimeEquals("A1B2C3D4", "A1B2C3D4"))
        assertFalse(HashUtils.constantTimeEquals("A1B2C3D4", "A1B2C3D5"))
        assertFalse(HashUtils.constantTimeEquals("ABC", "ABCD"))
    }

    @Test
    fun parseQrPayload_parsesJsonAndHostPort() {
        val jsonQr = """{"host":"192.168.1.44","port":8765,"token":"CAFE1234","name":"Pixel 9","mode":"receive"}"""
        val parsed = NetworkUtils.parseQrPayload(jsonQr)
        assertNotNull(parsed)
        assertEquals("192.168.1.44", parsed?.host)
        assertEquals(8765, parsed?.port)
        assertEquals("CAFE1234", parsed?.token)
        assertEquals("Pixel 9", parsed?.name)

        val manual = NetworkUtils.parseQrPayload("192.168.49.1:8765")
        assertNotNull(manual)
        assertEquals("192.168.49.1", manual?.host)
        assertEquals(8765, manual?.port)
    }
}
