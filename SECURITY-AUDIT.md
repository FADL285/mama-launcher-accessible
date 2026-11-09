# Security Audit — 0.2-pilot (pre-publication, 2026-11-09)

## Scope and method

Methodology adapted from Cloudflare's `security-audit-skill`
(github.com/cloudflare/security-audit-skill, reviewed as reference only — no
third-party scripts installed or executed): reconnaissance of trust boundaries
and input surfaces, attack-class hunting (exported components, deep links,
supply chain, data lifecycle), candidate validation, and structured verdicts
(`fixed` / `rejected` / `needs_validation`). Second reference: **OWASP MASVS**
(mas.owasp.org/MASVS, with MASTG/MASWE) — its control groups were walked as a
checklist for this app (see mapping below).

Distinguishing references from execution: the Cloudflare skill phases and the
OWASP MASVS groups were **reviewed as checklists**; what was **actually run**
is: the Gradle test suites, Android Lint, secrets/PII pattern scans, dependency
review, and a manual read of every source file. No external scanner or skill
was installed or executed.

## Trust boundaries

- Offline launcher: **no `android.permission.INTERNET`** declared. No network,
  WebView, IPC, providers, services, or receivers exist in the codebase.
- Data source: the device contacts provider only (`READ_CONTACTS`).
- Effect: placing a phone call only (`CALL_PHONE`), only from an explicit tap
  on the confirmation screen's green button, after a visible number choice for
  multi-number contacts, with a 1.5 s debounce. A runtime permission grant
  never starts a call by itself (pinned by `CallDecisionTest`).
- Call URI is built as `"tel:" + Uri.encode(selectedNumber)` from contacts data.

## Findings

- **F-01 (fixed):** Android 12+ cloud backup / device-to-device transfer was
  not explicitly excluded (`allowBackup=false` covers cloud backup but is
  deprecated on 12+). Added `android:dataExtractionRules`
  (`res/xml/data_extraction_rules.xml`) plus `android:fullBackupContent`
  (`res/xml/backup_rules.xml`) excluding all app data on every Android version.
  The app stores only one boolean flag, but "nothing leaves the device" is now
  enforced explicitly.
- **Rejected (intentional):** `LockedOrientationActivity` — portrait lock is a
  deliberate accessibility choice for the pilot audience, not an oversight.
- **Rejected (by design):** `MainActivity` is `exported=true` because a home
  launcher must be. It consumes no intent extras or data, so the exported
  surface carries no input risk. `ConfirmCallActivity` is `exported=false`;
  its extras are internal only.
- **needs_validation (low):** `ContactPhotoLoader` decodes the full bitmap
  before scaling down. An oversized local contact photo could cause memory
  pressure. Data source is the local contacts provider only, so impact is
  limited to the app's own UI; a bounds pre-pass (`inJustDecodeBounds`) is
  planned for the next iteration.
- **Note (not a defect):** the pilot APK is debug-signed and debuggable; the
  signing key is local and intentionally absent from this repository. Release
  signing is a future owner decision, as is the code license.

## OWASP MASVS control-group mapping (checklist review)

- **STORAGE** — pass: one boolean in `MODE_PRIVATE` SharedPreferences; backups
  and device transfer fully excluded (F-01 fix).
- **CRYPTO** — N/A: no cryptography used or needed.
- **AUTH** — N/A: no accounts or authentication.
- **NETWORK** — pass: no `INTERNET` permission; zero network code.
- **PLATFORM** — pass: minimal exported surface (see findings), two runtime
  permissions with explicit gating, no WebView, encoded `tel:` URI.
- **CODE** — pass with note: zero third-party runtime dependencies; pilot is
  debug-signed (release signing is a future decision); minSdk/targetSdk are
  provisional pilot choices.
- **RESILIENCE** — consciously out of scope for this pilot (no obfuscation or
  anti-tamper goal for a family-trial accessibility app).
- **PRIVACY** — pass: no collection, no tracking, no PII logging, fictional
  fixtures only, nothing leaves the device.

## Verified clean

- No secrets, keys, tokens, real names, or real phone numbers; all fixtures
  are fictional (`0100000000x`).
- No `Log.*` calls — no contact PII is logged.
- No dangerous APIs: no WebView, `Runtime.exec`, dynamic code loading,
  world-readable/writable modes, sockets, or HTTP clients.
- Permissions are exactly `READ_CONTACTS` + `CALL_PHONE`.
- Zero third-party **runtime** dependencies (only the internal `:ui` module).
  Test-only: JUnit 4.13.2, Paparazzi 1.2.0.
- `SharedPreferences` use is `MODE_PRIVATE`.

## Verification results

- Unit + snapshot tests: **18/18 passing** (12 unit, 6 rendered layout
  snapshots) after the F-01 fix.
- Android Lint: **0 errors, 4 warnings in 3 categories** — 1×
  `UnusedAttribute` (`dataExtractionRules` correctly ignored below API 31),
  2× `LockedOrientationActivity` (intentional, see above), 1× `SetTextI18n`
  (cosmetic i18n suggestion).
- Not covered by this audit (requires the physical device): runtime behavior
  of permissions dialogs, TTS output, and real contact photos — the on-device
  pilot trial (T4/T6) owns those.
