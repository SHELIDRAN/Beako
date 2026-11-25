package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nekomanga.shared.diagnostics.DiagnosticLog
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.update.AppRelease
import org.nekomanga.shared.update.AppUpdates

/** Where Beako's own update stands. */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data object UpToDate : AppUpdateState

    data class Available(val release: AppRelease) : AppUpdateState

    data class Downloading(val release: AppRelease, val progress: Float) : AppUpdateState

    /** The installer is starting; Beako is about to close. */
    data class Installing(val release: AppRelease) : AppUpdateState

    data class Failed(val message: String, val release: AppRelease? = null) : AppUpdateState
}

/**
 * Checks GitHub for a newer Beako, shortly after start and then twice a day while the setting is
 * on, and installs it with one click: download, check, close, install, reopen.
 */
class AppUpdateModel(
    private val scope: CoroutineScope,
    private val updates: AppUpdates,
    private val folder: File,
    val currentVersion: String,
    private val automatic: () -> Boolean,
    /** Can this copy install updates itself (an installed Windows build)? */
    val canInstall: () -> Boolean,
    /** Starts the installer after Beako exits; then [quit] closes the app. */
    private val install: (File) -> Unit,
    private val quit: () -> Unit,
    /** The version "Later" was chosen for and until when (epoch ms); saved across restarts. */
    private val snoozed: () -> Pair<String, Long> = { "" to 0L },
    private val snooze: (String, Long) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
) {
    var state by mutableStateOf<AppUpdateState>(AppUpdateState.Idle)
        private set

    /** The notice was closed with "Later" for this version, for [SNOOZE_MILLIS]. */
    var dismissedVersion by
        mutableStateOf<String?>(
            snoozed().let { (version, until) ->
                version.takeIf { it.isNotEmpty() && now() < until }
            },
        )
        private set

    private var job: Job? = null

    /** The release to offer in the app-wide notice, or null. */
    val notice: AppRelease?
        get() =
            when (val s = state) {
                is AppUpdateState.Available -> s.release.takeIf { it.version != dismissedVersion }
                is AppUpdateState.Downloading -> s.release
                is AppUpdateState.Installing -> s.release
                is AppUpdateState.Failed -> s.release?.takeIf { it.version != dismissedVersion }
                else -> null
            }

    /** Checks now and then on a schedule, while automatic checks are on. */
    fun startAutomatic(firstDelayMillis: Long = 15_000, everyMillis: Long = 12 * 60 * 60 * 1000L) {
        scope.launch {
            delay(firstDelayMillis)
            while (true) {
                if (automatic() && !busy) check(quiet = true)
                delay(everyMillis)
            }
        }
    }

    private val busy: Boolean
        get() = state is AppUpdateState.Downloading || state is AppUpdateState.Installing

    /** Asks GitHub for the latest release. [quiet] checks don't show errors (background ones). */
    fun check(quiet: Boolean = false) {
        if (busy || job?.isActive == true) return
        if (!quiet) state = AppUpdateState.Checking
        job = scope.launch {
            state =
                try {
                    val latest = updates.latest()
                    if (latest != null && AppUpdates.isNewer(latest.version, currentVersion))
                        AppUpdateState.Available(latest)
                    else AppUpdateState.UpToDate
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DiagnosticLog.error("Update check failed", e)
                    if (quiet)
                        state.takeIf { it is AppUpdateState.Available } ?: AppUpdateState.Idle
                    else AppUpdateState.Failed(message(e))
                }
        }
    }

    /**
     * "Later": hides the notice for this release for three days, across restarts. More › About
     * still offers it.
     */
    fun dismiss() {
        val version =
            when (val s = state) {
                is AppUpdateState.Available -> s.release.version
                is AppUpdateState.Failed -> s.release?.version
                else -> null
            }
        if (version != null) {
            dismissedVersion = version
            snooze(version, now() + SNOOZE_MILLIS)
        }
        if (state is AppUpdateState.Failed) state = AppUpdateState.Idle
    }

    /** Downloads [release], checks it, then hands over to the installer and closes Beako. */
    fun update(release: AppRelease) {
        if (busy) return
        job?.cancel()
        state = AppUpdateState.Downloading(release, 0f)
        job = scope.launch {
            try {
                val msi =
                    updates.download(release, folder) { p ->
                        // Only redraw on whole-percent steps.
                        val current = state
                        if (
                            current is AppUpdateState.Downloading && (p - current.progress) >= 0.01f
                        )
                            state = current.copy(progress = p)
                    }
                state = AppUpdateState.Installing(release)
                DiagnosticLog.info("Installing Beako ${release.version}")
                install(msi)
                // Give the hand-over script a moment to start before Beako exits.
                delay(400)
                quit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.error("Update to ${release.version} failed", e)
                state = AppUpdateState.Failed(message(e), release)
            }
        }
    }

    private fun message(e: Exception): String =
        when (e) {
            is ApiError.Unreachable -> "Couldn’t reach GitHub. Check your connection and try again."
            is ApiError.RateLimited -> "GitHub is limiting checks right now. Try again later."
            is java.io.IOException -> e.message ?: "The download failed. Try again."
            else -> "Couldn’t check for updates. Try again."
        }

    companion object {
        const val SNOOZE_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
