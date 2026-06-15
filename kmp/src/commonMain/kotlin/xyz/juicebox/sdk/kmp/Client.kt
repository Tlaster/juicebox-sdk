package xyz.juicebox.sdk.kmp

import kotlin.coroutines.cancellation.CancellationException

/**
 * Register and recover PIN-protected secrets on behalf of a particular user.
 */
public class Client(
    configuration: Configuration,
    previousConfigurations: List<Configuration> = emptyList(),
    authTokens: Map<RealmId, AuthToken>? = null,
    authTokenProvider: AuthTokenProvider? = null,
) {
    private val platformClient: PlatformClient =
        createPlatformClient(
            configuration = configuration,
            previousConfigurations = previousConfigurations,
            authTokens = authTokens,
            authTokenProvider = authTokenProvider,
        )

    /**
     * Stores a new PIN-protected secret on the configured realms.
     */
    @Throws(RegisterException::class, CancellationException::class)
    public suspend fun register(
        pin: ByteArray,
        secret: ByteArray,
        info: ByteArray = ByteArray(0),
        numGuesses: Int,
    ) {
        platformClient.register(pin, secret, info, numGuesses)
    }

    /**
     * Retrieves a PIN-protected secret from the configured realms.
     */
    @Throws(RecoverException::class, CancellationException::class)
    public suspend fun recover(
        pin: ByteArray,
        info: ByteArray = ByteArray(0),
    ): ByteArray = platformClient.recover(pin, info)

    /**
     * Deletes the registered secret for this user, if any.
     */
    @Throws(DeleteException::class, CancellationException::class)
    public suspend fun delete() {
        platformClient.delete()
    }
}
