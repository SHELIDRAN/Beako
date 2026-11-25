package org.nekomanga.desktop.platform

import java.io.File
import java.io.IOException

/**
 * Runs a downloaded Beako installer once this app has closed, then opens Beako again. The `.msi`
 * shares Beako's upgrade code, so it replaces the installed version in place and keeps the library
 * and settings (they live in the user's AppData, not the install folder). Windows asks for
 * permission (UAC) because Beako is installed for all users.
 */
object Installer {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** True when this copy was installed (jpackage launcher), so it can be updated in place. */
    val canInstall: Boolean
        get() = isWindows && launcher() != null

    /**
     * Starts the hand-over script for [msi]. The caller must quit right after, so the installer can
     * replace the app's files.
     */
    fun runAfterExit(msi: File) {
        if (!isWindows) throw IOException("Updates install on Windows only.")
        val app = launcher() ?: throw IOException("Run the installed Beako to update it.")
        val script = File(msi.parentFile, "install-update.ps1")
        // Windows PowerShell reads a script without a byte-order mark in the ANSI code page, which
        // would garble paths with letters outside it (C:\Users\Jörg).
        script.writeBytes(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                script(msi, app, ProcessHandle.current().pid()).toByteArray(Charsets.UTF_8),
        )
        ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-File",
                script.absolutePath,
            )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    /**
     * Waits for Beako (process [pid]) to exit, runs the installer with a progress bar and no
     * questions, then opens [app] again, whether or not the install went ahead (the user can
     * decline Windows' permission prompt). If Beako is still running after five minutes, nothing is
     * installed: replacing files in use would leave a half-updated app.
     */
    internal fun script(msi: File, app: File, pid: Long): String =
        """
        |${'$'}ErrorActionPreference = 'SilentlyContinue'
        |${'$'}deadline = (Get-Date).AddMinutes(5)
        |while (Get-Process -Id $pid) {
        |    if ((Get-Date) -gt ${'$'}deadline) { exit 1 }
        |    Start-Sleep -Milliseconds 500
        |}
        |Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', '"${quoted(msi)}"', '/passive', '/norestart') -Wait
        |Start-Process -FilePath '${quoted(app)}'
        |"""
            .trimMargin()

    /** A path inside a PowerShell single-quoted string. */
    private fun quoted(file: File) = file.absolutePath.replace("'", "''")

    /** Beako.exe, set by the jpackage launcher; null when run from Gradle or an IDE. */
    private fun launcher(): File? =
        System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }
}
