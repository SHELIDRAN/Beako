package org.nekomanga.desktop.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import org.nekomanga.shared.auth.MemoryVault
import org.nekomanga.shared.auth.SecretVault

/**
 * Secrets in Windows Credential Manager (generic credentials named "Beako/<key>", stored for this
 * Windows user only). Visible and removable in Control Panel → Credential Manager.
 */
class WindowsVault : SecretVault {
    override val persistent = true

    override fun get(key: String): String? {
        val out = PointerByReference()
        if (!Advapi.INSTANCE.CredReadW(WString(target(key)), CRED_TYPE_GENERIC, 0, out)) return null
        return try {
            val credential = Credential(out.value)
            val size = credential.CredentialBlobSize
            if (size == 0 || credential.CredentialBlob == null) null
            else String(credential.CredentialBlob!!.getByteArray(0, size), Charsets.UTF_8)
        } finally {
            Advapi.INSTANCE.CredFree(out.value)
        }
    }

    override fun put(key: String, secret: String) {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BLOB) { "Secret too large for Credential Manager" }
        val blob =
            Memory(bytes.size.toLong().coerceAtLeast(1)).apply { write(0, bytes, 0, bytes.size) }
        val credential =
            Credential().apply {
                Type = CRED_TYPE_GENERIC
                TargetName = WString(target(key))
                UserName = WString("Beako")
                CredentialBlobSize = bytes.size
                CredentialBlob = blob
                Persist = CRED_PERSIST_LOCAL_MACHINE
            }
        try {
            check(Advapi.INSTANCE.CredWriteW(credential, 0)) {
                "Couldn't save to Credential Manager (error ${Native.getLastError()})"
            }
        } finally {
            blob.clear()
        }
    }

    override fun delete(key: String) {
        Advapi.INSTANCE.CredDeleteW(WString(target(key)), CRED_TYPE_GENERIC, 0)
    }

    private fun target(key: String) = "Beako/$key"

    @Suppress("FunctionName")
    private interface Advapi : StdCallLibrary {
        fun CredWriteW(credential: Credential, flags: Int): Boolean

        fun CredReadW(
            target: WString,
            type: Int,
            flags: Int,
            credential: PointerByReference,
        ): Boolean

        fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean

        fun CredFree(buffer: Pointer)

        companion object {
            val INSTANCE: Advapi = Native.load("Advapi32", Advapi::class.java)
        }
    }

    /** The Win32 CREDENTIALW structure. */
    @Structure.FieldOrder(
        "Flags",
        "Type",
        "TargetName",
        "Comment",
        "LastWrittenLow",
        "LastWrittenHigh",
        "CredentialBlobSize",
        "CredentialBlob",
        "Persist",
        "AttributeCount",
        "Attributes",
        "TargetAlias",
        "UserName",
    )
    @Suppress("PropertyName")
    class Credential : Structure {
        @JvmField var Flags = 0
        @JvmField var Type = 0
        @JvmField var TargetName: WString? = null
        @JvmField var Comment: WString? = null
        @JvmField var LastWrittenLow = 0
        @JvmField var LastWrittenHigh = 0
        @JvmField var CredentialBlobSize = 0
        @JvmField var CredentialBlob: Pointer? = null
        @JvmField var Persist = 0
        @JvmField var AttributeCount = 0
        @JvmField var Attributes: Pointer? = null
        @JvmField var TargetAlias: WString? = null
        @JvmField var UserName: WString? = null

        constructor() : super()

        constructor(pointer: Pointer) : super(pointer) {
            read()
        }
    }

    companion object {
        private const val CRED_TYPE_GENERIC = 1
        private const val CRED_PERSIST_LOCAL_MACHINE = 2
        private const val MAX_BLOB = 5 * 512

        val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

        /** Credential Manager on Windows; elsewhere (development) secrets stay in memory only. */
        fun forThisSystem(): SecretVault = if (isWindows) WindowsVault() else MemoryVault()
    }
}
