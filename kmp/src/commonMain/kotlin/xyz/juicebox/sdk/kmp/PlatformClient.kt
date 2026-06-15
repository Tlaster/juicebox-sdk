package xyz.juicebox.sdk.kmp

internal interface PlatformClient {
    suspend fun register(
        pin: ByteArray,
        secret: ByteArray,
        info: ByteArray,
        numGuesses: Int,
    )

    suspend fun recover(
        pin: ByteArray,
        info: ByteArray,
    ): ByteArray

    suspend fun delete()
}

internal expect fun createPlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
): PlatformClient
