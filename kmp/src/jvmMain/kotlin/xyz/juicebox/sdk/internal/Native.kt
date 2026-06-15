package xyz.juicebox.sdk.internal

import xyz.juicebox.sdk.DeleteException
import xyz.juicebox.sdk.PinHashingMode
import xyz.juicebox.sdk.Realm
import xyz.juicebox.sdk.RealmId
import xyz.juicebox.sdk.RecoverException
import xyz.juicebox.sdk.RegisterException

public class Native private constructor() {
    public companion object {
        init {
            System.loadLibrary("juicebox_sdk_jni")
        }

        @JvmStatic
        public external fun clientCreate(
            configuration: Long,
            previousConfigurations: LongArray,
            getAuthToken: GetAuthTokenFn,
            httpSend: HttpSendFn,
        ): Long

        @JvmStatic
        public external fun clientDestroy(client: Long)

        @JvmStatic
        public external fun sdkVersion(): String

        @JvmStatic
        public external fun configurationCreate(
            realms: Array<Realm>,
            registerThreshold: Int,
            recoverThreshold: Int,
            pinHashingMode: PinHashingMode,
        ): Long

        @JvmStatic
        public external fun configurationsAreEqual(
            configuration1: Long,
            configuration2: Long,
        ): Boolean

        @JvmStatic
        public external fun configurationCreateFromJson(json: String): Long

        @JvmStatic
        public external fun configurationDestroy(configuration: Long)

        @JvmStatic
        @Throws(RegisterException::class)
        public external fun clientRegister(
            client: Long,
            pin: ByteArray,
            secret: ByteArray,
            info: ByteArray,
            numGuesses: Short,
        )

        @JvmStatic
        @Throws(RecoverException::class)
        public external fun clientRecover(
            client: Long,
            pin: ByteArray,
            info: ByteArray,
        ): ByteArray

        @JvmStatic
        @Throws(DeleteException::class)
        public external fun clientDelete(client: Long)

        @JvmStatic
        public external fun httpClientRequestComplete(
            httpClient: Long,
            response: HttpResponse,
        )

        @JvmStatic
        public external fun authTokenGetComplete(
            context: Long,
            contextId: Long,
            authToken: Long,
        )

        @JvmStatic
        public external fun authTokenGeneratorCreateFromJson(json: String): Long

        @JvmStatic
        public external fun authTokenGeneratorDestroy(generator: Long)

        @JvmStatic
        public external fun authTokenGeneratorVend(
            generator: Long,
            realmId: ByteArray,
            secretId: ByteArray,
        ): Long

        @JvmStatic
        public external fun authTokenCreate(jwt: String): Long

        @JvmStatic
        public external fun authTokenDestroy(authToken: Long)

        @JvmStatic
        public external fun authTokenString(authToken: Long): String
    }

    public class HttpHeader(
        @JvmField public var name: String,
        @JvmField public var value: String,
    )

    public class HttpRequest {
        @JvmField public var id: ByteArray = ByteArray(0)
        @JvmField public var method: String = ""
        @JvmField public var url: String = ""
        @JvmField public var headers: Array<HttpHeader>? = null
        @JvmField public var body: ByteArray? = null
    }

    public class HttpResponse {
        @JvmField public var id: ByteArray = ByteArray(0)
        @JvmField public var statusCode: Short = 0
        @JvmField public var headers: Array<HttpHeader> = emptyArray()
        @JvmField public var body: ByteArray = ByteArray(0)
    }

    public fun interface HttpSendFn {
        public fun send(
            httpClient: Long,
            request: HttpRequest,
        )
    }

    public fun interface GetAuthTokenFn {
        public fun get(
            context: Long,
            contextId: Long,
            realmId: RealmId,
        )
    }
}
