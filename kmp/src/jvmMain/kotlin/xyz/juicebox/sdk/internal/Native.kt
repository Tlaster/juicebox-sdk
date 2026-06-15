package xyz.juicebox.sdk.internal

import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import xyz.juicebox.sdk.DeleteException
import xyz.juicebox.sdk.PinHashingMode
import xyz.juicebox.sdk.Realm
import xyz.juicebox.sdk.RealmId
import xyz.juicebox.sdk.RecoverException
import xyz.juicebox.sdk.RegisterException

public class Native private constructor() {
    public companion object {
        init {
            NativeLibraryLoader.load()
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

private object NativeLibraryLoader {
    private const val libraryName = "juicebox_sdk_jni"
    private const val resourceRoot = "/juicebox/native"

    fun load() {
        val osName = normalizedOsName()
        val archName = normalizedArchName()
        val mappedLibraryName = System.mapLibraryName(libraryName)
        val resourcePath = "$resourceRoot/$osName-$archName/$mappedLibraryName"

        val input = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath)
        if (input == null) {
            loadFromLibraryPath(resourcePath)
            return
        }

        try {
            input.use { stream ->
                val tempDir = Files.createTempDirectory("juicebox-sdk-jni-")
                val tempFile = tempDir.resolve(mappedLibraryName)
                Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING)
                tempDir.toFile().deleteOnExit()
                tempFile.toFile().deleteOnExit()
                System.load(tempFile.toAbsolutePath().toString())
            }
        } catch (cause: IOException) {
            throw UnsatisfiedLinkError(
                "Failed to extract Juicebox JNI library from bundled resource $resourcePath.",
            ).apply { initCause(cause) }
        }
    }

    private fun loadFromLibraryPath(resourcePath: String) {
        try {
            System.loadLibrary(libraryName)
        } catch (cause: UnsatisfiedLinkError) {
            throw UnsatisfiedLinkError(
                "Could not load Juicebox JNI library. Expected bundled resource $resourcePath " +
                    "or native library $libraryName on java.library.path=" +
                    System.getProperty("java.library.path"),
            ).apply { initCause(cause) }
        }
    }

    private fun normalizedOsName(): String =
        when {
            osName().contains("mac") || osName().contains("darwin") -> "macos"
            osName().contains("linux") -> "linux"
            osName().contains("windows") -> "windows"
            else -> sanitize(System.getProperty("os.name"))
        }

    private fun normalizedArchName(): String =
        when (val arch = System.getProperty("os.arch").lowercase(Locale.US)) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            else -> sanitize(arch)
        }

    private fun osName(): String =
        System.getProperty("os.name").lowercase(Locale.US)

    private fun sanitize(value: String): String =
        value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
}
