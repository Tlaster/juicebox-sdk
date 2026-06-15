package xyz.juicebox.sdk.kmp

/**
 * A 16-byte unique identifier specified by the realm.
 */
public class RealmId(
    public val bytes: ByteArray,
) {
    public constructor(string: String) : this(string.decodeHex())

    init {
        require(bytes.size == 16) { "realm id must be 16 bytes" }
    }

    override fun toString(): String = bytes.encodeHex()

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is RealmId && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * A remote service that the client interacts with directly.
 */
public class Realm(
    public val id: RealmId,
    public val address: String,
    public val publicKey: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Realm &&
                    id == other.id &&
                    address == other.address &&
                    publicKey.contentEquals(other.publicKey)
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}
