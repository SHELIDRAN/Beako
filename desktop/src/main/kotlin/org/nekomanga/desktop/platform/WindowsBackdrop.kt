package org.nekomanga.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Frame

/** What [WindowsBackdrop.apply] managed to set. */
data class BackdropResult(val darkTitleBar: Boolean, val mica: Boolean)

/**
 * The window frame on Windows: a title bar that matches the palette (dark on the dark palettes,
 * light on Porcelain), and on Windows 11 the Mica material behind it, so the desktop's colours show
 * softly through the frame. Beako's own ground stays opaque. Windows 10 gets the matching title bar
 * only; anything unsupported is skipped, never an error.
 */
object WindowsBackdrop {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun apply(frame: Frame, dark: Boolean, mica: Boolean): BackdropResult {
        if (!isWindows || !frame.isDisplayable) return BackdropResult(false, false)
        val hwnd = Native.getComponentPointer(frame) ?: return BackdropResult(false, false)
        val dwm = Dwm.INSTANCE
        val darkOk =
            set(dwm, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0) ||
                // Windows 10 before 20H1 used an earlier number for the same attribute.
                set(dwm, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, if (dark) 1 else 0)
        val micaOk =
            if (mica) {
                // Windows 11 22H2 and later; 21H2 only knows the older, undocumented switch.
                set(dwm, hwnd, DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_MAINWINDOW) ||
                    set(dwm, hwnd, DWMWA_MICA_EFFECT, 1)
            } else {
                set(dwm, hwnd, DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_NONE)
                set(dwm, hwnd, DWMWA_MICA_EFFECT, 0)
                false
            }
        return BackdropResult(darkOk, micaOk)
    }

    private fun set(dwm: Dwm, hwnd: Pointer, attribute: Int, value: Int): Boolean = runCatching {
        dwm.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value).pointer, 4) == 0
    }
        .getOrDefault(false)

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_SYSTEMBACKDROP_TYPE = 38
    private const val DWMWA_MICA_EFFECT = 1029
    private const val DWMSBT_NONE = 1
    private const val DWMSBT_MAINWINDOW = 2

    @Suppress("FunctionName")
    private interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int

        companion object {
            val INSTANCE: Dwm by lazy { Native.load("dwmapi", Dwm::class.java) }
        }
    }
}
