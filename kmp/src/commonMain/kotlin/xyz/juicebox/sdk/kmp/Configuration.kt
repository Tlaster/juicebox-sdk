package xyz.juicebox.sdk.kmp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The parameters used to configure a [Client].
 */
public class Configuration(
    public val json: String,
) {
    init {
        require(json.isNotBlank()) { "configuration json must not be blank" }
    }

    public constructor(
        realms: List<Realm>,
        registerThreshold: Int,
        recoverThreshold: Int,
        pinHashingMode: PinHashingMode,
    ) : this(
        buildJsonObject {
            put(
                "realms",
                buildJsonArray {
                    realms.forEach { realm -> add(realm.toJson()) }
                },
            )
            put("register_threshold", registerThreshold)
            put("recover_threshold", recoverThreshold)
            put("pin_hashing_mode", pinHashingMode.jsonName)
        }.toString(),
    )
}

private fun Realm.toJson(): JsonObject =
    buildJsonObject {
        put("id", id.toString())
        put("address", address)
        publicKey?.let { put("public_key", it.encodeHex()) }
    }
