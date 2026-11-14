package org.nekomanga.shared.auth

/**
 * Where sign-in secrets live. On Windows this is Credential Manager; elsewhere (development) a
 * memory-only vault, so secrets never reach a plain file.
 */
interface SecretVault {
    fun get(key: String): String?

    fun put(key: String, secret: String)

    fun delete(key: String)

    /** True when secrets survive a restart. */
    val persistent: Boolean
}

class MemoryVault : SecretVault {
    private val secrets = mutableMapOf<String, String>()

    @Synchronized override fun get(key: String) = secrets[key]

    @Synchronized
    override fun put(key: String, secret: String) {
        secrets[key] = secret
    }

    @Synchronized
    override fun delete(key: String) {
        secrets.remove(key)
    }

    override val persistent = false
}
