package com.agentkosticka.playbox.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PlayboxArchiveTest {
    @Test
    fun readsSingleManifestAndIgnoresOtherEntries() {
        val manifest = "{\"schema\":1}".toByteArray()
        val archive = archiveOf(
            "ignored.bin" to byteArrayOf(1, 2, 3),
            "manifest.json" to manifest,
        )

        assertArrayEquals(manifest, readPlayboxManifest(ByteArrayInputStream(archive)))
    }

    @Test
    fun rejectsMissingManifest() {
        val archive = archiveOf("other.json" to byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            readPlayboxManifest(ByteArrayInputStream(archive))
        }
    }

    @Test
    fun enforcesUncompressedManifestLimit() {
        val archive = archiveOf("manifest.json" to ByteArray(6) { 7 })

        assertThrows(IllegalArgumentException::class.java) {
            readPlayboxManifest(ByteArrayInputStream(archive), maxBytes = 5)
        }
    }

    private fun archiveOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
