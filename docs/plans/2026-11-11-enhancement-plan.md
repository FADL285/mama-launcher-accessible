# Mama Launcher enhancement plan (post-audit)

Date: 2026-11-11 (Africa/Cairo), revised 2026-11-12 after Codex + Claude review of PR #19. Status: proposed, awaiting owner decisions and review comments. This document records tasks only; it implements nothing and authorizes no release.

Sources: independent audit `docs/audits/2026-11-11-codex-gpt-audit.md` (PR #18, merged), prior `docs/audit-014.md`, an independent re-verification of all seven audit findings against the source at `564f455` (production-equivalent to current main; PR #16 changed only test PNGs), and the [codex]/[claude] review comments on PR #19, each independently re-verified against main `79774e4` before incorporation.

## Standing constraints (apply to every task)

- `noNotif` (versionCode 20, `0.15-no-notif-footer-candidate`) stays listener-free. Do not re-add the notification listener to it.
- `full` (versionCode 16, `0.14-pilot-candidate`) cannot update an installed noNotif20 build. That is an accepted limitation, not a defect; do not bump full16's version as a "fix".
- Call permission callbacks never place calls. Every call requires a fresh explicit Call press with a current grant check.
- Call-log access stays read-only. No writes to the system call log, no notification cancellation.
- No rewrites, no framework/language migration, no targetSdk bump, no signing or distribution changes, no Drive uploads. Play target-API requirements (API 36 for new submissions, API 35 for existing-app availability from 2026-08-31) are noted for awareness only and are not a reason to change the private sideload target in any task below.
- **Build/artifact boundary (review clarification):** the owner-request gate covers *release, signed, installed or distributed* artifacts. Selecting T-02 explicitly authorizes CI-only debug packaging on PRs (never uploaded, never distributed, no release keys). Selecting T-04 explicitly authorizes locally built, unsigned, never-distributed measurement builds. Release APK builds, signing, installation on any device the family uses, and Drive "Latest" replacement happen only when the owner explicitly asks, after the chosen work passes its gates.
- Each task ships as its own small reviewable PR. No task below is started until the owner selects it.
- Real handset, TalkBack, audio, and any test-device use are separately authorized decisions. Nothing here touches the production handset.

## Open decision (owner, blocks T-05)

**D-01 Short-number policy.** The dialpad currently dims Call/Save for input shorter than 3 digits (alpha only, `DialpadActivity.java:215-217`; neither button is disabled, so TalkBack announces both as enabled at every length) but the Call handler still accepts any non-blank input (`DirectCallAction.java:18`), so a typed "7" dispatches when permission is granted. Save is already gated in its handler (`DialpadActivity.java:49`), so only its accessibility state disagrees with its visuals. Decide one of:
- (a) short numbers are supported: make Call/Save look and announce enabled for supported input; or
- (b) some inputs are disallowed: define which, and make the visual state, accessibility enabled state, and handlers all agree.

The decision must also cover (reviewer-verified gaps):
- Non-digit input the keypad can produce: a leading `+` (long-press 0 on empty input, `DialpadActivity.java:180-183`), and the `*` / `#` keys (`ui/src/main/res/layout/activity_dialpad.xml`). Say whether these count toward length and whether a lone `+` is callable.
- Service/MMI strings such as `*#06#`: `#` may need encoding via `dialUri` (`DialIntents.java:31`); decide handling explicitly, not as a side effect.
- Whether Save's threshold changes together with Call's, keeping its verified duplicate lookup and failure-vs-no-match behavior (`DialpadActivity.java:48-67`).
- Emergency numbers are out of scope for a length rule: on most Android builds `ACTION_CALL` to an emergency number opens the system dialer instead of calling.

Empty input stays non-callable in all cases. Do not silently impose a universal 3-digit rule on all call sources; any rule lands in the dialpad only (see T-05).

## Tasks

### T-01 (P0) - Docs: canonical current-state table
- **Goal:** one entry-point table covering stable handset baseline, full16, noNotif20, and current source, so readers stop confusing versions.
- **Scope:** `README.md` (including the stale "Current source: merged PR #10, commit 46c966d…" line at `README.md:11`), `docs/build-and-test.md`, notification guidance in the family guide split into full-only vs noNotif, link to newest validation doc.
- **Non-goals:** rewriting or deleting dated historical audits; changing any runtime behavior.
- **Acceptance/tests:** entry docs name the same versions as `app/build.gradle`; noNotif sections carry no listener-setup instructions; links resolve; docs-only diff. Any test/lint count in an entry doc carries a date and commit or is replaced by a link to the dated validation doc, so it cannot silently drift again. The family guide's notification bullets (`docs/family-guide.ar.md:62-66`) get an explicit inline "full only" marker in Arabic, not only a separate heading. Keep the actual historical handset baseline labelled as a dated baseline, not relabelled as latest source.
- **Risk:** low. **Effort:** small (~half day). **Depends on:** none. Land before T-02 (both touch `docs/build-and-test.md`; T-02 only appends).

### T-02 (P0) - Build gates: CI + distribution checksum
- **Goal:** every PR runs both flavor unit suites, UI golden verification, app lint for both flavors, and merged-manifest checks; debug packaging runs only after those checks pass.
- **Scope:** GitHub Actions workflow(s) (`.github/` does not exist on main; this sets the runner baseline: JDK 11, SDK platform 33, Build Tools 30.0.3); add official `distributionSha256Sum` for the Gradle wrapper distribution after independent verification, plus `gradle/actions/wrapper-validation` pinned by SHA; pin the runner image and JDK distribution to match the golden-recording host; record intentional flavor-specific skips (appends to `docs/build-and-test.md`).
- **Non-goals:** release/signing jobs, secrets, Drive, dependency or toolchain version bumps (see T-08), golden updates, APK artifact upload from CI, suppressing lint.
- **Lint gate (review blocker, resolved):** gate on `:app:lintNoNotifDebug` + `:app:lintFullDebug` only. Standalone `:ui:lintDebug` has a known existing `WrongConstant` error reproduced on unchanged main (`docs/build-and-test.md:116`, `docs/notification-history-validation.md:26,30`), which is recorded as a **known non-gating red**; do not suppress it and do not claim UI lint is green. Fixing it is T-10.
- **Acceptance/tests:** clean run on a docs-only PR and on a code PR; gates fail on golden mismatch, app lint errors, checksum tampering (demonstrate on a throwaway branch with a wrong `distributionSha256Sum` and an uncached distribution so the negative control is not vacuous); merged-manifest output inspection (`processNoNotifDebugMainManifest` output) proves the listener service/binding is absent in the actual noNotif artifact inputs - the Robolectric-level `NoNotificationBuildTest` does not prove what goes into the APK. Run lint and Robolectric in separate/sequential jobs (a combined run hit exit 137 on a 2GB runner). If goldens fail on first CI run for host-parity reasons, pin the runner to the recording host; do not re-record goldens under this task.
- **Risk:** medium (build-maintenance). **Effort:** medium (~1-2 days, plus up to 1 day buffer for golden host parity). **Depends on:** none; T-01 lands first for doc hygiene. Soft prerequisite for T-03's regression matrix when ready.

### T-03 (P1) - Fix interrupted CALL_PHONE permission handling (GPT-01 / A02)
- **Goal:** an empty/interrupted permission result no longer persists `call_denied_once`, so the next tap can ask again instead of showing the blocked path.
- **Scope:** `DirectCallAction` permission-result handling, **plus** the two minimal callback-forwarding changes in `DialpadActivity.java:154-156` and `ConfirmCallActivity.java:182-184`, which currently discard the `permissions[]` names; pass them through so a matching request code with an unrelated permission can be distinguished.
- **Non-goals:** auto-calling from callbacks (never), changing the grant check, changing contacts-permission UX, UX redesign.
- **Acceptance/tests:** activity regressions on API 24/30/33 covering **both host activities**: empty arrays, mismatched arrays, matching-code/wrong-permission, unrelated request codes (already ignored at `DirectCallAction.java:37`; regression guard), first/repeated denial, rationale true/false, revoke/regrant, pending-state clearing. Interrupted result shows neutral or no feedback (never the blocked denial hint) and clears `pending` without calling. `call_denied_once` is cleared when a grant is observed (callback or `checkSelfPermission` at tap), closing the Settings grant-then-revoke edge. Explicit proof no callback path places a call and duplicate taps stay suppressed.
- **Risk:** low-to-medium (permission flow). **Effort:** small (~half-1 day, assuming the existing Android test environment). **Depends on:** none. First recommended code change. Lands before T-05 (shared dialpad path).

### T-04 (P2) - Measurement: low-end performance baseline
- **Goal:** measure before optimizing. Establish cold/warm interaction timing, frame time, peak/settled heap, and retained workers for Phone/Contacts/History/Dialpad on synthetic data.
- **Scope, split (review):** **T-04a** emulator-only, no device approval needed; **T-04b** optional non-production A10s, separately authorized. Synthetic workloads (50/500/2000 contacts; thumbnail/large/corrupt/rotated photos; repeated navigation; slow provider; long Arabic names; largest text scale) traced via Perfetto/Android Studio.
- **Build/fixture prerequisites (review):** pin the flavor (noNotif) and one build type (profileable/release-like, unsigned, never distributed) for all before/after comparisons; a debuggable build exaggerates main-thread cost and mixing build types invalidates comparisons. The existing `FixtureContactsProvider` is a test-source-only abstract provider and cannot feed the traced app; test-only fixture/harness provisioning (slow/failing provider, arbitrary corrupt/rotated streams) is a separately selected prerequisite PR - no silent production-code change and no device authorization is assumed.
- **Non-goals:** production handset, real contact data, carrier calls, publishing numbers as handset certification.
- **Acceptance/tests:** recorded workload, build/flavor/build-type, OS/API, repeat counts (at least five trials per scenario after a documented reset), and a written baseline in docs **with an explicit target-to-beat** so T-06/T-07 "improvement" is objectively checkable.
- **Risk:** low (app), test-device approval needed only for T-04b. **Effort:** ~1 day setup + measurement if fixture provisioning already exists; separate setup from measurement otherwise. **Depends on:** owner approval for T-04b only. Gates T-06/T-07 sizing.

### T-05 (P2) - Align short-input appearance, accessibility and handler (GPT-06)
- **Goal:** visual state, accessibility enabled state, and the Call/Save handlers agree for every input, per D-01.
- **Scope (review blocker, resolved):** the gate lands **in `DialpadActivity` before `callAction.tap(...)` (`DialpadActivity.java:140`); `DirectCallAction` is unchanged** because it is shared with `ConfirmCallActivity.java:123,127` - a rule inside it would silently apply to history and contact-tile calls, violating D-01. Accessibility state uses `setEnabled(false)` (or an equivalent a11y state/action), not alpha alone; verify the touch-guard `guard(...)` wrapper still behaves and does not swallow re-enable. Save's a11y/enabled state aligns with its existing handler gate unless D-01 changes Save's threshold.
- **Non-goals:** deciding the policy in code (D-01 is the owner's), changing history/confirmation call paths, `DirectCallAction`.
- **Acceptance/tests:** empty, one/two digits, accepted short inputs, ordinary numbers, `+`/`*`/`#` and lone `+` per D-01; restored short number after recreation shows the correct enabled state (`number` is restored before `sync()`, `DialpadActivity.java:126,142`); editing/backspacing across the boundary; touch-guard and accessibility activation; Save/Call consistency. If the disabled look changes from alpha to a real disabled style, golden updates are allowed **in this PR only, reviewed image by image**.
- **Risk:** low-to-medium (behavior). **Effort:** small (~half day). **Depends on:** D-01; T-03 landed first (shared dialpad path).

### T-06 (P3) - Bounded, cancellable contacts pipeline (GPT-03 / A03)
- **Goal:** contacts reads are owned, coalesced, cancellable and deadline-bounded; history renders number-only results without waiting indefinitely for optional contact labels.
- **Scope:** shared contacts boundary absorbing all four current consumers with their different threading models: `PhoneActivity.java:92` (raw thread), `DialpadActivity.java:39,51` (activity-owned `lookupWorker`), `HistorySnapshot.java:54-62` (signal checked only after the full load), `ContactsLoader.java:90-98` (query and loop without `CancellationSignal`). Follow the existing budget-plus-signal pattern in `CallLogRepository.java:37,74,119` as the design model.
- **Degraded history (review):** define behavior for both rendering and opening a row: valid history renders number-only when labels are late/unavailable; tapping a callable row reaches number-only confirmation with the exact raw number; retries never grow an unbounded queue; late labels cannot reopen a paused/replaced screen; coalescing must not cancel another live consumer (grid vs dialpad refresh/Save vs history). Preserve the pending-snapshot/draw/focus acknowledgement contract (`CallHistoryController.java:198-205,225-228,324-331`).
- **Non-goals:** changing failure-vs-empty semantics (a deadline expiry maps to **failure**, e.g. the Save path's `dial_lookup_needed`, never to "empty"), the exact-clicked-number rule, the 200-row paging contract, provider write access.
- **Acceptance/tests:** a **named deadline value** and where it is configured; latch-controlled overlapping refresh/pause/destroy; blocked provider with valid call log; repeated retries; large result sets; cursor close and permission loss; repeat T-04 measurements against the target-to-beat.
- **Risk:** medium (concurrency). **Effort:** ~3-5 days (review-revised from 2-4; four call sites with different lifecycle contracts). **Depends on:** T-04 baseline. Lands before T-07 (both touch `PhoneActivity`).

### T-07 (P3) - Async photo loading with invalidation (GPT-02 + GPT-04 / A01)
- **Goal:** photo I/O/decode leaves the main thread; caches invalidate correctly; no delayed image lands on the wrong contact.
- **Scope:** bounded cancellable loader with immediate placeholder, identity checks on completion against the view's **currently bound contact id + URI + size** (not adapter position - `ContactGridAdapter` reuses `convertView`), lifecycle/recycle cancellation, defined refresh invalidation, bounded/expiring **thread-safe** negative cache (`misses` is a plain unbounded `HashSet`, `ContactPhotoLoader.java:27`). Covers grid and confirmation; the confirmation screen's per-screen new loader decoding at 900px on the main thread (`ConfirmCallActivity.java:89`) is the single largest main-thread decode and is its own acceptance case: the screen opens before the photo decodes, and fast back/forward never shows a wrong-contact photo.
- **Goldens (review):** `UiSnapshotTest` builds the grid with a `ContactPhotoLoader`; async loading would capture placeholders. Acceptance requires either "goldens unchanged via a synchronous injectable test executor" or explicit owner permission for image-by-image reviewed golden updates in this PR.
- **Stale-completion tests (review):** an old same-URI load completing **after** refresh, revoke/regrant, or rebinding must neither display nor repopulate the new cache generation (URI equality alone cannot reject it); destruction/recreation mid-decode; two simultaneous views requesting different sizes; refresh/revoke while a decode is in flight, not only subsequent cache hits.
- **Non-goals:** adding an image library unless measurements justify it; changing fallback/tile design.
- **Acceptance/tests:** same-URI changed pixels refresh; failed-then-readable URI recovers; eviction; revoke/regrant; rapid scroll/recycle never cross-applies images; measured main-thread decode time drops against the T-04 target without memory regression.
- **Risk:** medium (concurrency/identity). **Effort:** ~2-4 days (upper end if goldens need a test seam). **Depends on:** T-04 baseline, T-06 landed. Combine GPT-02 and GPT-04 here rather than as two overlapping cache projects.

### T-08 (P3) - Toolchain security upgrade evaluation
- **Goal:** evaluate moving past the 2026-11-10 Gradle advisories (GHSA-3cfg-jpcr-gfp3, GHSA-gqrr-4j89-5267, GHSA-pcp8-98xm-c6pr) with compatibility validated, and give the owner a decision-ready report.
- **Stated outright (review):** the OSS fixes are Gradle **8.14.7 / 9.9.1 only**; 7.6.8 exists but requires the paid Gradle Security Subscription. So the smallest safe move from the pinned 7.6.4 is a **new major version (8.14.7)**, not a patch bump. AGP 7.4.2 is not documented as compatible with Gradle 8.14, and Paparazzi 1.2.0 is a 2023-era plugin; if either breaks, the real work becomes an AGP 8.x move, which brings JDK 17 (contradicting the JDK 11 baseline) and `buildFeatures { buildConfig true }` (the flavors depend on `buildConfigField`, and AGP 8 disables `BuildConfig` generation by default), plus probable Paparazzi bump and golden re-recording risk.
- **Explicit stop-and-ask (review):** if Gradle 8.14.7 requires AGP/JDK/Paparazzi changes, **stop and return to the owner with the compatibility findings**; the blocked/no-safe-move outcome is a compatibility report plus an owner decision on any expanded toolchain scope, not pressure to change JDK/AGP/Paparazzi or goldens to make the task pass. Interim mitigation stays isolated single-tenant ephemeral build hosts.
- **Scope:** compatibility research, then - only if a safe move exists - one separate reviewable PR with the smallest safe version move and a full T-02 gate run.
- **Non-goals:** blind jump to Gradle 9.x, bundling the bump into any other task, claiming the installed APK is affected (it is not; this is build tooling).
- **Acceptance/tests:** full T-02 gate green on the upgraded toolchain; goldens unchanged; wrapper and distribution checksums verified against official sources.
- **Risk:** medium. **Effort:** ~1 day research; 2-4 days implementation only if AGP must move (mostly golden parity and lint differences). **Depends on:** T-02.

### T-09 (acceptance, when owner chooses a release) - Device/accessibility/audio acceptance
- **Goal:** synthetic-data device pass before any chosen release: TalkBack/Switch Access/keyboard traversal, touch guard, Arabic TTS audibility, noNotif merged manifest on device, update continuity.
- **Scope:** separately authorized non-production device; checklist from audit section 8; stop before any real call handoff.
- **Update continuity (review):** runs **only after the owner's release request has authorized signing**, and only on the non-production device; debug-signed builds cannot establish release-signed update continuity, so that item stays pending until the owner supplies/authorizes the matching signed artifacts. Include the full16 → noNotif20 direction as an **expected-failure check confirming the accepted limitation**; uninstall, clearing data, version bumps or signer changes are prohibited as ways to make that case pass. For any authorized supported update, record source/target identity, version and signer, and verify preservation of PIN/preferences/local favorites.
- **Non-goals:** production handset, real contacts, store submission.
- **Acceptance/tests:** recorded device/OS/build/flavor/signer; checklist results in docs; release decision remains the owner's.
- **Risk:** device-dependent. **Effort:** medium (not schedulable until the authorized device, speech engine and signed artifacts exist). **Depends on:** the code tasks chosen for that release.

### T-10 (small, optional) - Fix the standalone UI lint `WrongConstant` error
- **Goal:** make `:ui:lintDebug` green honestly so it can join the T-02 gate later.
- **Scope:** the unchanged HomeLabel `Layout.BREAK_STRATEGY_SIMPLE` line only, in its own tiny PR, preserving API 24 behavior (see the prior fix in PR #14 for the pattern).
- **Non-goals:** suppressing the error, batching it into T-02 or any runtime task, touching other warnings.
- **Acceptance/tests:** `:ui:lintDebug` exits clean; UI goldens unchanged; API 24/25/33 render regressions pass. Only then may T-02 add UI lint to the gate.
- **Risk:** low. **Effort:** small (~half day). **Depends on:** none.

## Order

1. T-01 lands first (docs hygiene; T-02 only appends to `docs/build-and-test.md`).
2. T-02 and T-03 can proceed independently; T-02 is a soft prerequisite for T-03's API 24/30/33 regression matrix when ready.
3. T-03 before T-05 (shared dialpad path); D-01 decision unlocks T-05.
4. T-04a measurement precedes T-06/T-07 implementation; T-04b is optional.
5. T-06 before T-07 (both touch `PhoneActivity`).
6. T-08 after T-02.
7. T-10 independently, whenever UI lint should join the gate.
8. T-09 gates any release the owner requests.

## Review guidance

Reviewers: comment on feasibility, missing edge cases, ordering, effort/risk realism, and any conflict with the standing constraints. Do not implement. Open questions: D-01 short-number policy; whether T-04b may use a non-production A10s; Play target-API timing if distribution goals change.

## Changelog

- **2026-11-12 (v2):** incorporated Codex + Claude PR #19 review comments, each re-verified against main `79774e4`: clarified the build/artifact authorization boundary; fixed the T-06→T-08 cross-reference; resolved the T-02 lint gate (app lint gated, UI lint a known non-gating red with fix task T-10 added); expanded T-03 with callback forwarding, interrupted-feedback definition and grant-observed flag clearing; split T-04 into emulator/device parts with pinned build type and fixture prerequisites; expanded D-01 with `+`/`*`/`#`, MMI and Save alignment; confined T-05 to the dialpad with real a11y state; defined T-06 degraded-history behavior, named deadline requirement and 3-5 day estimate; added T-07 golden/stale-completion/thread-safety requirements; rewrote T-08 with the major-version jump and explicit stop-and-ask; tied T-09 update continuity to release-request signing with the expected-failure full16 check; ordering updated (T-01→T-02, T-03→T-05, T-06→T-07). Greptile's T-06→T-08 reference fix and CodeRabbit's minor comment are covered by the same edits. No reviewer comment conflicted with the standing constraints.
