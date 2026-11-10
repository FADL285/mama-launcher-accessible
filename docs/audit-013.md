# Security and performance review: 0.13 candidate

Reviewed 2026-11-10 against merged base `c239668040c7231c10752bf01b034b0fae2d1a09` plus the handset-feedback working changes.

This is a bounded source/static review and regression pass, not a security certification, penetration test, complete dependency-advisory audit, or measured handset performance report. Prior audit documents describe earlier revisions; their test counts are not current results.

## Actually executed

| Check | Result |
| --- | --- |
| App JUnit/Robolectric tests | 186 cases, 0 failures |
| UI/Paparazzi tests, separate snapshot verification | 83 cases, 0 failures |
| Android Lint debug | 0 errors, 11 warnings |
| Semgrep CE 1.184.0, `p/java` + `p/security-audit` | 88 rules, 113 source targets, 0 findings, 0 scan errors, approximately 100% parsed |
| Tracked-source credential-pattern scan | No private keys, GitHub tokens or AWS access-key patterns found |
| Debug build and APK signing verification | Debug and release builds succeed; dedicated release signature verifies, with a different signer from the old installed pilot |
| Whitespace/error check | `git diff --check` clean |

Semgrep ran locally with metrics/version checking disabled; registry rules were downloaded. No source was uploaded to a hosted scanner. Source, fixtures, manifest, backups, intent handoffs, settings gate, photo loading, worker callbacks and the feedback diff were inspected. No live endpoint, call or handset was exercised.

External references, treated as checklists only:
- Cloudflare methodology: https://github.com/cloudflare/security-audit-skill
- OWASP MASVS: https://mas.owasp.org/MASVS/

The Cloudflare orchestration workflow was not installed or executed. Its independent-hunter/verifier procedure was not completed, so this report does not claim conformance to that workflow. MobSF and an LLM-hosted SAST tool were not run. No emulator/device dynamic review was completed.

## Boundary review

| Surface | Observed control / result |
| --- | --- |
| External activities | Only Main is exported, as required for a launcher. It consumes no externally supplied call/contact/settings payload. Internal call, contacts, dialpad, settings and PIN pages remain non-exported. |
| Sensitive data and egress | Only READ_CONTACTS and CALL_PHONE permissions. No INTERNET permission, network client, WebView, analytics, secret-bearing logs, or new storage/export route was found in the inspected application source. Speech still uses the separately installed TTS engine; that engine is outside this app's control. |
| Calls | DirectCallAction requires a fresh explicit press and permission; granting permission alone does not call. No change to call dispatch or speaker routing. |
| Protected settings | SessionGate remains memory-only, with persisted PIN lockout and explicit sanctioned external-return handling. ProtectedModeActivity retains its screen-off lock. New optional navigation does not opt Settings/PIN into an alternate gate. |
| New screen-off handling | Registers only while opted-in pages are started and unregisters onStop. Android SCREEN_OFF is the system signal; it does not accept arbitrary navigation parameters. Screen interaction/keyguard checks defer returning until unlock. Foreground checks prevent a screen-off during an ordinary external leave from arming the feature. |
| Background results | Existing contact/dialpad generation checks remain. Pages already finishing after an unlock return skip resume-time refreshes and avoid pointless worker/clock setup. |
| Backup | allowBackup=false and exclusion rules remain unchanged. No export/transfer feature added. |
| Test assets | Only synthetic contact data/artwork. No handset photographs or feedback attachments copied into the source tree. |

No newly confirmed cross-boundary vulnerability was found in this pass. Zero scanner findings does not prove the app is vulnerability-free.

## Performance review

The feedback change adds no recurring timer, database scan or background service. The existing Home clock still refreshes every 30 seconds while resumed and removes its callbacks on pause. Screen-off handling uses one receiver per opted-in started page and a boolean pending state. The measured header uses existing layout parameters rather than an extra posted layout listener or repeated `setLayoutParams` calls during measurement.

Unchanged limitations needing device measurement, not represented as fixed:

- ContactPhotoLoader bounds decoded dimensions and caches up to 6 MiB per loader, but image/provider I/O still happens synchronously when contact tiles and call confirmation are bound. A slow provider or many uncached photos can stall frames. Test cold and warm scrolling on the pilot handset with a realistic contact count. Moving this to asynchronous binding would need recycled-view and cancellation regression coverage and is outside the narrow feedback patch.
- Phone contact refresh reads on a worker thread, but each refresh starts a thread and stale work is discarded rather than cancelled. Rapid repeated resumes/retries and a slow provider can waste work. No runtime CPU/heap/jank measurements were taken.
- Main rebuilds app tiles on resume and checks package launchability; this behavior is unchanged. No claim of faster app launching or scrolling is made.
- Large-text headers may use two rows, consuming more vertical space by design. Short dialpad windows keep actions visible while keys/results remain scrollable; snapshots check this, but touch usability needs the handset.

## Lint warnings

- 7 LockedOrientationActivity: existing portrait accessibility choice.
- 1 UnusedAttribute: dataExtractionRules applies to Android 12+; legacy backup exclusions remain present.
- 1 RedundantNamespace: existing layout declaration.
- 1 MonochromeLauncherIcon: adaptive icon has no themed monochrome layer.
- 1 ClickableViewAccessibility: existing custom touch-guard handling calls performClick/performLongClick; TalkBack/touch-guard interaction still needs device testing.

These warnings were not suppressed to make the report clean.

## Release and supply-chain limits

The build still uses pinned but old Gradle/AGP/Paparazzi versions; this pass did not run a complete dependency advisory check or upgrade toolchains. The debug APK is debuggable by design and is not the delivery artifact. The held candidate uses the release build variant without a debuggable attribute; this alone does not make it a hardened or device-validated release.

The existing pilot signing key could not be recovered. The owner approved reinstall and a private backup of a dedicated key. The held 0.13 release APK uses that dedicated RSA-3072 key with valid v2/v3 signatures. Its encrypted keystore was backed up privately, permissions checked owner-only, re-downloaded byte-identical, and the downloaded key certificate matched the APK. No private backup link or password is stored in this repository. No APK was distributed or handset uninstall performed during this review.

Dedicated release certificate SHA-256: `5c77e65030a4d8c0a69398a3e56ec2e5be529ef54f25adada442f2a6154582d8`. Future updates must use that same key; the old pilot signer is different.

The owner chose F06 default ON on 2026-11-10. The switch remains configurable; tests pin the default and both saved choices.
