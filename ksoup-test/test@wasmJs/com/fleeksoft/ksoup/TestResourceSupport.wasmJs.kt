package com.fleeksoft.ksoup

import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.uncompress
import korlibs.io.file.std.uniVfs
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

internal actual object TestResourceSupport {
    actual val canReadResourceFiles: Boolean = false

    actual suspend fun readUrlBytes(url: String): ByteArray = url.uniVfs.readAll()

    actual suspend fun readResourceBytes(resource: String, absolutePath: String): ByteArray =
        SystemFileSystem.source(Path(absolutePath)).buffered().readByteArray()

    actual suspend fun readCompressedResourceBytes(resource: String, absolutePath: String): ByteArray =
        uncompressGzip(readResourceBytes(resource, absolutePath))

    actual fun uncompressGzip(bytes: ByteArray): ByteArray = bytes.uncompress(GZIP)
}
