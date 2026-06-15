package xyz.juicebox.sdk.kmp

import xyz.juicebox.sdk.AuthToken as AndroidAuthToken
import xyz.juicebox.sdk.Client as AndroidClient
import xyz.juicebox.sdk.Configuration as AndroidConfiguration
import xyz.juicebox.sdk.DeleteError as AndroidDeleteError
import xyz.juicebox.sdk.DeleteException as AndroidDeleteException
import xyz.juicebox.sdk.RealmId as AndroidRealmId
import xyz.juicebox.sdk.RecoverError as AndroidRecoverError
import xyz.juicebox.sdk.RecoverException as AndroidRecoverException
import xyz.juicebox.sdk.RegisterError as AndroidRegisterError
import xyz.juicebox.sdk.RegisterException as AndroidRegisterException

internal actual fun createPlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
): PlatformClient =
    AndroidPlatformClient(
        configuration = configuration,
        previousConfigurations = previousConfigurations,
        authTokens = authTokens,
        authTokenProvider = authTokenProvider,
    )

private class AndroidPlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
) : PlatformClient {
    private val delegate: AndroidClient

    init {
        if (authTokenProvider != null && authTokens == null) {
            AndroidClient.fetchAuthTokenCallback = { realmId ->
                authTokenProvider
                    .getAuthToken(RealmId(realmId.bytes.copyOf()))
                    ?.let { AndroidAuthToken(it.jwt) }
            }
        }
        delegate =
            AndroidClient(
                configuration = AndroidConfiguration(configuration.json),
                previousConfigurations =
                    previousConfigurations
                        .map { AndroidConfiguration(it.json) }
                        .toTypedArray(),
                authTokens =
                    authTokens?.mapKeys { (realmId, _) ->
                        AndroidRealmId(realmId.bytes.copyOf())
                    }?.mapValues { (_, token) ->
                        AndroidAuthToken(token.jwt)
                    },
            )
    }

    override suspend fun register(
        pin: ByteArray,
        secret: ByteArray,
        info: ByteArray,
        numGuesses: Int,
    ) {
        try {
            delegate.register(pin, secret, info, numGuesses.toShort())
        } catch (e: AndroidRegisterException) {
            throw RegisterException(e.error.toCommon(), e)
        }
    }

    override suspend fun recover(
        pin: ByteArray,
        info: ByteArray,
    ): ByteArray =
        try {
            delegate.recover(pin, info)
        } catch (e: AndroidRecoverException) {
            throw RecoverException(e.error.toCommon(), e.guessesRemaining, e)
        }

    override suspend fun delete() {
        try {
            delegate.delete()
        } catch (e: AndroidDeleteException) {
            throw DeleteException(e.error.toCommon(), e)
        }
    }
}

private fun AndroidRegisterError.toCommon(): RegisterError = RegisterError.valueOf(name)

private fun AndroidRecoverError.toCommon(): RecoverError = RecoverError.valueOf(name)

private fun AndroidDeleteError.toCommon(): DeleteError = DeleteError.valueOf(name)
