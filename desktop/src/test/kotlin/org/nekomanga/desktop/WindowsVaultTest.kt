package org.nekomanga.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.nekomanga.desktop.platform.WindowsVault

/** Runs only on Windows (CI): a real round trip through Credential Manager. */
class WindowsVaultTest {

    @Test
    fun secretsRoundTripThroughCredentialManager() {
        assumeTrue(WindowsVault.isWindows)
        val vault = WindowsVault()
        val key = "test.${System.nanoTime()}"
        try {
            assertNull(vault.get(key))
            vault.put(key, "token-ünïcode-✓")
            assertEquals("token-ünïcode-✓", vault.get(key))
            vault.put(key, "rotated")
            assertEquals("rotated", vault.get(key))
        } finally {
            vault.delete(key)
        }
        assertNull(vault.get(key))
    }
}
