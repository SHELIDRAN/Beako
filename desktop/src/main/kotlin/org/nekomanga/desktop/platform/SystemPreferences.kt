package org.nekomanga.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/** What Windows asks apps to do about motion and transparency. */
data class SystemComfort(val reduceMotion: Boolean = false, val reduceTransparency: Boolean = false)

/**
 * Reads Windows' own comfort settings: Settings → Accessibility → Visual effects → "Animation
 * effects" (SPI_GETCLIENTAREAANIMATION) and "Transparency effects" (the EnableTransparency value).
 * Beako's own switches in More add to these; they never turn the system's choice off. Off Windows,
 * or when a value can't be read, nothing is asked.
 */
object SystemPreferences {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun read(): SystemComfort {
        if (!isWindows) return SystemComfort()
        return SystemComfort(
            reduceMotion = runCatching { !animationsOn() }.getOrDefault(false),
            reduceTransparency = runCatching { !transparencyOn() }.getOrDefault(false),
        )
    }

    private fun animationsOn(): Boolean {
        val on = IntByReference(1)
        val ok = User32.INSTANCE.SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION, 0, on.pointer, 0)
        return !ok || on.value != 0
    }

    private fun transparencyOn(): Boolean {
        val value = IntByReference(1)
        val size = IntByReference(4)
        val status =
            Advapi32.INSTANCE.RegGetValueW(
                HKEY_CURRENT_USER,
                WString(PERSONALIZE),
                WString("EnableTransparency"),
                RRF_RT_REG_DWORD,
                null,
                value.pointer,
                size,
            )
        // Missing value: Windows' default, which is on.
        return status != 0 || value.value != 0
    }

    private const val SPI_GETCLIENTAREAANIMATION = 0x1042
    private const val RRF_RT_REG_DWORD = 0x00000010
    private const val PERSONALIZE =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    /** (HKEY)(LONG)0x80000001, sign-extended as Windows does. */
    private val HKEY_CURRENT_USER = Pointer(0x80000001L.toInt().toLong())

    @Suppress("FunctionName")
    private interface User32 : StdCallLibrary {
        fun SystemParametersInfoW(action: Int, param: Int, value: Pointer?, winIni: Int): Boolean

        companion object {
            val INSTANCE: User32 by lazy { Native.load("user32", User32::class.java) }
        }
    }

    @Suppress("FunctionName")
    private interface Advapi32 : StdCallLibrary {
        fun RegGetValueW(
            key: Pointer?,
            subKey: WString,
            value: WString,
            flags: Int,
            type: IntByReference?,
            data: Pointer?,
            size: IntByReference,
        ): Int

        companion object {
            val INSTANCE: Advapi32 by lazy { Native.load("advapi32", Advapi32::class.java) }
        }
    }
}
