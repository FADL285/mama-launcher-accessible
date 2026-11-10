# Stable 0.14 audit

Date: 2026-11-10. Scope: performance, architecture, maintainability, release practices, accessibility and security boundaries.

**Recommendation: keep 0.14 installed. Review documentation separately; do not replace a working handset build for speculative cleanup.** The highest-value performance follow-up is measured photo-loading work, not a rewrite. One narrow call-permission cancellation edge case was reproduced in an isolated JVM test and is a candidate for a later small patch. No runtime improvement below is implemented or approved.

## Baseline and evidence

- Main commit: `46c966d16575edbfb3aa86cc99b559a4b6832145`, tree `f27727be81394eef9d25854257c8f1b29bb23619`, merged [PR #10](https://github.com/FADL285/mama-launcher-accessible/pull/10).
- App version remains `0.14-pilot-candidate`, code 16. "Stable" describes the current handset handoff decision, not a different binary.
- Fresh audit run: `:app:testDebugUnitTest :ui:verifyPaparazziDebug :app:lintDebug --rerun-tasks`: 206 app tests, 88 UI tests, zero failures/errors/skips. Goldens verified, not regenerated.
- Lint: 0 errors / 11 warnings. Seven portrait-orientation warnings; one each for API31 backup attribute, redundant namespace, missing monochrome icon and custom-touch accessibility.
- Semgrep Community: `p/java` + `p/security-audit`, metrics off; 88 applicable rules, 36 production Java files, zero findings/errors. Not a dependency-vulnerability audit or proof of no vulnerabilities.
- A temporary audit-only Robolectric/API30 probe reproduced A02. It inspected permission intents/feedback, never delivered a call, and was removed after execution. It is not part of the baseline's 206 tests.
- Reviewed production source inventory: 36 Java files, approximately 3,105 lines; manifest, build files, backup rules and relevant layouts/tests. Line count is inventory, not a quality score.
- No physical-device profiling, APK replacement, device permission mutation, live call, remote source-code scanner or new runtime dependency. No new APK.

Findings distinguish confirmed code behavior, inferred risk and validation gaps. Priority ranks a future backlog; it is not an instruction to interrupt the stable handoff.

## Ranked findings and proposals

| ID | Priority / evidence | Finding | Next step | Stable-release exposure |
| --- | --- | --- | --- | --- |
| A01 | Medium; synchronous path confirmed, latency unmeasured | Photo I/O/decode on main thread | Measure cold grid/detail; then cancellable async loading if material | Runtime change; separate approval/tests |
| A02 | Medium; JVM reproduction | Cancelled call-permission result persisted as denial | Small cancellation-vs-denial patch and regressions | Changes permission behavior; do not auto-ship |
| A03 | Medium; resource-use risk | Refresh workers not cancelled/coalesced | Bounded worker and provider cancellation | Concurrency change; regression risk |
| A04 | Low; potential inefficiency | Home rebuilds tiles/app metadata on every resume | Trace before caching | Cache/state change; defer without measurements |
| A05 | Medium; tooling gap | No committed CI or wrapper distribution checksum | Separate CI/build-integrity PR | No intended app change; build-gate risk |
| A06 | Medium; validation gap | Accessibility services/device performance unmeasured | Authorized synthetic test-device matrix | Evidence only; no stable binary change |
| A07 | Low; maintainability judgment | Broad activity responsibilities/dense formatting | Small data/controller seams, separate formatting | Refactors are not risk-free |
| A08 | Future scope | Portrait/platform assumptions are not a tablet plan | Separate capability/layout/toolchain evaluation | Do not alter the phone build to silence warnings |

### A01. Photo loading on the main thread

Evidence: [ContactGridAdapter.java](../ui/src/main/java/info/fadl/mamalauncher/ui/ContactGridAdapter.java), `getView`, line 56 calls `photos.load(...,360)` synchronously. [ConfirmCallActivity.java](../app/src/main/java/info/fadl/mamalauncher/ConfirmCallActivity.java), `onCreate`, line 89 loads a 900px photo. [ContactPhotoLoader.java](../ui/src/main/java/info/fadl/mamalauncher/ui/ContactPhotoLoader.java), lines 31-73 opens streams, decodes bounds/pixels and scales before returning.

The call path confirms main-thread I/O/decode, **not measured delay, ANRs or excessive RAM on this handset**. View recycling, a 6MiB byte-bounded LRU and subsampling are good existing safeguards. The cap is per loader, not whole-process RAM or peak transient decode allocation.

Proposal: record cold/warm photo-grid/detail traces with synthetic large images first. If material, use a bounded worker, immediate placeholders, contact-ID/URI/size checks before applying results and cancellation on recycle/close. Keep unreadable-photo and permission-loss fallback. A library such as Glide is an option, not a mandatory dependency for this small local-only app.

Secondary risk: `misses` remembers unreadable URIs for the loader's lifetime without expiry; results can stay stale if a provider changes content at the same URI. Cover invalidation in a future design. The URI-only cache key is **not** a proven grid/detail-size defect: those screens currently use different loader instances.

Acceptance: no wrong-person photo on rapid scroll, correct cancellation/revocation fallback, lower measured main-thread decode time, and no memory regression. Sources S1-S3.

### A02. Call-permission cancellation treated as denial

Evidence: [DirectCallAction.java](../app/src/main/java/info/fadl/mamalauncher/DirectCallAction.java), lines 29-41. `permissionResult` sets `call_denied_once=true` whenever a result is not granted, including an empty result. A subsequent tap with that flag and a false rationale reports blocked without requesting permission.

Reproduction: isolated API30 activity with CALL_PHONE denied; tap queues a permission intent; simulate an empty callback; clear the framework in-flight-request flag as normal dispatch would; tap again. The preference becomes true and the second tap has no permission intent. This proves the JVM branch, not every Samsung cancellation/rationale combination.

Impact: a recoverable cancellation can need setup help on the next call attempt. It fails closed: **no unintended call occurs**. Already-granted permission bypasses this path. It is separate from contacts retry fixed in 0.14.

Proposal: distinguish interrupted/empty result from completed denial; test rationale, repeated denial, grant, revocation and duplicate taps across API24/30/33. Keep the rule that a permission callback never places a call. Do not blindly copy contacts UX or promise Android always shows its dialog. Source S4.

### A03. Refresh work lacks cancellation/coalescing

Evidence: [PhoneActivity.java](../app/src/main/java/info/fadl/mamalauncher/PhoneActivity.java), lines 59-65 and 86-115: each resume/refresh with permission creates a raw thread and full contacts read. Generation/lifecycle checks reject stale UI results, but there is no provider `CancellationSignal` or worker shutdown. [SettingsActivity.java](../app/src/main/java/info/fadl/mamalauncher/SettingsActivity.java), lines 253-279 also owns a raw discovery thread with destroyed/finishing checks when applying results.

This is a slow-provider/repeated-navigation resource risk, **not proof of a visible leak or stale selection**. Existing checks are useful. Dialpad already owns an executor with shutdown/generation guards; replacing it wholesale would discard working protections.

Proposal: a small contacts data boundary with bounded executor, explicit request ownership and provider cancellation where supported. Test overlapping reads, pause/resume/destruction, failure and permission loss. Preserve error-versus-empty semantics. Sources S3, S5.

### A04. Home reconstruction and layout work

Evidence: [MainActivity.java](../app/src/main/java/info/fadl/mamalauncher/MainActivity.java), lines 84-99 and 112-150 rebuilds tiles on resume and reads package labels/icons on the main thread. [UiBinder.java](../ui/src/main/java/info/fadl/mamalauncher/ui/UiBinder.java), lines 171-209 fits labels and measures twice to retain equal cards/visible centering.

For a small selected app list, this may be negligible. Two-pass layout is intentional and regression-tested, not inherently a bug. Only cache if traces justify it, with invalidation for package, placement/order, font and theme changes. Preserve Home reset and missing-app persistence. Do not trade readable centered labels for an unmeasured speedup. Source S3.

### A05. CI and build integrity not enforced in source

Evidence: no tracked `.github/workflows/`, dependency-verification metadata or dependency lock files. [gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) pins a URL without `distributionSha256Sum`. Versions are fixed and the runtime has no third-party library dependencies, but build/test dependencies still resolve externally.

Proposal: independently verify Wrapper JAR/distribution against official Gradle checksums, then add the verified checksum and reviewed dependency-verification policy. Add CI for tests, golden verification, lint and debug assembly without release secrets; review/pin any CI actions separately. Keep intentional warnings visible rather than disabling lint. Missing verification does not prove compromise or a known exploit.

Tooling-only work has no intended app behavior change but can break builds or accept wrong goldens. It is lower exposure than a runtime refactor, **not zero risk**. Toolchain/SDK upgrades need a separate compatibility branch. Sources S6-S7.

### A06. Accessibility/device evidence incomplete

Evidence: no committed `androidTest` suite; current checks are JVM/layout-based. Lint flags `SettingsActivity` line 321 for custom touch. Its tap path calls `performClick` and offers a tap-to-position alternative, so the warning alone does not prove reorder inaccessible. Large targets/full-name descriptions do not establish focus order or screen-reader announcements.

Proposal: on a separate authorized device, test TalkBack/Switch Access and keyboard from Home through Phone, number choice, Listen, Back, gate and reorder. Check duplicated name/photo announcements, roles, focus order, long names, touch guard and recovery dialogs. Check that alpha-only unavailable-looking actions are understandable. Record issues before suppressing warnings or adding descriptions to decorative images. No accessibility/WCAG certification is claimed. Source S8.

### A07. Architecture and maintainability seams

Evidence: `SettingsActivity` combines TTS, permissions, preferences, app discovery and reorder (~446 lines). `DialpadActivity` combines provider reads, matching/save state, navigation and keys (~226 lines). Several helpers use dense one-line methods. The `ui` module includes provider-backed photo I/O as well as rendering.

Positive: pure matching/ordering/voice/PIN helpers and targeted lifecycle/failure tests already exist. This is a maintainability judgment, not a measured functional defect.

Proposal: extract one seam per reviewed PR (contacts repository, settings section controller, speech setup controller). Pin preference keys/migration, safe call boundaries and physical-left/right layout. Format separately after tests. Kotlin, Compose, dependency injection or single-activity migration is not required. Android explicitly permits adapting its architecture recommendations. Source S5.

### A08. Tablet/platform work is separate

All seven activities request portrait; min24/compile33/target33; AGP7.4.2/Paparazzi1.2.0/Gradle7.6.4. Seven orientation warnings do not make portrait a defect on the current phone. A10s-like renders do not cover tablets, landscape, multiwindow or telephony.

Before choosing a tablet, check voice/SIM calling capability, default phone app, installed speech engines, RAM and real layouts. Separate larger-screen design from toolchain/target upgrades. This audit does not assess app-store policy or certify future OS support. Source S7.

## Security/privacy verdict within scope

No verified release-blocking security defect was found in the reviewed surfaces. This is a bounded source/static review, not a penetration test or guarantee.

- Manifest exports the intended Home/launcher activity; other activities are non-exported. Permissions are contacts/calling only; no internet permission or analytics SDK in the audited runtime configuration.
- Contacts/matching stay local in this implementation. External TTS/system apps remain a separate trust boundary; offline-voice metadata is not an audit of the engine.
- PIN uses random salt, PBKDF2 and persisted lockout; sessions are memory-only and rechecked. Four digits are a convenience guard, not hardware-bound/root-resistant account security. More KDF iterations alone would not create that boundary and need low-end-device timing. Source S9.
- Backup is disabled with exclusions. Preferences/favorites are not a migration/backup system. The old backup-rule comment mentions only one flag; executable rules exclude broader data. No runtime XML change is needed for this docs patch.
- No keys, private download links or real personal fixtures are included. No code license is invented; that remains the owner's decision.

## Safe sequence without disturbing the stable build

1. **Now, docs only:** current README, family/build/architecture guides and historical index. No manifest/resources/Gradle/runtime/test changes, no APK.
2. **Evidence only:** profile A01/A03/A04 and test accessibility on a separate authorized synthetic-data device. No change to the deployed handset without permission.
3. **Owner chooses a small patch:** A02 is concrete; A01 is a measured-performance candidate. Each requires approval, regressions, review and a new release only if wanted.
4. **Tooling PR:** CI/checksum verification with independently verified values; no bundled toolchain upgrade.
5. **Later:** architecture seams, tablet evaluation and features. No rewrite for its own sake.

## Measurement plan before performance work

On an authorized non-production device, use synthetic address books (for example 50/500/2,000 contacts), with/without photos, large images and unreadable URIs. Record model, OS, RAM, build mode, font size, selected app count and contact count.

Capture cold/warm Home, open Phone, grid fling, repeated detail visits, return from system screens without making calls, rapid dialing during slow reads and lifecycle interruptions. Collect frame/jank traces, main-thread CPU samples, peak/settled memory and active worker counts with Android Studio/Perfetto or equivalent tools.

Compare repeated runs/distributions, not a single fast run or JVM wall-clock timing. Baseline first, then set a target from the observed problem. Require no wrong-person image, no unintended call, bounded work/memory and preserved large-text/RTL/contrast behavior. **No benchmark numbers are claimed.**

## Source ledger

Primary evidence is pinned source and executed checks above. Public sources were searched/fetched on 2026-11-10: platform/build docs and a security standard, not independent handset benchmarks. No community anecdote was used to infer this handset's performance.

- **S1, Android Developers, Handling bitmaps:** threading/cache/memory risks. https://developer.android.com/develop/ui/views/graphics
- **S2, Android Developers, Loading large bitmaps efficiently:** bounds/subsampling/target sizing. https://developer.android.com/topic/performance/graphics/load-bitmap
- **S3, Android Developers, Better performance through threading:** UI work, activity references and worker lifetime. https://developer.android.com/topic/performance/threads
- **S4, Android Developers, Request runtime permissions:** empty interrupted callbacks/rationale/denial limits. https://developer.android.com/training/permissions/requesting
- **S5, Android Developers, Architecture recommendations for Views:** separation/lifecycle and adapting recommendations. https://developer.android.com/topic/architecture/views/recommendations-views
- **S6, Gradle, Security best practices:** distribution checksum/Wrapper verification. https://docs.gradle.org/current/userguide/best_practices_security.html
- **S7, Android Developers, AGP7.4 release notes:** JDK11/Gradle7.5 minimums. https://developer.android.com/build/releases/agp-7-4-0-release-notes
- **S8, Android Developers, Test accessibility in Views:** analysis, automation and user-oriented testing. https://developer.android.com/guide/topics/ui/accessibility/views/testing-views
- **S9, OWASP, MASVS-AUTH-2:** local authentication/platform best practices, not a compliance badge. https://mas.owasp.org/MASVS/controls/MASVS-AUTH-2/
