package com.fleeksoft.ksoup

public enum class PlatformType {
    ANDROID,
    ANDROID_NATIVE,
    JVM,
    IOS,
    LINUX,
    JS,
    MAC,
    WINDOWS,
    WASM_JS,
    WASM_WASI,
}

public expect object Platform {
    public val current: PlatformType
}
