package org.nekomanga.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.nekomanga.desktop.platform.Installer
import org.nekomanga.desktop.screens.summary

class AppUpdateTest {
    @Test
    fun theHandOverWaitsForBeakoInstallsQuietlyAndReopensIt() {
        val script =
            Installer.script(
                File("C:/Users/O'Neil Smith/AppData/Local/Beako/cache/updates/Beako-0.1.9.msi"),
                File("C:/Program Files/Beako/Beako.exe"),
                4242,
            )
        assertTrue("Get-Process -Id 4242" in script && "exit 1" in script)
        assertTrue("/passive" in script && "/norestart" in script)
        // A quote in a path is doubled inside PowerShell's single-quoted strings.
        assertTrue("O''Neil Smith" in script)
        assertTrue(script.trimEnd().endsWith("Beako.exe'"))
        assertTrue(script.indexOf("msiexec") < script.lastIndexOf("Start-Process -FilePath"))
    }

    @Test
    fun laterKeepsTheNoticeQuietForThreeDaysAcrossRestarts() {
        var saved = "" to 0L
        var clock = 1_000L
        fun model() =
            org.nekomanga.desktop.state.AppUpdateModel(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                org.nekomanga.shared.update.AppUpdates(
                    { org.nekomanga.shared.net.HttpResponse(404, null, "") },
                    okhttp3.OkHttpClient(),
                ),
                File("."),
                "0.1.8",
                automatic = { false },
                canInstall = { false },
                install = {},
                quit = {},
                snoozed = { saved },
                snooze = { v, until -> saved = v to until },
                now = { clock },
            )
        val first = model()
        first.dismiss() // nothing offered yet: nothing saved
        assertEquals("" to 0L, saved)
        saved = "0.1.9" to (clock + org.nekomanga.desktop.state.AppUpdateModel.SNOOZE_MILLIS)
        assertEquals("0.1.9", model().dismissedVersion)
        clock += org.nekomanga.desktop.state.AppUpdateModel.SNOOZE_MILLIS + 1
        assertEquals(null, model().dismissedVersion)
    }

    @Test
    fun releaseNotesAreShortenedForTheNotice() {
        val notes =
            "## Beako 0.1.9\n\n**Install:** download…\n\n### New\n- **Updates** from inside the app\n- Faster lists"
        assertEquals("Updates from inside the app\nFaster lists", summary(notes))
    }
}
