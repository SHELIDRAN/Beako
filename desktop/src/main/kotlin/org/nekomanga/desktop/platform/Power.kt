package org.nekomanga.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary

/**
 * Whether the PC is on mains power, for "update only while plugged in". Desktops without a battery
 * count as plugged in, and so does anything that can't be read (or off Windows).
 */
object Power {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun pluggedIn(): Boolean {
        if (!isWindows) return true
        return runCatching {
                val status = SystemPowerStatus()
                if (!Kernel32.INSTANCE.GetSystemPowerStatus(status)) return@runCatching true
                // ACLineStatus: 0 offline (battery), 1 online, 255 unknown.
                status.ACLineStatus.toInt() and 0xFF != 0
            }
            .getOrDefault(true)
    }

    @Suppress("PropertyName")
    @Structure.FieldOrder(
        "ACLineStatus",
        "BatteryFlag",
        "BatteryLifePercent",
        "SystemStatusFlag",
        "BatteryLifeTime",
        "BatteryFullLifeTime",
    )
    class SystemPowerStatus : Structure() {
        @JvmField var ACLineStatus: Byte = 0
        @JvmField var BatteryFlag: Byte = 0
        @JvmField var BatteryLifePercent: Byte = 0
        @JvmField var SystemStatusFlag: Byte = 0
        @JvmField var BatteryLifeTime: Int = 0
        @JvmField var BatteryFullLifeTime: Int = 0
    }

    @Suppress("FunctionName")
    private interface Kernel32 : StdCallLibrary {
        fun GetSystemPowerStatus(status: SystemPowerStatus): Boolean

        companion object {
            val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
        }
    }
}

/**
 * Keeps the display awake while reading (Windows' SetThreadExecutionState). Call [hold] and
 * [release] from the same (UI) thread.
 */
object KeepAwake {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun hold() = set(ES_CONTINUOUS or ES_DISPLAY_REQUIRED or ES_SYSTEM_REQUIRED)

    fun release() = set(ES_CONTINUOUS)

    private fun set(flags: Int) {
        if (!isWindows) return
        runCatching { Kernel32.INSTANCE.SetThreadExecutionState(flags) }
    }

    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_DISPLAY_REQUIRED = 0x00000002
    private const val ES_SYSTEM_REQUIRED = 0x00000001

    @Suppress("FunctionName")
    private interface Kernel32 : StdCallLibrary {
        fun SetThreadExecutionState(flags: Int): Int

        companion object {
            val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
        }
    }
}
