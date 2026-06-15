package xyz.juicebox.sdk.kmp

internal class UnsupportedPlatformClient(
    private val targetName: String,
) : PlatformClient {
    override suspend fun register(
        pin: ByteArray,
        secret: ByteArray,
        info: ByteArray,
        numGuesses: Int,
    ): Nothing = unsupported()

    override suspend fun recover(
        pin: ByteArray,
        info: ByteArray,
    ): Nothing = unsupported()

    override suspend fun delete(): Nothing = unsupported()

    private fun unsupported(): Nothing {
        throw UnsupportedOperationException(
            "Juicebox KMP runtime for $targetName is not wired yet. " +
                "Use Android for now, or add the platform bridge backed by rust/sdk/bridge.",
        )
    }
}
