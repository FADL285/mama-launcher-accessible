# Build, test and release continuity

Toolchain baseline: 0.14/code16. The app has `full` (versionCode 16, `0.14-pilot-candidate`) and `noNotif` (versionCode 20, `0.15-no-notif-footer-candidate`) flavors, matching `app/build.gradle`; the README's Current status table is the canonical version reference. This guide documents the existing build; it does not upgrade tools or authorize a new release. The delivered noNotif20 candidate is documented in [candidate validation](no-notification-candidate.md).

## Toolchain

| Part | Repository setting / verified environment |
| --- | --- |
| Gradle Wrapper | 7.6.4 |
| Android Gradle Plugin | 7.4.2 |
| JDK | 11 used for baseline checks |
| Android SDK | Platform 33; Build Tools 30.0.3 for AGP's default |
| Android API | min24, compile33, target33 |
| Source language | Java8 source/target compatibility |
| UI test tool | Paparazzi 1.2.0 |
| App test tools | JUnit 4.13.2, Robolectric 4.11.1 |

AGP 7.4's documented minimums include Gradle 7.5 and JDK 11. Newer build tools/JDKs are not automatically drop-in upgrades for this pinned test stack. Use the wrapper, not an unrelated globally installed Gradle.

Install the SDK platform/build tools, set `ANDROID_HOME` or a local untracked `local.properties`, and set `JAVA_HOME` to JDK 11. First builds need network access to obtain build/test dependencies; the app itself has no internet permission.

```bash
export JAVA_HOME=/path/to/jdk-11
export ANDROID_HOME=/path/to/android-sdk
./gradlew --version
./gradlew :app:assembleNoNotifDebug
./gradlew :app:testNoNotifDebugUnitTest :app:testFullDebugUnitTest
./gradlew :ui:verifyPaparazziDebug :app:lintNoNotifDebug :app:lintFullDebug
```

Choose the intended capability explicitly:

| Flavor | Debug task | Debug APK |
| --- | --- | --- |
| noNotif (no notification listener) | `:app:assembleNoNotifDebug` | `app/build/outputs/apk/noNotif/debug/app-noNotif-debug.apk` |
| full (notification listener included) | `:app:assembleFullDebug` | `app/build/outputs/apk/full/debug/app-full-debug.apk` |

Generic aggregate assembly can build both flavors; it does not choose the safe distribution target for you. `local.properties`, build output and signing material must not be committed.

## What the checks cover

- `:app:testNoNotifDebugUnitTest` / `:app:testFullDebugUnitTest`: pure JUnit and JVM Android/Robolectric flows. Calls are inspected as intents, not delivered to a carrier.
- `:ui:verifyPaparazziDebug`: renders layouts and compares with committed synthetic golden images. Includes RTL, long names, A10s-like configurations, high contrast and large text cases.
- `:app:lintNoNotifDebug` / `:app:lintFullDebug`: static Android checks. Read warnings; passing with warnings is not a clean accessibility certification.
- Optional SAST: `semgrep scan --config p/java --config p/security-audit --metrics=off app/src/main/java ui/src/main/java`. Fetching rules requires network. No source upload/remote analysis is needed for this scan; assess any added service separately.

Fresh 2026-11-10 baseline run: 206 app tests + 88 UI tests, no failures/errors/skips; lint 0 errors / 11 warnings; Semgrep 88 applicable rules across 36 production Java files, 0 findings/errors. Test counts change when tests are intentionally added; do not make the count a substitute for reading failures.

App reports are under `app/build/reports/tests/testNoNotifDebugUnitTest/` and `testFullDebugUnitTest/`; UI reports are under `ui/build/reports/tests/testDebugUnitTest/`; lint XML/HTML is under `app/build/reports/`. Failure images/diffs are local evidence under generated test output, not golden fixtures.

### Changing a visual baseline

Only after an approved UI change:

1. Run verification first and understand every failing image.
2. Record the intended change: `./gradlew :ui:recordPaparazziDebug` (or select the affected tests).
3. Inspect actual before/after pixels, not only filenames or a passing task. Check RTL/LTR, largest text, contrast and short-window states affected by the change.
4. Confirm fixtures contain no real contact names, numbers or photos.
5. Run `:ui:verifyPaparazziDebug` separately and commit the intended goldens with the source change.

Do not record all goldens merely to make an unexplained failure pass.

## Release continuity

Use a flavor-specific release task:

| Flavor | Release task | Unsigned APK |
| --- | --- | --- |
| noNotif | `:app:assembleNoNotifRelease` | `app/build/outputs/apk/noNotif/release/app-noNotif-release-unsigned.apk` |
| full | `:app:assembleFullRelease` | `app/build/outputs/apk/full/release/app-full-release-unsigned.apk` |

The noNotif flavor is the source of the delivered19 APK. Full currently inherits code16 from main, so do not use it as an update over19 without a separately authorized version/capability decision. There is no release-signing configuration in the repository. The distributed 0.14 was separately aligned and signed with the project's dedicated release key; do not commit that key or its password.

- A debug APK has a different signer and is not an update to the release installation.
- An ordinary update needs the same application ID and signing identity, with an appropriate version code. Confirm the existing installation before planning an update.
- Preserve the dedicated key through the owner's private backup process. Do not put keys, passwords, vault links or private download links in issues, commits, docs or CI logs.
- Uninstall/clear storage removes app-local preferences, PIN and local favorites. It is destructive recovery, not the first troubleshooting step.
- Before delivery: verify the exact source commit/tree, package/version, final APK hash and signing certificate; install/update only with owner authorization. A build success alone is not release approval.

Example verification commands (Android SDK build tools on PATH):

```bash
apksigner verify --verbose --print-certs approved-release.apk
sha256sum approved-release.apk
# On an authorized test device only, after checking installed identity/version:
adb install -r approved-release.apk
```

No APK is produced for the docs-only audit follow-up. These commands document a future authorized release workflow, not a request to replace the stable installation.

## Device validation that local tests cannot replace

On a separate authorized test handset with synthetic contacts, capture cold/warm startup, contact-grid fling, repeated confirmation visits, large photos and slow-provider behavior. Record frame timing and memory with device/OS/build/workload details. Test empty/denied/granted permissions and interrupted requests, process death and screen lock.

Run TalkBack/Switch Access and hardware-keyboard traversal. Check speech audibly with network off and chosen engine/volume recorded. Real carrier calls, emergency/special numbers and contact edits have real effects: do not execute them automatically as a test.

Tablet/landscape testing is a separate matrix, including whether the device can place calls at all. See [audit measurement plan](audit-014.md#measurement-plan-before-performance-work).

## Proposed tooling work, not applied

No committed CI workflow, dependency verification metadata or wrapper distribution checksum exists in the audited baseline. A future PR can add CI/test gates, verify the wrapper/distribution checksums against official Gradle publications, and review dependency verification. Verify checksums independently; never paste a guessed value. Toolchain/target upgrades belong in their own tested branch, not a docs patch.


## Historical notification/history feature review (before flavors, no APK)

This historical record predates the flavor split and the subsequent HomeLabel lint fix. Its task names are not current build instructions; use the flavor-specific tasks above. At the time, the review deliberately kept versionCode 16 / versionName `0.14-pilot-candidate` and does not package or install a candidate. Run compilation, JVM/layout tests and lint without assemble/package application tasks:

```sh
./gradlew :app:compileDebugJavaWithJavac :ui:compileDebugJavaWithJavac
./gradlew :app:testDebugUnitTest --max-workers=1
./gradlew :ui:verifyPaparazziDebug --max-workers=1
./gradlew :app:lintDebug --max-workers=1
./gradlew :ui:lintDebug --max-workers=1
```

On a 2GB runner, running lint and Robolectric concurrently exhausted memory once (exit 137); sequential, single-worker runs completed. The unchanged UI HomeLabel break-strategy constant fails standalone UI lint on this SDK/toolchain, also reproduced on main. Do not suppress it or claim standalone UI lint is green. The feature validation record separates that existing error, new warnings, tests and handset gaps. Release/signing/install remains a separately approved stage.

## CI gates (T-02, added 2026-11-13)

`.github/workflows/ci.yml` runs on every PR and on pushes to `main`, on a pinned `ubuntu-22.04` image with Temurin JDK 11 and Android SDK platform 33 / Build Tools 30.0.3 (the golden-recording baseline). Jobs are separate on purpose: a combined lint+Robolectric run hit exit 137 on a 2GB runner.

1. **wrapper** - Gradle wrapper jar validation (`gradle/actions/setup-gradle` `validate-wrappers`, pinned by SHA). The wrapper distribution itself is checksum-pinned via `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` (verified against gradle.org and services.gradle.org; a tampered value fails with an expected/actual checksum error - negative control run locally 2026-11-13).
2. **unit-tests** - `:app:testNoNotifDebugUnitTest :app:testFullDebugUnitTest`.
3. **goldens** - `:ui:verifyPaparazziDebug`.
4. **lint** - `:app:lintNoNotifDebug :app:lintFullDebug :ui:lintDebug` (the fresh UI lint run: 0 errors on main as of 2026-11-13; any new failure is handled under T-10, never suppressed).
5. **manifest-check** - merges both flavor manifests and fails if `LauncherNotificationListener` appears in the noNotif merged manifest (artifact-input level, beyond the Robolectric `NoNotificationBuildTest`); a positive control asserts it IS present in the full merged manifest so the check is not vacuous.
6. **package** - `:app:assembleNoNotifDebug :app:assembleFullDebug`, only after all checks pass. CI-only debug packaging: never uploaded, never distributed, no signing.

Dependency payloads are checksum-pinned by `gradle/verification-metadata.xml` (sha256; signatures off). It covers every dependency of the CI task set; regenerate with `./gradlew --write-verification-metadata sha256 <ci tasks>` when dependencies change, and review the diff like a lockfile.

Intentional flavor-specific skips: `NotificationListenerFlowTest` is skipped on noNotif (`Assume` on `BuildConfig.NOTIFICATION_ACCESS`) and `NoNotificationBuildTest` is skipped on full - each suite's skip count is the other flavor's listener tests, not a hidden failure.
