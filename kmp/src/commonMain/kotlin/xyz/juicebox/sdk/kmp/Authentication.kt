package xyz.juicebox.sdk.kmp

/**
 * A token used to authenticate with a realm.
 */
public class AuthToken(
    public val jwt: String,
) {
    override fun toString(): String = jwt
}

/**
 * Called when a client needs an auth token for a realm.
 */
public fun interface AuthTokenProvider {
    public fun getAuthToken(realmId: RealmId): AuthToken?
}
