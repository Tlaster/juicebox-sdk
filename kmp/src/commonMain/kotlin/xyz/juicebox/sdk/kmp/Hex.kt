package xyz.juicebox.sdk.kmp

internal fun ByteArray.encodeHex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

internal fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "hex string must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
