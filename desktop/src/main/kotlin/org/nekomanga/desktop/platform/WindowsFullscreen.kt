package org.nekomanga.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.awt.Frame
import java.awt.Rectangle

/**
 * Borderless full screen on Windows: removes the window's caption and resize frame and sizes it to
 * the monitor, which Windows treats as full screen (the taskbar hides). Compose's
 * WindowPlacement.Fullscreen only changed its own state on a decorated window, so the title bar
 * stayed (0.1.1 QA).
 */
internal object WindowsFullscreen {

    /** What [enter] changed, for [exit] to put back. */
    class Saved(val style: Long, val bounds: Rectangle, val extendedState: Int)

    fun enter(frame: Frame): Saved {
        val saved = Saved(0, frame.bounds, frame.extendedState)
        // A maximised window keeps its maximised placement through SetWindowPos; restore it first.
        if (frame.extendedState and Frame.MAXIMIZED_BOTH != 0) frame.extendedState = Frame.NORMAL
        val hwnd = Native.getComponentPointer(frame)
        val style = User32.INSTANCE.GetWindowLongPtrW(hwnd, GWL_STYLE)
        User32.INSTANCE.SetWindowLongPtrW(
            hwnd,
            GWL_STYLE,
            style and (WS_CAPTION or WS_THICKFRAME).inv(),
        )
        val monitor = User32.INSTANCE.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)
        val info = MonitorInfo()
        User32.INSTANCE.GetMonitorInfoW(monitor, info)
        val r = info.rcMonitor
        User32.INSTANCE.SetWindowPos(
            hwnd,
            null,
            r.left,
            r.top,
            r.right - r.left,
            r.bottom - r.top,
            SWP_FRAMECHANGED or SWP_NOOWNERZORDER or SWP_SHOWWINDOW,
        )
        return Saved(style, saved.bounds, saved.extendedState)
    }

    fun exit(frame: Frame, saved: Saved) {
        val hwnd = Native.getComponentPointer(frame)
        User32.INSTANCE.SetWindowLongPtrW(hwnd, GWL_STYLE, saved.style)
        User32.INSTANCE.SetWindowPos(
            hwnd,
            null,
            0,
            0,
            0,
            0,
            SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOOWNERZORDER,
        )
        frame.bounds = saved.bounds
        frame.extendedState = saved.extendedState
    }

    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000L
    private const val WS_THICKFRAME = 0x00040000L
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_SHOWWINDOW = 0x0040
    private const val SWP_NOOWNERZORDER = 0x0200

    @Suppress("FunctionName")
    private interface User32 : StdCallLibrary {
        fun GetWindowLongPtrW(hwnd: Pointer?, index: Int): Long

        fun SetWindowLongPtrW(hwnd: Pointer?, index: Int, value: Long): Long

        fun MonitorFromWindow(hwnd: Pointer?, flags: Int): Pointer?

        fun GetMonitorInfoW(monitor: Pointer?, info: MonitorInfo): Boolean

        fun SetWindowPos(
            hwnd: Pointer?,
            insertAfter: Pointer?,
            x: Int,
            y: Int,
            cx: Int,
            cy: Int,
            flags: Int,
        ): Boolean

        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }
    }

    @Structure.FieldOrder("left", "top", "right", "bottom")
    class Rect : Structure() {
        @JvmField var left = 0
        @JvmField var top = 0
        @JvmField var right = 0
        @JvmField var bottom = 0
    }

    @Structure.FieldOrder("cbSize", "rcMonitor", "rcWork", "dwFlags")
    class MonitorInfo : Structure() {
        @JvmField var cbSize = 40
        @JvmField var rcMonitor = Rect()
        @JvmField var rcWork = Rect()
        @JvmField var dwFlags = 0
    }
}
