package com.fleeksoft.ksoup

internal actual object TestResourceSupport {
    actual val canReadResourceFiles: Boolean = false

    actual suspend fun readUrlBytes(url: String): ByteArray =
        unsupported("URL test resources")

    actual suspend fun readResourceBytes(resource: String, absolutePath: String): ByteArray =
        WasmWasiTestResources.read(resource)

    actual suspend fun readCompressedResourceBytes(resource: String, absolutePath: String): ByteArray =
        WasmWasiTestResources.readUncompressed(resource)

    actual fun uncompressGzip(bytes: ByteArray): ByteArray =
        unsupported("Compressed test resources")

    private fun unsupported(feature: String): Nothing =
        throw UnsupportedOperationException("$feature are not supported on wasmWasi")
}
