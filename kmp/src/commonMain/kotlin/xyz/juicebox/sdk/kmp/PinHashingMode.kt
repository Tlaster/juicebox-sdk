package xyz.juicebox.sdk.kmp

/**
 * A strategy for hashing the user-provided PIN.
 */
public enum class PinHashingMode(
    internal val jsonName: String,
) {
    STANDARD_2019("Standard2019"),
    FAST_INSECURE("FastInsecure"),
}
