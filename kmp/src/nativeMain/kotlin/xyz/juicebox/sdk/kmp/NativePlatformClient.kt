@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package xyz.juicebox.sdk.kmp

import cnames.structs.JuiceboxAuthToken
import cnames.structs.JuiceboxAuthTokenManager
import cnames.structs.JuiceboxClient
import cnames.structs.JuiceboxConfiguration
import cnames.structs.JuiceboxHttpClientState
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVarOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import xyz.juicebox.sdk.ffi.JuiceboxDeleteErrorAssertion
import xyz.juicebox.sdk.ffi.JuiceboxDeleteErrorInvalidAuth
import xyz.juicebox.sdk.ffi.JuiceboxDeleteErrorRateLimitExceeded
import xyz.juicebox.sdk.ffi.JuiceboxDeleteErrorTransient
import xyz.juicebox.sdk.ffi.JuiceboxDeleteErrorUpgradeRequired
import xyz.juicebox.sdk.ffi.JuiceboxHttpHeader
import xyz.juicebox.sdk.ffi.JuiceboxHttpRequest
import xyz.juicebox.sdk.ffi.JuiceboxHttpRequestMethodDelete
import xyz.juicebox.sdk.ffi.JuiceboxHttpRequestMethodGet
import xyz.juicebox.sdk.ffi.JuiceboxHttpRequestMethodPost
import xyz.juicebox.sdk.ffi.JuiceboxHttpRequestMethodPut
import xyz.juicebox.sdk.ffi.JuiceboxHttpResponse
import xyz.juicebox.sdk.ffi.JuiceboxRecoverError
import xyz.juicebox.sdk.ffi.JuiceboxRecoverErrorReasonAssertion
import xyz.juicebox.sdk.ffi.JuiceboxRecoverErrorReasonInvalidAuth
import xyz.juicebox.sdk.ffi.JuiceboxRecoverErrorReasonInvalidPin
import xyz.juicebox.sdk.ffi.JuiceboxRecoverErrorReasonNotRegistered
import xyz.juicebox.sdk.ffi.JuiceboxRecoverErrorReasonRateLimitExceeded
import xyz.juicebox.sdk.ffi.JuiceboxRecoverErrorReasonTransient
import xyz.juicebox.sdk.ffi.JuiceboxRecoverErrorReasonUpgradeRequired
import xyz.juicebox.sdk.ffi.JuiceboxRegisterErrorAssertion
import xyz.juicebox.sdk.ffi.JuiceboxRegisterErrorInvalidAuth
import xyz.juicebox.sdk.ffi.JuiceboxRegisterErrorRateLimitExceeded
import xyz.juicebox.sdk.ffi.JuiceboxRegisterErrorTransient
import xyz.juicebox.sdk.ffi.JuiceboxRegisterErrorUpgradeRequired
import xyz.juicebox.sdk.ffi.JuiceboxUnmanagedConfigurationArray
import xyz.juicebox.sdk.ffi.JuiceboxUnmanagedDataArray
import xyz.juicebox.sdk.ffi.JuiceboxUnmanagedHttpHeaderArray
import xyz.juicebox.sdk.ffi.juicebox_auth_token_create
import xyz.juicebox.sdk.ffi.juicebox_auth_token_destroy
import xyz.juicebox.sdk.ffi.juicebox_client_create
import xyz.juicebox.sdk.ffi.juicebox_client_delete
import xyz.juicebox.sdk.ffi.juicebox_client_destroy
import xyz.juicebox.sdk.ffi.juicebox_client_recover
import xyz.juicebox.sdk.ffi.juicebox_client_register
import xyz.juicebox.sdk.ffi.juicebox_configuration_create_from_json
import xyz.juicebox.sdk.ffi.juicebox_configuration_destroy
import xyz.juicebox.sdk.ffi.juicebox_sdk_version

internal actual fun createPlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
): PlatformClient =
    NativePlatformClient(
        configuration = configuration,
        previousConfigurations = previousConfigurations,
        authTokens = authTokens,
        authTokenProvider = authTokenProvider,
    )

private class NativePlatformClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
    authTokens: Map<RealmId, AuthToken>?,
    authTokenProvider: AuthTokenProvider?,
) : PlatformClient {
    private val native: CPointer<JuiceboxClient> =
        createNativeClient(configuration, previousConfigurations)

    @Suppress("unused")
    private val cleaner = createCleaner(native) { client ->
        juicebox_client_destroy(client)
    }

    init {
        NativeAuthTokens.source =
            TokenSource(
                tokens = authTokens.orEmpty(),
                provider = authTokenProvider,
            )
    }

    override suspend fun register(
        pin: ByteArray,
        secret: ByteArray,
        info: ByteArray,
        numGuesses: Int,
    ) {
        require(numGuesses in 0..UShort.MAX_VALUE.toInt()) {
            "numGuesses must be between 0 and ${UShort.MAX_VALUE}"
        }

        suspendCancellableCoroutine<Unit> { continuation: CancellableContinuation<Unit> ->
            val operation = StableRef.create(OperationContinuation(continuation))
            pin.withUnmanagedDataArray { pinArray ->
                secret.withUnmanagedDataArray { secretArray ->
                    info.withUnmanagedDataArray { infoArray ->
                        juicebox_client_register(
                            client = native,
                            context = operation.asCPointer(),
                            pin = pinArray,
                            secret = secretArray,
                            info = infoArray,
                            num_guesses = numGuesses.toUShort(),
                            response = registerResponseCallback,
                        )
                    }
                }
            }
        }
    }

    override suspend fun recover(
        pin: ByteArray,
        info: ByteArray,
    ): ByteArray =
        suspendCancellableCoroutine<ByteArray> { continuation: CancellableContinuation<ByteArray> ->
            val operation = StableRef.create(OperationContinuation(continuation))
            pin.withUnmanagedDataArray { pinArray ->
                info.withUnmanagedDataArray { infoArray ->
                    juicebox_client_recover(
                        client = native,
                        context = operation.asCPointer(),
                        pin = pinArray,
                        info = infoArray,
                        response = recoverResponseCallback,
                    )
                }
            }
        }

    override suspend fun delete() {
        suspendCancellableCoroutine<Unit> { continuation: CancellableContinuation<Unit> ->
            val operation = StableRef.create(OperationContinuation(continuation))
            juicebox_client_delete(
                client = native,
                context = operation.asCPointer(),
                response = deleteResponseCallback,
            )
        }
    }
}

private fun createNativeClient(
    configuration: Configuration,
    previousConfigurations: List<Configuration>,
): CPointer<JuiceboxClient> =
    memScoped {
        val configurationPtr = configuration.toFfiConfiguration()
        val previousConfigurationPtrs =
            previousConfigurations.map { it.toFfiConfiguration() }

        val previousConfigurationArray =
            previousConfigurationPtrs.withConfigurationPointerArray(this)

        val client =
            juicebox_client_create(
                configuration = configurationPtr,
                previous_configurations = previousConfigurationArray,
                auth_token_get = authTokenGetCallback,
                http_send = httpSendCallback,
            )

        previousConfigurationPtrs.forEach { juicebox_configuration_destroy(it) }
        juicebox_configuration_destroy(configurationPtr)

        checkNotNull(client) { "juicebox_client_create returned null" }
    }

private fun Configuration.toFfiConfiguration(): CPointer<JuiceboxConfiguration> =
    checkNotNull(juicebox_configuration_create_from_json(json)) {
        "juicebox_configuration_create_from_json returned null"
    }

private fun List<CPointer<JuiceboxConfiguration>>.withConfigurationPointerArray(
    scope: MemScope,
): CValue<JuiceboxUnmanagedConfigurationArray> {
    val data =
        if (isEmpty()) {
            null
        } else {
            scope.allocArray<CPointerVar<JuiceboxConfiguration>>(size).also { array ->
                forEachIndexed { index, configuration ->
                    array[index] = configuration
                }
            }
        }

    return cValue {
        this.data = data
        length = size.convert()
    }
}

private object NativeAuthTokens {
    var source: TokenSource? = null
}

private class TokenSource(
    private val tokens: Map<RealmId, AuthToken>,
    private val provider: AuthTokenProvider?,
) {
    fun get(realmId: RealmId): AuthToken? =
        tokens[realmId] ?: provider?.getAuthToken(realmId)
}

private val authTokenGetCallback =
    staticCFunction(::getAuthToken)

private fun getAuthToken(
    context: CPointer<JuiceboxAuthTokenManager>?,
    contextId: ULong,
    realmId: CArrayPointer<UByteVar>?,
    callback: CPointer<CFunction<(CPointer<JuiceboxAuthTokenManager>?, ULong, CPointer<JuiceboxAuthToken>?) -> Unit>>?,
) {
    if (callback == null || realmId == null) return

    val token =
        NativeAuthTokens.source
            ?.get(RealmId(realmId.toByteArray(16)))

    val ffiToken = token?.let { juicebox_auth_token_create(it.jwt) }
    callback(context, contextId, ffiToken)
    ffiToken?.let { juicebox_auth_token_destroy(it) }
}

private val nativeHttpClient = HttpClient()
private val nativeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private val httpSendCallback =
    staticCFunction(::sendHttp)

private fun sendHttp(
    context: CPointer<JuiceboxHttpClientState>?,
    request: CPointer<JuiceboxHttpRequest>?,
    callback: CPointer<CFunction<(CPointer<JuiceboxHttpClientState>?, CPointer<JuiceboxHttpResponse>?) -> Unit>>?,
) {
    if (context == null || request == null || callback == null) return

    val requestData = request.pointed.toNativeRequest()
    nativeScope.launch {
        val response =
            runCatching { requestData.execute() }
                .getOrElse { NativeHttpResponse(requestData.id, 0, emptyList(), ByteArray(0)) }
        callback.withResponse(context, response)
    }
}

private data class NativeHttpRequest(
    val id: ByteArray,
    val method: UInt,
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

private data class NativeHttpResponse(
    val id: ByteArray,
    val statusCode: Int,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

private fun JuiceboxHttpRequest.toNativeRequest(): NativeHttpRequest =
    NativeHttpRequest(
        id = id.toByteArray(16),
        method = method,
        url = url?.toKString().orEmpty(),
        headers = headers.toPairs(),
        body = body.toByteArray(),
    )

private suspend fun NativeHttpRequest.execute(): NativeHttpResponse {
    val requestMethod = method.toKtorMethod()
    val requestHeaders = headers
    val requestBody = body
    val sdkVersion = juicebox_sdk_version()?.toKString().orEmpty()

    val response =
        nativeHttpClient.request(url) {
            method = requestMethod
            headers {
                append("User-Agent", "JuiceboxSdk-KMP-Native/$sdkVersion")
                append("X-Juicebox-Version", sdkVersion)
                requestHeaders.forEach { (name, value) -> append(name, value) }
            }
            if (requestBody.isNotEmpty()) {
                setBody(ByteArrayContent(requestBody))
            }
        }

    return NativeHttpResponse(
        id = id,
        statusCode = response.status.value,
        headers =
            response.headers
                .entries()
                .map { (name, values) -> name to values.joinToString(",") },
        body = response.bodyAsBytes(),
    )
}

private fun UInt.toKtorMethod(): HttpMethod =
    when (this) {
        JuiceboxHttpRequestMethodGet -> HttpMethod.Get
        JuiceboxHttpRequestMethodPut -> HttpMethod.Put
        JuiceboxHttpRequestMethodPost -> HttpMethod.Post
        JuiceboxHttpRequestMethodDelete -> HttpMethod.Delete
        else -> HttpMethod.Get
    }

private fun CPointer<CFunction<(CPointer<JuiceboxHttpClientState>?, CPointer<JuiceboxHttpResponse>?) -> Unit>>.withResponse(
    context: CPointer<JuiceboxHttpClientState>,
    response: NativeHttpResponse,
) {
    memScoped {
        val responseVar = alloc<JuiceboxHttpResponse>()
        response.id.copyInto(responseVar.id, 16)
        responseVar.status_code = response.statusCode.coerceIn(0, UShort.MAX_VALUE.toInt()).toUShort()
        responseVar.headers.set(response.headers.toUnmanagedHeaders(this))
        responseVar.body.set(response.body.toUnmanagedDataArray(this))
        this@withResponse(context, responseVar.ptr)
    }
}

private fun List<Pair<String, String>>.toUnmanagedHeaders(
    scope: MemScope,
): CValue<JuiceboxUnmanagedHttpHeaderArray> {
    val data =
        if (isEmpty()) {
            null
        } else {
            scope.allocArray<JuiceboxHttpHeader>(size).also { array ->
                forEachIndexed { index, (name, value) ->
                    array[index].name = name.cstr.getPointer(scope)
                    array[index].value = value.cstr.getPointer(scope)
                }
            }
        }

    return cValue {
        this.data = data
        length = size.convert()
    }
}

private fun ByteArray.toUnmanagedDataArray(
    scope: MemScope,
): CValue<JuiceboxUnmanagedDataArray> {
    val data =
        if (isEmpty()) {
            null
        } else {
            scope.allocArray<UByteVar>(size).also { array ->
                forEachIndexed { index, byte ->
                    array[index] = byte.toUByte()
                }
            }
        }

    return cValue {
        this.data = data
        length = size.convert()
    }
}

private fun JuiceboxUnmanagedHttpHeaderArray.toPairs(): List<Pair<String, String>> {
    val pointer = data ?: return emptyList()
    return List(length.toInt()) { index ->
        val header = pointer[index]
        header.name?.toKString().orEmpty() to header.value?.toKString().orEmpty()
    }
}

private fun JuiceboxUnmanagedDataArray.toByteArray(): ByteArray =
    data?.toByteArray(length.toInt()) ?: ByteArray(0)

private fun CArrayPointer<UByteVar>.toByteArray(length: Int): ByteArray =
    ByteArray(length) { index -> this[index].toByte() }

private fun ByteArray.copyInto(
    destination: CArrayPointer<UByteVar>,
    length: Int,
) {
    val count = minOf(size, length)
    repeat(count) { index ->
        destination[index] = this[index].toUByte()
    }
}

private inline fun <R> ByteArray.withUnmanagedDataArray(
    block: (CValue<JuiceboxUnmanagedDataArray>) -> R,
): R =
    usePinned { pinned ->
        val data =
            if (isEmpty()) {
                null
            } else {
                pinned.addressOf(0).reinterpret<UByteVar>()
            }
        block(
            cValue {
                this.data = data
                length = size.convert()
            },
        )
    }

private fun JuiceboxUnmanagedDataArray.set(value: CValue<JuiceboxUnmanagedDataArray>) {
    value.useContents {
        this@set.data = data
        this@set.length = length
    }
}

private fun JuiceboxUnmanagedHttpHeaderArray.set(value: CValue<JuiceboxUnmanagedHttpHeaderArray>) {
    value.useContents {
        this@set.data = data
        this@set.length = length
    }
}

private class OperationContinuation<T>(
    val continuation: CancellableContinuation<T>,
)

private val registerResponseCallback =
    staticCFunction(::onRegisterResponse)

private fun onRegisterResponse(
    context: COpaquePointer?,
    error: CPointer<UIntVarOf<UInt>>?,
) {
    val operation = context.takeOperation<Unit>() ?: return
    val registerError = error?.get(0)?.toRegisterError()
    if (registerError == null) {
        operation.continuation.resume(Unit)
    } else {
        operation.continuation.resumeWithException(RegisterException(registerError))
    }
}

private val recoverResponseCallback =
    staticCFunction(::onRecoverResponse)

private fun onRecoverResponse(
    context: COpaquePointer?,
    secret: CValue<JuiceboxUnmanagedDataArray>,
    error: CPointer<JuiceboxRecoverError>?,
) {
    val operation = context.takeOperation<ByteArray>() ?: return
    if (error == null) {
        operation.continuation.resume(secret.useContents { toByteArray() })
    } else {
        val recoverError = error.pointed.reason.toRecoverError()
        val guessesRemaining = error.pointed.guesses_remaining?.get(0)?.toShort()
        operation.continuation.resumeWithException(
            RecoverException(recoverError, guessesRemaining),
        )
    }
}

private val deleteResponseCallback =
    staticCFunction(::onDeleteResponse)

private fun onDeleteResponse(
    context: COpaquePointer?,
    error: CPointer<UIntVarOf<UInt>>?,
) {
    val operation = context.takeOperation<Unit>() ?: return
    val deleteError = error?.get(0)?.toDeleteError()
    if (deleteError == null) {
        operation.continuation.resume(Unit)
    } else {
        operation.continuation.resumeWithException(DeleteException(deleteError))
    }
}

private fun <T> COpaquePointer?.takeOperation(): OperationContinuation<T>? {
    val ref = this?.asStableRef<OperationContinuation<T>>() ?: return null
    val operation = ref.get()
    ref.dispose()
    return operation
}

private fun UInt.toRegisterError(): RegisterError =
    when (this) {
        JuiceboxRegisterErrorInvalidAuth -> RegisterError.INVALID_AUTH
        JuiceboxRegisterErrorUpgradeRequired -> RegisterError.UPGRADE_REQUIRED
        JuiceboxRegisterErrorRateLimitExceeded -> RegisterError.RATE_LIMIT_EXCEEDED
        JuiceboxRegisterErrorTransient -> RegisterError.TRANSIENT
        JuiceboxRegisterErrorAssertion -> RegisterError.ASSERTION
        else -> RegisterError.ASSERTION
    }

private fun UInt.toRecoverError(): RecoverError =
    when (this) {
        JuiceboxRecoverErrorReasonInvalidPin -> RecoverError.INVALID_PIN
        JuiceboxRecoverErrorReasonNotRegistered -> RecoverError.NOT_REGISTERED
        JuiceboxRecoverErrorReasonInvalidAuth -> RecoverError.INVALID_AUTH
        JuiceboxRecoverErrorReasonUpgradeRequired -> RecoverError.UPGRADE_REQUIRED
        JuiceboxRecoverErrorReasonRateLimitExceeded -> RecoverError.RATE_LIMIT_EXCEEDED
        JuiceboxRecoverErrorReasonTransient -> RecoverError.TRANSIENT
        JuiceboxRecoverErrorReasonAssertion -> RecoverError.ASSERTION
        else -> RecoverError.ASSERTION
    }

private fun UInt.toDeleteError(): DeleteError =
    when (this) {
        JuiceboxDeleteErrorInvalidAuth -> DeleteError.INVALID_AUTH
        JuiceboxDeleteErrorUpgradeRequired -> DeleteError.UPGRADE_REQUIRED
        JuiceboxDeleteErrorRateLimitExceeded -> DeleteError.RATE_LIMIT_EXCEEDED
        JuiceboxDeleteErrorTransient -> DeleteError.TRANSIENT
        JuiceboxDeleteErrorAssertion -> DeleteError.ASSERTION
        else -> DeleteError.ASSERTION
    }
