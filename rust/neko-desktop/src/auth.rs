//! Signing in to MangaDex with a personal API client.
//!
//! MangaDex lets each account create a personal API client (Settings → API Clients on
//! mangadex.org). Neko exchanges the client's ID and secret, plus the account's username and
//! password, for tokens using the OAuth password grant. The password is used once and never
//! stored. The client secret and refresh token are kept in Windows Credential Manager; the
//! short-lived access token lives only in memory.

use std::sync::Mutex;
use std::time::{Duration, Instant};

use anyhow::{Context as _, Result, anyhow, bail};
use serde::{Deserialize, Serialize};
use serde_json::Value;

const TOKEN_URL: &str = "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token";
const KEYRING_SERVICE: &str = "Neko Desktop";
const KEYRING_USER: &str = "mangadex-personal-client";

/// What stays on disk (in the OS credential store) between launches.
#[derive(Clone, Debug, Serialize, Deserialize)]
struct Stored {
    username: String,
    client_id: String,
    client_secret: String,
    refresh_token: String,
}

struct Access {
    token: String,
    expires: Instant,
}

#[derive(Default)]
struct State {
    stored: Option<Stored>,
    access: Option<Access>,
}

/// Everything needed to sign in. `password` is used for this one request.
#[derive(Clone, Default)]
pub struct Credentials {
    pub username: String,
    pub password: String,
    pub client_id: String,
    pub client_secret: String,
}

#[derive(Default)]
pub struct Session {
    state: Mutex<State>,
}

fn entry() -> Result<keyring::Entry> {
    keyring::Entry::new(KEYRING_SERVICE, KEYRING_USER).map_err(|e| anyhow!("{e}"))
}

impl Session {
    /// Restores a previous sign-in from the credential store, if there is one.
    pub fn load() -> Self {
        let stored = entry()
            .ok()
            .and_then(|e| e.get_password().ok())
            .and_then(|json| serde_json::from_str::<Stored>(&json).ok());
        Self {
            state: Mutex::new(State { stored, access: None }),
        }
    }

    pub fn username(&self) -> Option<String> {
        self.state
            .lock()
            .ok()?
            .stored
            .as_ref()
            .map(|s| s.username.clone())
    }

    pub fn sign_in(&self, http: &reqwest::blocking::Client, creds: &Credentials) -> Result<()> {
        if creds.username.trim().is_empty() || creds.password.is_empty() {
            bail!("Enter your MangaDex username and password");
        }
        if creds.client_id.trim().is_empty() || creds.client_secret.trim().is_empty() {
            bail!("Enter the client ID and secret from your MangaDex personal API client");
        }
        let form = [
            ("grant_type", "password"),
            ("username", creds.username.trim()),
            ("password", creds.password.as_str()),
            ("client_id", creds.client_id.trim()),
            ("client_secret", creds.client_secret.trim()),
        ];
        let tokens = request_tokens(http, &form)?;
        let stored = Stored {
            username: creds.username.trim().to_owned(),
            client_id: creds.client_id.trim().to_owned(),
            client_secret: creds.client_secret.trim().to_owned(),
            refresh_token: tokens.refresh,
        };
        save(&stored)?;
        let mut state = self.state.lock().map_err(|_| anyhow!("session lock poisoned"))?;
        state.access = Some(tokens.access);
        state.stored = Some(stored);
        Ok(())
    }

    pub fn sign_out(&self) {
        if let Ok(mut state) = self.state.lock() {
            *state = State::default();
        }
        if let Ok(e) = entry() {
            let _ = e.delete_credential();
        }
    }

    /// A valid access token, refreshing it first when it's about to expire.
    pub fn access_token(&self, http: &reqwest::blocking::Client) -> Result<String> {
        let mut state = self.state.lock().map_err(|_| anyhow!("session lock poisoned"))?;
        if let Some(a) = &state.access
            && a.expires > Instant::now() + Duration::from_secs(30)
        {
            return Ok(a.token.clone());
        }
        let Some(stored) = state.stored.clone() else {
            bail!("You’re not signed in to MangaDex")
        };
        let form = [
            ("grant_type", "refresh_token"),
            ("refresh_token", stored.refresh_token.as_str()),
            ("client_id", stored.client_id.as_str()),
            ("client_secret", stored.client_secret.as_str()),
        ];
        let tokens = match request_tokens(http, &form) {
            Ok(t) => t,
            Err(e) => {
                // An expired or revoked refresh token means signing in again.
                if e.to_string().contains("invalid_grant") {
                    *state = State::default();
                    if let Ok(entry) = entry() {
                        let _ = entry.delete_credential();
                    }
                    bail!("Your MangaDex session expired. Sign in again in Settings");
                }
                return Err(e);
            }
        };
        let mut stored = stored;
        stored.refresh_token = tokens.refresh;
        let _ = save(&stored);
        let token = tokens.access.token.clone();
        state.access = Some(tokens.access);
        state.stored = Some(stored);
        Ok(token)
    }
}

struct Tokens {
    access: Access,
    refresh: String,
}

fn request_tokens(http: &reqwest::blocking::Client, form: &[(&str, &str)]) -> Result<Tokens> {
    let resp = http
        .post(TOKEN_URL)
        .form(form)
        .send()
        .context("Couldn't reach MangaDex sign-in")?;
    let status = resp.status();
    let body: Value = resp.json().unwrap_or(Value::Null);
    if !status.is_success() {
        let code = body["error"].as_str().unwrap_or("");
        let detail = body["error_description"].as_str().unwrap_or("");
        bail!("{}", explain(code, detail, status.as_u16()));
    }
    let access = body["access_token"]
        .as_str()
        .context("MangaDex didn't return an access token")?;
    let refresh = body["refresh_token"]
        .as_str()
        .context("MangaDex didn't return a refresh token")?;
    let expires_in = body["expires_in"].as_u64().unwrap_or(900);
    Ok(Tokens {
        access: Access {
            token: access.to_owned(),
            expires: Instant::now() + Duration::from_secs(expires_in),
        },
        refresh: refresh.to_owned(),
    })
}

/// Turns OAuth error codes into guidance people can act on.
fn explain(code: &str, detail: &str, status: u16) -> String {
    match code {
        "invalid_client" | "unauthorized_client" => {
            "MangaDex didn’t accept the client ID or secret. Check them on mangadex.org under Settings → API Clients, and make sure the client is approved".into()
        }
        "invalid_grant" if detail.to_lowercase().contains("credentials") => {
            "The username or password is incorrect".into()
        }
        "invalid_grant" => format!("MangaDex refused the sign-in (invalid_grant): {detail}"),
        "" => format!("MangaDex sign-in failed with HTTP {status}"),
        other => format!("MangaDex sign-in failed ({other}){}", if detail.is_empty() { String::new() } else { format!(": {detail}") }),
    }
}

fn save(stored: &Stored) -> Result<()> {
    let json = serde_json::to_string(stored)?;
    entry()?
        .set_password(&json)
        .map_err(|e| anyhow!("Couldn't save your sign-in to the system credential store: {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn explains_common_errors() {
        assert!(explain("invalid_client", "", 401).contains("client ID or secret"));
        assert_eq!(
            explain("invalid_grant", "Invalid user credentials", 401),
            "The username or password is incorrect"
        );
        assert!(explain("", "", 500).contains("HTTP 500"));
    }
}
