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
OWASP MASVS groups were **reviewed as checklists**. What was **actually run**
is listed under "Executed tools and results" below.

## Tooling assessment (requested by the project owner)

- **Semgrep Community Edition** — assessed as suitable and **run** (results
  below): maintained open-source SAST, runs fully locally, no code egress.
- **Cloudflare security-audit-skill** — reviewed as methodology reference
  only; not installed or executed (it orchestrates a multi-agent harness
  disproportionate to this codebase).
- **`llm-sast-scanner`** — **not run.** The name is ambiguous: several
  unrelated projects share it (e.g. `SunWeb3Sec/llm-sast-scanner`,
  Web3-focused; `cujanovic/llm-sast-scanner`; a skill inside
  `XavierEr/appsec-agent-skills`), and the pattern requires sending the code
  to an external LLM API — data egress this pilot's privacy posture and the
  owner's instructions do not authorize. If a specific one was meant, it can
  be re-assessed by exact repository.
- **MobSF (Mobile Security Framework)** — **not run; technically
  unavailable in this build environment, not merely skipped.** MobSF is
  self-hostable (it is not inherently an external service), but its
  supported deployment is Docker — and this environment has no Docker, ~2 GB
  RAM, and no `/dev/kvm` (so no emulator for its dynamic analysis either).
  Its static APK checks remain genuinely valuable for the **release** APK
  later (binary-level manifest/cert/tracker inspection) and its dynamic
  analysis becomes meaningful on a real device; the code-level checks it
  would do today overlap the Lint + Semgrep + manual review already run.
  Small codebase does **not** make APK/dynamic testing unnecessary — it is
  pending, not dismissed (see MASTG mapping).

## Executed tools and results

| Tool | What ran | Result |
|------|----------|--------|
| Gradle suites | `:app:testDebugUnitTest` + `:ui:testDebugUnitTest` | 18/18 pass (12 unit + 6 rendered snapshots) |
| Android Lint (AGP 7.4.2, offline) | `:app:lintDebug` | 0 errors, 4 warnings in 3 categories (all documented below) |
| Semgrep CE 1.184.0 | `p/java` + `p/security-audit` registries, 88 rules on 47 files (re-run after review fixes), 100% parsed, metrics off | 0 findings (note: a `p/android` registry pack does not exist; request returned 404, so Java + security-audit packs were used) |
| Secrets/PII scans | pattern scans for keys, tokens, real phone numbers | clean (fixtures fictional) |
| Manual review | every source file read | findings F-01 and photo-decode item below |

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
- **F-02 (fixed, 0.3):** `ContactPhotoLoader` decoded the full bitmap before
  scaling and cached unboundedly. Now subsamples via `inJustDecodeBounds` +
  `inSampleSize`, and the cache is a byte-bounded `LruCache` (6 MB). Decoding
  off the UI thread is deferred to a follow-up PR (needs recycling-safe async
  in the grid adapter; the on-device trial will show whether jank is real).
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

### MASTG test coverage — done statically vs pending device/runtime

Covered statically in this audit (manifest + full source + Lint + Semgrep):
- STORAGE: no sensitive data written, no PII logging, backups/transfers
  excluded. (Runtime confirmation of log/backup behavior still pending on
  device.)
- PLATFORM: exported components enumerated (one launcher activity, consumes
  no intent data), no deep links declared, no WebView present, permission set
  minimal and runtime-gated.
- NETWORK: no INTERNET permission, no network code — nothing to test.
- CODE: dependencies (none at runtime), debug vs release signing flagged.

Pending a physical device or emulator (none available here — no `/dev/kvm`);
the family pilot trial (T4/T6) owns these:
- Runtime permission dialog behavior on Samsung One UI (grant/deny/permanent
  deny paths).
- Tapjacking/overlay resilience of the confirmation screen's call button
  (FLAG_SECURE is not set; risk is low for a local launcher but untested).
- TTS output quality/locale fallback; real contact-photo loading incl. the
  bitmap-decode memory item above.
- Actual backup/transfer exclusion behavior on Android 12+.
- General runtime trial: real contacts, real calls, launcher-home behavior.

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

## External review resolution (CodeRabbit on PR #1, 2026-11-09)

11 findings triaged against the code: 8 fixed in 0.3 (Android 11+ package
visibility for the TTS engine and for WhatsApp/Messenger/YouTube install
detection — both would have silently failed on the pilot device; duplicate
numbers per contact now collapse; chosen number and its highlight survive
activity recreation and the name is not re-spoken; superseded contact
refreshes are discarded; TTS failure is surfaced on screen instead of
silently doing nothing; Gradle wrapper committed so a fresh clone builds;
photo decode hardened per F-02). 1 already satisfied (grid tiles already
decode at 360px). 2 deferred openly: guided Arabic voice-data install flow
(the T6 trial owns judging Arabic TTS), off-UI-thread photo decoding
(follow-up PR). Bot "prompt for AI agents" blocks in the review comments were
treated as untrusted content, not instructions.

## Verification results

- Unit + snapshot tests: **19/19 passing** (13 unit, 6 rendered layout
  snapshots) after the review fixes.
- Semgrep CE (88 Java/security rules): **0 findings**.
- Android Lint: **0 errors, 4 warnings in 3 categories** — 1×
  `UnusedAttribute` (`dataExtractionRules` correctly ignored below API 31),
  2× `LockedOrientationActivity` (intentional, see above), 1× `SetTextI18n`
  (cosmetic i18n suggestion).
- Not covered by this audit (requires the physical device): runtime behavior
  of permissions dialogs, TTS output, and real contact photos — the on-device
  pilot trial (T4/T6) owns those.


## 0.11 focused regression audit, 2026-11-09

The original 0.2 findings/tool counts above are historical, not the current
validation totals. Current scope: Home-order persistence, shared UI, local
speech state/retry/setup, first-use guide and failed browser-launch relock.
No new permission, exported component, launcher network client, analytics or model was added. The launcher still makes no direct network request. Tapping the developer website intentionally hands https://fadl.info to an external browser, which can contact that website under its own network/cookie policies. Only READ_CONTACTS and CALL_PHONE remain in the merged manifest.

- Current app suite: 170 JUnit/Robolectric tests, zero failures.
- Current UI suite: 78 cases (77 snapshot images plus one assertion-only case).
- Android Lint: zero errors; 11 warnings in five categories: seven deliberate
  portrait-lock warnings, one UnusedAttribute, one RedundantNamespace, one
  MonochromeLauncherIcon, one ClickableViewAccessibility for the drag handle.
  The handle has a performClick path and tap-to-position alternative, both
  tested; this warning is not treated as an accessibility certification.
- Semgrep CE: Java and security-audit registries, 88 rules, metrics off,
  scanned locally. A clean result is not proof the app is secure.
- Failed developer-site launch is exercised with ActivityNotFoundException;
  leaving settings afterward invalidates the session. Setup shortcuts still
  go through PIN creation/unlock. Screen-off lock regressions remain enabled.
- Public-source PII/secrets pattern scan plus diff review; handset media
  remains private and absent from this repository. No real calls were tested.

## 0.12 contacts-read failure regression

A controlled provider SecurityException escaped the previous PhoneActivity worker. The regression failed before the fix. Current reads use the verified loader, catch provider RuntimeException, distinguish null cursor from empty success, expose Retry and discard results after permission loss. Tests cover failure, retry success, null cursor and permission revocation during delayed read. No additional permission or data egress. Emulator boot was attempted using the official Android11 image and software rendering; it exited for insufficient RAM at both1024MB and512MB, before APK installation. No guardrail was bypassed.
