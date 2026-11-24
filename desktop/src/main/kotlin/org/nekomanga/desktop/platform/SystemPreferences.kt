package org.nekomanga.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/** What Windows asks apps to do about motion and transparency. */
data class SystemComfort(
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    /** Windows' app mode: true light, false dark, null unknown (off Windows). */
    val lightMode: Boolean? = null,
)

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
            lightMode =
                runCatching { personalize("AppsUseLightTheme")?.let { it != 0 } }.getOrNull(),
        )
    }

    private fun animationsOn(): Boolean {
        val on = IntByReference(1)
        val ok = User32.INSTANCE.SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION, 0, on.pointer, 0)
        return !ok || on.value != 0
    }

    // Missing value: Windows' default, which is on.
    private fun transparencyOn(): Boolean =
        personalize("EnableTransparency")?.let { it != 0 } ?: true

    /** A DWORD under the Personalize key, or null when it isn't there. */
    private fun personalize(name: String): Int? {
        val value = IntByReference(0)
        val size = IntByReference(4)
        val status =
            Advapi32.INSTANCE.RegGetValueW(
                HKEY_CURRENT_USER,
                WString(PERSONALIZE),
                WString(name),
                RRF_RT_REG_DWORD,
                null,
                value.pointer,
                size,
            )
        return if (status == 0) value.value else null
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
