package xyz.juicebox.sdk.kmp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.juicebox.sdk.AuthToken as JvmAuthToken
import xyz.juicebox.sdk.Configuration as JvmConfiguration
import xyz.juicebox.sdk.DeleteError as JvmDeleteError
import xyz.juicebox.sdk.DeleteException as JvmDeleteException
import xyz.juicebox.sdk.RealmId as JvmRealmId
import xyz.juicebox.sdk.RecoverError as JvmRecoverError
import xyz.juicebox.sdk.RecoverException as JvmRecoverException
import xyz.juicebox.sdk.RegisterError as JvmRegisterError
import xyz.juicebox.sdk.RegisterException as JvmRegisterException
import xyz.juicebox.sdk.internal.Native
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

internal actual fun createPlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
): PlatformClient =
    JvmPlatformClient(
        configuration = configuration,
        previousConfigurations = previousConfigurations,
        authTokens = authTokens,
        authTokenProvider = authTokenProvider,
    )

private class JvmPlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    private val authTokenProvider: AuthTokenProvider?,
) : PlatformClient {
    private val tokenMap =
        authTokens
            .orEmpty()
            .mapKeys { (realmId, _) -> JvmRealmId(realmId.bytes.copyOf()) }
            .mapValues { (_, token) -> JvmAuthToken(token.jwt) }
    private val native: Long =
        Native.clientCreate(
            JvmConfiguration(configuration.json).native,
            previousConfigurations.map { JvmConfiguration(it.json).native }.toLongArray(),
            Native.GetAuthTokenFn { context, contextId, realmId ->
                thread(name = "juicebox-auth-token") {
                    val token =
                        tokenMap[realmId]
                            ?: authTokenProvider
                                ?.getAuthToken(RealmId(realmId.bytes.copyOf()))
                                ?.let { JvmAuthToken(it.jwt) }
                    Native.authTokenGetComplete(context, contextId, token?.native ?: 0)
                }
            },
            Native.HttpSendFn { httpClient, request ->
                thread(name = "juicebox-http") {
                    Native.httpClientRequestComplete(httpClient, request.execute())
                }
            },
        )

    override suspend fun register(
        pin: ByteArray,
        secret: ByteArray,
        info: ByteArray,
        numGuesses: Int,
    ) {
        withContext(Dispatchers.IO) {
            try {
                Native.clientRegister(native, pin, secret, info, numGuesses.toShort())
            } catch (e: JvmRegisterException) {
                throw RegisterException(e.error.toCommon(), e)
            }
        }
    }

    override suspend fun recover(
        pin: ByteArray,
        info: ByteArray,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                Native.clientRecover(native, pin, info)
            } catch (e: JvmRecoverException) {
                throw RecoverException(e.error.toCommon(), e.guessesRemaining, e)
            }
        }

    override suspend fun delete() {
        withContext(Dispatchers.IO) {
            try {
                Native.clientDelete(native)
            } catch (e: JvmDeleteException) {
                throw DeleteException(e.error.toCommon(), e)
            }
        }
    }

    @Suppress("deprecation")
    protected fun finalize() {
        Native.clientDestroy(native)
    }
}

private fun Native.HttpRequest.execute(): Native.HttpResponse {
    val response = Native.HttpResponse()
    response.id = id
    return try {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("User-Agent", "JuiceboxSdk-JVM/${Native.sdkVersion()}")
        connection.setRequestProperty("X-Juicebox-Version", Native.sdkVersion())
        headers?.forEach { header ->
            header?.let { connection.setRequestProperty(it.name, it.value) }
        }
        connection.doInput = true
        body?.let {
            connection.doOutput = true
            connection.outputStream.write(it)
        }
        response.statusCode = connection.responseCode.toShort()
        response.headers =
            connection.headerFields
                .filterKeys { it != null }
                .map { (key, values) -> Native.HttpHeader(key, values.joinToString(",")) }
                .toTypedArray()
        response.body =
            if (response.statusCode == 200.toShort()) {
                connection.inputStream.readBytes()
            } else {
                connection.errorStream?.readBytes() ?: ByteArray(0)
            }
        response
    } catch (_: Throwable) {
        response.statusCode = (-1).toShort()
        response.headers = emptyArray()
        response.body = ByteArray(0)
        response
    }
}

private fun JvmRegisterError.toCommon(): RegisterError = RegisterError.valueOf(name)

private fun JvmRecoverError.toCommon(): RecoverError = RecoverError.valueOf(name)

private fun JvmDeleteError.toCommon(): DeleteError = DeleteError.valueOf(name)
