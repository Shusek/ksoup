package com.fleeksoft.ksoup

internal expect object TestResourceSupport {
    val canReadResourceFiles: Boolean

    suspend fun readUrlBytes(url: String): ByteArray

    suspend fun readResourceBytes(resource: String, absolutePath: String): ByteArray

    suspend fun readCompressedResourceBytes(resource: String, absolutePath: String): ByteArray

    fun uncompressGzip(bytes: ByteArray): ByteArray
}
