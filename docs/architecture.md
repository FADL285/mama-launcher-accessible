# Architecture and behavior boundaries

Audited baseline: 0.14, main `46c966d16575edbfb3aa86cc99b559a4b6832145` (2026-11-10).

## Shape

This is a small, framework-based, multi-activity Android application, not an MVVM implementation. Gradle module separation is useful, but it is not a full separation between UI and data: activities still read providers/preferences and manage background work.

| Area | Main types | Responsibility |
| --- | --- | --- |
| Daily navigation | `MainActivity`, `AppsPageActivity`, `BaseActivity` | Chosen apps, clock, touch guard, screen-lock return |
| Contacts | `PhoneActivity`, `ContactsLoader`, `ContactSorter`, `LocalFavorites` | Permission, provider reads, grouping, sorting and failure/retry |
| Dialpad | `DialpadActivity`, `DialContactMatcher`, `DialIntents` | Typed number, background matching, saved-number check and system handoff |
| Call boundary | `ConfirmCallActivity`, `CallDecision`, `DirectCallAction` | Visible target/number choice, deliberate action, permission and call intent |
| Setup protection | `PinGateActivity`, `ProtectedModeActivity`, `SessionGate`, `PinLock` | PIN/device credential, foreground-only session and lockout |
| Preferences | `Prefs`, `AppSelection`, `LocalFavorites`, `SpeechEngineChoice` | Local configuration, migration/order and user choices |
| Speech | `NameSpeaker`, `ArabicVoicePolicy`, `ArabicSpeechState` | Engine initialization, offline Arabic selection, state/retry and shutdown |
| Shared Views | `UiBinder`, `PageHeader`, `HomeClock`, `SettingsSections`, `SpeechStatusUi` | Layout binding, responsive headers, date/time and state presentation |
| Photos | `ContactGridAdapter`, `ContactPhotoLoader` | Reuse grid views, decode/subsample and byte-capped bitmap cache |

The classes above live under `app/src/main/java/info/fadl/mamalauncher/` or `ui/src/main/java/info/fadl/mamalauncher/ui/`. Layouts, strings, fonts and themes live in `ui/src/main/res/`; app identity/manifest and backup rules live in `app/src/main/`.

## Data and effects

1. The contacts provider is the authority for contact names, numbers, photos and system favorites. `ContactsLoader` groups rows by contact ID and removes repeated number rows.
2. App-local favorites are an overlay, not an edit to system contacts. Home/Apps placement and ordering live in `SharedPreferences` (`pilot`). Missing packages are hidden at render time rather than removed from stored choices.
3. Phone performs the contacts query in a raw worker thread, then binds on the main thread. Generation/lifecycle/permission checks reject stale results. Bitmap reads/decodes still happen synchronously during binding or confirmation creation; see audit A01.
4. Dialpad uses a single-thread executor for provider reads and matching. Generation counters discard obsolete queued/results work. Save re-reads contacts before treating a number as new; failure is not an empty address book. The executor is shut down on destruction.
5. A matching contact goes to confirmation, never directly to a call. A new Save action opens the system contact-insert form. Call intents are started only by the deliberate call action, with permission checked at tap time.
6. TTS is an external engine service. `NameSpeaker` uses generation guards, initialization timeouts and shutdown on leaving confirmation. App policy selects voices reported as installed/offline Arabic; readiness is not proof of audible speech or correct pronunciation.

## Invariants to preserve

- Permission grants, lookup completion, speech callbacks, Listen and Favorite must not start a call.
- A multi-number contact must have a visible chosen target before Call.
- Provider failure/null cursor or denied access must not claim a number is new or contacts are empty.
- A stale callback must not navigate or replace newer data. Recycled photo views must never receive another person's delayed image in a future async implementation.
- A manual TTS-engine choice is binding on app-managed fallback. Do not silently install an engine/model or send names to a cloud voice.
- The settings session is memory-only; protected screens recheck it and lock on ordinary departure/screen-off. Sanctioned app-info/TTS-system-screen excursions preserve the session. Opening the developer website in a browser also sets this external-session exception: it remains until Settings resumes and clears the flag, screen-off locks protected mode, or the process ends. Leaving that browser for another app can therefore leave the in-memory settings session unlocked. This is a current limitation, not a general guarantee that every departure locks immediately.
- Home-after-lock defaults ON for Phone/Apps/Dialpad. Confirmation is intentionally excluded. Protected settings have their own screen-off lock.
- Touch guard changes physical touch timing, not the availability of a keyboard/accessibility click action.
- Full app names remain available through accessibility/long press even when visible text is fitted or ellipsized.
- An update retains package identity, signing certificate and compatible preferences. Never treat uninstall/reinstall as an ordinary update.

## What is good already

Pure helpers make ordering, number matching, voice policy and PIN decisions testable. Data-provider failures, stale results, permission grants that must not auto-call, large text and RTL have targeted regressions. Framework-only runtime keeps the dependency surface small. This does not eliminate build/test dependencies or platform behavior differences.

## Where to improve later

Favor small seams over a rewrite: introduce a contacts repository/provider boundary and a cancellable photo loader, then move settings sections into focused controllers while retaining current activity navigation and preference keys. A ViewModel may help later if lifecycle/state work grows. A Compose/Kotlin/single-activity migration is not required to improve this app and would not be a low-risk stable-release cleanup.

See [audit A01-A08](audit-014.md) for priority, evidence and validation gates. Architecture guidance is a design aid, not a mandate to replace working code.
