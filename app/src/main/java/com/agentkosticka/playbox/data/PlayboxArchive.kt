package com.agentkosticka.playbox.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal const val PLAYBOX_SCHEMA_VERSION = 1
internal const val MAX_PLAYBOX_MANIFEST_BYTES = 2_000_000

/** Reads the single manifest from a .playbox archive with a strict uncompressed-size cap. */
internal fun readPlayboxManifest(
    input: InputStream,
    maxBytes: Int = MAX_PLAYBOX_MANIFEST_BYTES,
): ByteArray {
    require(maxBytes > 0)
    ZipInputStream(input).use { zip ->
        var result: ByteArray? = null
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name == "manifest.json") {
                require(result == null) { "Effect file contains multiple manifests" }
                val sink = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = zip.read(buffer)
                    if (count <= 0) break
                    total += count
                    require(total <= maxBytes) { "Effect file is too large" }
                    sink.write(buffer, 0, count)
                }
                result = sink.toByteArray()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return requireNotNull(result) { "Effect file is missing manifest.json" }
    }
}
