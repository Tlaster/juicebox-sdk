package xyz.juicebox.sdk.kmp

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.js

internal actual fun createPlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
): PlatformClient =
    WasmPlatformClient(
        configuration = configuration,
        previousConfigurations = previousConfigurations,
        authTokens = authTokens,
        authTokenProvider = authTokenProvider,
    )

private class WasmPlatformClient(
    private val configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
) : PlatformClient {
    private val previousConfigurationJsons = previousConfigurations.map { it.json }
    private val tokenSource =
        TokenSource(
            tokens = authTokens.orEmpty(),
            provider = authTokenProvider,
        )
    private var client: JsAny? = null

    override suspend fun register(
        pin: ByteArray,
        secret: ByteArray,
        info: ByteArray,
        numGuesses: Int,
    ) {
        require(numGuesses in 0..UShort.MAX_VALUE.toInt()) {
            "numGuesses must be between 0 and ${UShort.MAX_VALUE}"
        }

        try {
            awaitJsPromise(
                jsClientRegister(
                    client = getClient(),
                    pin = pin.toUint8Array(),
                    secret = secret.toUint8Array(),
                    info = info.toUint8Array(),
                    numGuesses = numGuesses,
                ),
            )
        } catch (e: WasmJsException) {
            throw RegisterException(e.error.toRegisterError(), e)
        }
    }

    override suspend fun recover(
        pin: ByteArray,
        info: ByteArray,
    ): ByteArray =
        try {
            val secret =
                awaitJsPromise(
                    jsClientRecover(
                        client = getClient(),
                        pin = pin.toUint8Array(),
                        info = info.toUint8Array(),
                    ),
                )
            secret?.toByteArray() ?: ByteArray(0)
        } catch (e: WasmJsException) {
            throw RecoverException(
                error = e.error.toRecoverError(),
                guessesRemaining = e.error.recoverGuessesRemaining()?.toShort(),
                cause = e,
            )
        }

    override suspend fun delete() {
        try {
            awaitJsPromise(jsClientDelete(getClient()))
        } catch (e: WasmJsException) {
            throw DeleteException(e.error.toDeleteError(), e)
        }
    }

    private suspend fun getClient(): JsAny {
        installAuthTokenCallback { realmIdArray ->
            tokenSource
                .get(RealmId(realmIdArray.toByteArray(expectedLength = 16)))
                ?.jwt
        }

        client?.let { return it }

        val module = checkNotNull(awaitJsPromise(juiceboxModulePromise)) {
            "juicebox-sdk module import returned null"
        }
        val previousConfigurations = createJsArray()
        previousConfigurationJsons.forEach { json ->
            pushJsArray(previousConfigurations, newJuiceboxConfiguration(module, json))
        }

        return newJuiceboxClient(
            module = module,
            configuration = newJuiceboxConfiguration(module, configuration.json),
            previousConfigurations = previousConfigurations,
        ).also { client = it }
    }
}

private class TokenSource(
    private val tokens: Map<RealmId, AuthToken>,
    private val provider: AuthTokenProvider?,
) {
    fun get(realmId: RealmId): AuthToken? =
        tokens[realmId] ?: provider?.getAuthToken(realmId)
}

private class WasmJsException(
    val error: JsAny?,
) : Exception(jsErrorMessage(error))

private val juiceboxModulePromise: JsAny = importJuiceboxModule()

private suspend fun awaitJsPromise(promise: JsAny): JsAny? =
    suspendCancellableCoroutine { continuation: CancellableContinuation<JsAny?> ->
        promiseThen(
            promise = promise,
            onResolved = { value -> continuation.resume(value) },
            onRejected = { error -> continuation.resumeWithException(WasmJsException(error)) },
        )
    }

private fun ByteArray.toUint8Array(): JsAny {
    val array = createUint8Array(size)
    forEachIndexed { index, byte ->
        setUint8(array, index, byte.toInt() and 0xff)
    }
    return array
}

private fun JsAny.toByteArray(
    expectedLength: Int? = null,
): ByteArray {
    val length = expectedLength ?: uint8ArrayLength(this)
    return ByteArray(length) { index -> getUint8(this, index).toByte() }
}

private fun JsAny?.toRegisterError(): RegisterError =
    when (jsErrorCode(this)) {
        0 -> RegisterError.INVALID_AUTH
        1 -> RegisterError.UPGRADE_REQUIRED
        2 -> RegisterError.RATE_LIMIT_EXCEEDED
        4 -> RegisterError.TRANSIENT
        else -> RegisterError.ASSERTION
    }

private fun JsAny?.toRecoverError(): RecoverError =
    when (jsErrorCode(this)) {
        0 -> RecoverError.INVALID_PIN
        1 -> RecoverError.NOT_REGISTERED
        2 -> RecoverError.INVALID_AUTH
        3 -> RecoverError.UPGRADE_REQUIRED
        4 -> RecoverError.RATE_LIMIT_EXCEEDED
        6 -> RecoverError.TRANSIENT
        else -> RecoverError.ASSERTION
    }

private fun JsAny?.toDeleteError(): DeleteError =
    when (jsErrorCode(this)) {
        0 -> DeleteError.INVALID_AUTH
        1 -> DeleteError.UPGRADE_REQUIRED
        2 -> DeleteError.RATE_LIMIT_EXCEEDED
        4 -> DeleteError.TRANSIENT
        else -> DeleteError.ASSERTION
    }

private fun JsAny?.recoverGuessesRemaining(): Int? {
    val value = jsRecoverGuessesRemaining(this)
    return if (value < 0) null else value
}

private fun importJuiceboxModule(): JsAny = js("import('juicebox-sdk')")

private fun promiseThen(
    promise: JsAny,
    onResolved: (JsAny?) -> Unit,
    onRejected: (JsAny?) -> Unit,
): Unit = js("promise.then(onResolved, onRejected)")

private fun newJuiceboxConfiguration(
    module: JsAny,
    json: String,
): JsAny = js("new module.Configuration(json)")

private fun newJuiceboxClient(
    module: JsAny,
    configuration: JsAny,
    previousConfigurations: JsAny,
): JsAny = js("new module.Client(configuration, previousConfigurations)")

private fun jsClientRegister(
    client: JsAny,
    pin: JsAny,
    secret: JsAny,
    info: JsAny,
    numGuesses: Int,
): JsAny = js("client.register(pin, secret, info, numGuesses)")

private fun jsClientRecover(
    client: JsAny,
    pin: JsAny,
    info: JsAny,
): JsAny = js("client.recover(pin, info)")

private fun jsClientDelete(client: JsAny): JsAny = js("client.delete()")

private fun installAuthTokenCallback(callback: (JsAny) -> String?): Unit =
    js("globalThis.JuiceboxGetAuthToken = async function(realmId) { return callback(realmId); }")

private fun createUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun setUint8(
    array: JsAny,
    index: Int,
    value: Int,
): Unit = js("array[index] = value")

private fun getUint8(
    array: JsAny,
    index: Int,
): Int = js("array[index]")

private fun uint8ArrayLength(array: JsAny): Int = js("array.length")

private fun createJsArray(): JsAny = js("[]")

private fun pushJsArray(
    array: JsAny,
    value: JsAny,
): Unit = js("array.push(value)")

private fun jsErrorCode(error: JsAny?): Int =
    js(
        """
        error == null
            ? -1
            : typeof error === 'number'
                ? error
                : typeof error.reason === 'number'
                    ? error.reason
                    : typeof error.code === 'number'
                        ? error.code
                        : -1
        """,
    )

private fun jsRecoverGuessesRemaining(error: JsAny?): Int =
    js(
        """
        error != null && typeof error.guesses_remaining === 'number'
            ? error.guesses_remaining
            : -1
        """,
    )

private fun jsErrorMessage(error: JsAny?): String =
    js(
        """
        error == null
            ? 'Juicebox wasm operation failed'
            : String(error.message || error.reason || error)
        """,
    )
