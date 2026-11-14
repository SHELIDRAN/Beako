package org.nekomanga.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nekomanga.desktop.components.GlassButton
import org.nekomanga.desktop.components.GlassTextField
import org.nekomanga.desktop.state.AccountsModel
import org.nekomanga.desktop.state.Service
import org.nekomanga.desktop.state.ServiceStatus
import org.nekomanga.desktop.theme.BeakoColors
import org.nekomanga.shared.library.SettingsStore
import org.nekomanga.shared.mangadex.MangaDexCredentials
import org.nekomanga.shared.tracking.anilist.AniListTracker

/** More → Accounts and tracking: sign-in for Comick, AniList and MangaDex, imports and sync. */
@Composable
fun AccountsSection(
    accounts: AccountsModel,
    settingsStore: SettingsStore,
    secretsPersist: Boolean,
) {
    val settings by settingsStore.settings.collectAsState()
    val comick by accounts.comick.account.collectAsState()
    val comickConnected by accounts.comickAccount.connected.collectAsState()
    val aniList by accounts.aniList.account.collectAsState()
    val mangaDexUser by accounts.mangaDex.username.collectAsState()

    Section("Accounts and tracking") {
        Text(
            if (secretsPersist) {
                "Sign-ins are kept in Windows Credential Manager. Beako never stores your passwords."
            } else {
                "On this system sign-ins last until Beako closes. Beako never stores your passwords."
            },
            style = MaterialTheme.typography.bodySmall,
            color = BeakoColors.TextMuted,
        )
        ToggleRow(
            "Update trackers as you read",
            "When you finish a chapter, send your progress to linked trackers.",
            settings.autoTrack,
        ) {
            settingsStore.update { s -> s.copy(autoTrack = it) }
        }

        HorizontalDivider(color = BeakoColors.Rim)
        ServiceRow(
            name = "Comick",
            detail =
                when {
                    comick != null -> "Signed in as ${comick?.name}"
                    comickConnected -> "Connected"
                    else -> "Track your reading in your Comick library."
                },
            status = accounts.status[Service.COMICK],
            onCancel = { accounts.cancel(Service.COMICK) },
        ) {
            if (comickConnected) {
                GlassButton("Import library", { accounts.import(Service.COMICK) })
                GlassButton("Disconnect", { accounts.disconnect(Service.COMICK) })
            } else {
                GlassButton("Connect", accounts::connectComick, primary = true)
            }
        }

        HorizontalDivider(color = BeakoColors.Rim)
        var clientId by remember { mutableStateOf(settings.aniListClientId) }
        ServiceRow(
            name = "AniList",
            detail = aniList?.let { "Signed in as ${it.name}" } ?: "Track your reading on AniList.",
            status = accounts.status[Service.ANILIST],
            onCancel = { accounts.cancel(Service.ANILIST) },
            form =
                if (aniList != null) null
                else {
                    {
                        Text(
                            "AniList needs your own API client. On anilist.co go to Settings → Developer → " +
                                "Create new client, and set its redirect URL to:",
                            style = MaterialTheme.typography.bodySmall,
                            color = BeakoColors.TextMuted,
                        )
                        SelectionContainer {
                            Text(
                                AniListTracker.REDIRECT_URI,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BeakoColors.Text,
                            )
                        }
                        GlassTextField(
                            "Client ID",
                            clientId,
                            { clientId = it.filter(Char::isDigit) },
                            Modifier.widthIn(max = 280.dp),
                        )
                    }
                },
        ) {
            if (aniList != null) {
                GlassButton("Import list", { accounts.import(Service.ANILIST) })
                GlassButton("Disconnect", { accounts.disconnect(Service.ANILIST) })
            } else {
                GlassButton("Connect", { accounts.connectAniList(clientId) }, primary = true)
            }
        }

        HorizontalDivider(color = BeakoColors.Rim)
        var username by remember { mutableStateOf(settings.mangaDexUsername) }
        var password by remember { mutableStateOf("") }
        var mdClientId by remember { mutableStateOf(settings.mangaDexClientId) }
        var mdSecret by remember { mutableStateOf("") }
        ServiceRow(
            name = "MangaDex",
            detail =
                mangaDexUser?.let { "Signed in as $it" }
                    ?: "Import your follows and mark chapters read on MangaDex.",
            status = accounts.status[Service.MANGADEX],
            onCancel = { accounts.cancel(Service.MANGADEX) },
            form =
                if (mangaDexUser != null) null
                else {
                    {
                        Text(
                            "MangaDex signs in third-party apps through a personal API client. Create one " +
                                "on mangadex.org under Settings → API Clients; it works once MangaDex approves it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BeakoColors.TextMuted,
                        )
                        FieldRow {
                            GlassTextField("Username", username, { username = it }, FieldWidth)
                            GlassTextField(
                                "Password",
                                password,
                                { password = it },
                                FieldWidth,
                                secret = true,
                            )
                        }
                        FieldRow {
                            GlassTextField("Client ID", mdClientId, { mdClientId = it }, FieldWidth)
                            GlassTextField(
                                "Client secret",
                                mdSecret,
                                { mdSecret = it },
                                FieldWidth,
                                secret = true,
                            )
                        }
                    }
                },
        ) {
            if (mangaDexUser != null) {
                GlassButton("Import follows", { accounts.import(Service.MANGADEX) })
                GlassButton("Sign out", { accounts.disconnect(Service.MANGADEX) })
            } else {
                GlassButton(
                    "Sign in",
                    {
                        accounts.signInMangaDex(
                            MangaDexCredentials(username, password, mdClientId, mdSecret),
                        )
                        // The password is sent once and never kept, not even in this form.
                        password = ""
                        mdSecret = ""
                    },
                    primary = true,
                )
            }
        }
        if (mangaDexUser != null) {
            ToggleRow(
                "Mark chapters read on MangaDex",
                "Chapters you finish in Beako are marked read on your MangaDex account.",
                settings.syncReadToMangaDex,
            ) {
                settingsStore.update { s -> s.copy(syncReadToMangaDex = it) }
            }
        }
    }
}

private val FieldWidth = Modifier.widthIn(min = 200.dp, max = 280.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceRow(
    name: String,
    detail: String,
    status: ServiceStatus?,
    onCancel: () -> Unit,
    form: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = BeakoColors.Text)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = BeakoColors.TextMuted,
                )
            }
        }
        form?.invoke()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (status?.busy == true) {
                GlassButton("Cancel", onCancel)
            } else {
                actions()
            }
        }
        status?.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.isError) BeakoColors.Danger else BeakoColors.TextMuted,
            )
        }
    }
}
