package xyz.juicebox.sdk.kmp

public enum class RegisterError {
    INVALID_AUTH,
    UPGRADE_REQUIRED,
    RATE_LIMIT_EXCEEDED,
    ASSERTION,
    TRANSIENT,
}

public class RegisterException(
    public val error: RegisterError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

public enum class RecoverError {
    INVALID_PIN,
    NOT_REGISTERED,
    INVALID_AUTH,
    UPGRADE_REQUIRED,
    RATE_LIMIT_EXCEEDED,
    ASSERTION,
    TRANSIENT,
}

public class RecoverException(
    public val error: RecoverError,
    public val guessesRemaining: Short? = null,
    cause: Throwable? = null,
) : Exception(error.name, cause)

public enum class DeleteError {
    INVALID_AUTH,
    UPGRADE_REQUIRED,
    RATE_LIMIT_EXCEEDED,
    ASSERTION,
    TRANSIENT,
}

public class DeleteException(
    public val error: DeleteError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
