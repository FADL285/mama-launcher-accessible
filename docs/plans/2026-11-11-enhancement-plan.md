# Mama Launcher enhancement plan (post-audit)

Date: 2026-11-11 (Africa/Cairo). Status: proposed, awaiting owner decisions and review comments. This document records tasks only; it implements nothing and authorizes no release.

Sources: independent audit `docs/audits/2026-11-11-codex-gpt-audit.md` (PR #18, merged), prior `docs/audit-014.md`, and an independent re-verification of all seven audit findings against the source at `564f455` (production-equivalent to current main; PR #16 changed only test PNGs).

## Standing constraints (apply to every task)

- `noNotif` (versionCode 20, `0.15-no-notif-footer-candidate`) stays listener-free. Do not re-add the notification listener to it.
- `full` (versionCode 16, `0.14-pilot-candidate`) cannot update an installed noNotif20 build. That is an accepted limitation, not a defect; do not bump full16's version as a "fix".
- Call permission callbacks never place calls. Every call requires a fresh explicit Call press with a current grant check.
- Call-log access stays read-only. No writes to the system call log, no notification cancellation.
- No rewrites, no framework/language migration, no targetSdk bump, no signing or distribution changes, no Drive uploads. Play target-API requirements (API 36 for new submissions, API 35 for existing-app availability from 2026-08-31) are noted for awareness only and are not a reason to change the private sideload target in any task below.
- Each task ships as its own small reviewable PR. No task below is started until the owner selects it.
- Release, APK build, signing and Drive "Latest" replacement happen only when the owner explicitly asks, after the chosen work passes its gates.
- Real handset, TalkBack, audio, and any test-device use are separately authorized decisions. Nothing here touches the production handset.

## Open decision (owner, blocks T-05)

**D-01 Short-number policy.** The dialpad currently dims Call/Save for input shorter than 3 digits (alpha only) but the Call handler still accepts any non-blank input, so a typed "7" dispatches when permission is granted. Decide one of:
- (a) short numbers are supported: make Call/Save look enabled for supported input lengths; or
- (b) some inputs are disallowed: define which, and make the disabled visual state, accessibility enabled state, and handler all agree.

Empty input stays non-callable in all cases. Do not silently impose a universal 3-digit rule on all call sources. Effort to decide: n/a (product decision). Everything in T-05 depends on this answer.

## Tasks

### T-01 (P0) - Docs: canonical current-state table
- **Goal:** one entry-point table covering stable handset baseline, full16, noNotif20, and current source, so readers stop confusing versions.
- **Scope:** `README.md`, `docs/build-and-test.md`, notification guidance in the family guide split into full-only vs noNotif, link to newest validation doc.
- **Non-goals:** rewriting or deleting dated historical audits; changing any runtime behavior.
- **Acceptance/tests:** entry docs name the same versions as `app/build.gradle`; noNotif sections carry no listener-setup instructions; links resolve; docs-only diff.
- **Risk:** low. **Effort:** small (~half day). **Depends on:** none.

### T-02 (P0) - Build gates: CI + distribution checksum
- **Goal:** every PR runs both flavor unit suites, UI golden verification, app/UI lint, both debug assemblies, and merged-manifest checks (noNotif has no listener service/binding).
- **Scope:** GitHub Actions workflow(s); add official `distributionSha256Sum` for the Gradle wrapper distribution after independent verification; review/pin actions by SHA; record intentional flavor-specific skips.
- **Non-goals:** release/signing jobs, secrets, Drive, dependency or toolchain version bumps (see T-06), golden updates.
- **Acceptance/tests:** clean run on a docs-only PR and on a code PR; gates fail on golden mismatch, lint errors, checksum tampering; noNotif merged-manifest inspection passes in CI.
- **Risk:** medium (build-maintenance). **Effort:** medium (~1-2 days). **Depends on:** none; can run parallel with T-03.

### T-03 (P1) - Fix interrupted CALL_PHONE permission handling (GPT-01 / A02)
- **Goal:** an empty/interrupted permission result no longer persists `call_denied_once`, so the next tap can ask again instead of showing the blocked path.
- **Scope:** `DirectCallAction` permission-result handling only; distinguish completed denial from interrupted/empty or unrelated results.
- **Non-goals:** auto-calling from callbacks (never), changing the grant check, changing contacts-permission UX, UX redesign.
- **Acceptance/tests:** activity regressions on API 24/30/33 for empty arrays, unrelated request codes, first/repeated denial, rationale true/false, revoke/regrant, pending-state clearing; explicit proof that no callback path places a call and duplicate taps stay suppressed.
- **Risk:** low-to-medium (permission flow). **Effort:** small (~half-1 day). **Depends on:** none. First recommended code change.

### T-04 (P2) - Measurement: low-end performance baseline
- **Goal:** measure before optimizing. Establish cold/warm interaction timing, frame time, peak/settled heap, and retained workers for Phone/Contacts/History/Dialpad on synthetic data.
- **Scope:** synthetic workloads (50/500/2000 contacts; thumbnail/large/corrupt/rotated photos; repeated navigation; slow provider; long Arabic names; largest text scale) on an emulator and, only if separately authorized, a non-production A10s. Traces via Perfetto/Android Studio.
- **Non-goals:** production handset, real contact data, carrier calls, any code change, publishing numbers as handset certification.
- **Acceptance/tests:** recorded workload, build/flavor, OS/API, and repeated trials; written baseline report in docs.
- **Risk:** low (app), test-device approval needed. **Effort:** ~1 day setup + measurement. **Depends on:** owner approval for any device use. Gates T-06/T-07 sizing.

### T-05 (P2) - Align short-input appearance, accessibility and handler (GPT-06)
- **Goal:** visual state, accessibility enabled state, and the Call handler agree for every input length.
- **Scope:** `DialpadActivity.sync` and the Call handler per decision D-01; Save consistency.
- **Non-goals:** deciding the policy in code (D-01 is the owner's), changing history/confirmation call paths.
- **Acceptance/tests:** empty, one/two digits, accepted short inputs, ordinary numbers; touch-guard and accessibility activation; Save/Call consistency.
- **Risk:** low-to-medium (behavior). **Effort:** small (~half day). **Depends on:** D-01.

### T-06 (P3) - Bounded, cancellable contacts pipeline (GPT-03 / A03)
- **Goal:** contacts reads are owned, coalesced, cancellable and deadline-bounded; history renders number-only results without waiting indefinitely for optional contact labels.
- **Scope:** shared contacts boundary with a bounded executor, provider `CancellationSignal`, in-loop cancel/deadline checks, generation/lifecycle guards preserved; history optional-identity degradation.
- **Non-goals:** changing failure-vs-empty semantics, the exact-clicked-number rule, the 200-row paging contract, or provider write access.
- **Acceptance/tests:** latch-controlled overlapping refresh/pause/destroy; blocked provider with valid call log; repeated retries; large result sets; cursor close and permission loss; repeat T-04 measurements show improvement without regressions.
- **Risk:** medium (concurrency). **Effort:** ~2-4 days. **Depends on:** T-04 baseline; T-02 CI helpful.

### T-07 (P3) - Async photo loading with invalidation (GPT-02 + GPT-04 / A01)
- **Goal:** photo I/O/decode leaves the main thread; caches invalidate correctly; no delayed image lands on the wrong contact.
- **Scope:** bounded cancellable loader with immediate placeholder, URI/contact/size identity checks on completion, lifecycle/recycle cancellation, defined refresh invalidation, bounded/expiring negative cache. Covers grid and confirmation.
- **Non-goals:** adding an image library unless measurements justify it; changing fallback/tile design; wrong-person risk must go to zero, not "unlikely".
- **Acceptance/tests:** same-URI changed pixels refresh; failed-then-readable URI recovers; eviction; revoke/regrant; rapid scroll/recycle never cross-applies images; measured main-thread decode time drops without memory regression (T-04 workloads).
- **Risk:** medium (concurrency/identity). **Effort:** ~2-4 days. **Depends on:** T-04 baseline. Combine GPT-02 and GPT-04 here rather than as two overlapping cache projects.

### T-08 (P3) - Toolchain security upgrade evaluation
- **Goal:** plan and test a Gradle upgrade past the 2026-11-10 advisories (GHSA-3cfg-jpcr-gfp3, GHSA-gqrr-4j89-5267, GHSA-pcp8-98xm-c6pr) with AGP 7.4.2 / Paparazzi 1.2.0 / JDK 11 compatibility validated.
- **Scope:** compatibility research, a separate reviewable PR with the smallest safe version move, CI run; meanwhile prefer isolated single-tenant ephemeral build hosts.
- **Non-goals:** blind jump to Gradle 9.x, bundling the bump into any other task, claiming the installed APK is affected (it is not; this is build tooling).
- **Acceptance/tests:** full T-02 gate green on the upgraded toolchain; goldens unchanged; wrapper and distribution checksums verified against official sources.
- **Risk:** medium. **Effort:** ~1-2 days research + PR. **Depends on:** T-02.

### T-09 (acceptance, when owner chooses a release) - Device/accessibility/audio acceptance
- **Goal:** synthetic-data device pass before any chosen release: TalkBack/Switch Access/keyboard traversal, touch guard, Arabic TTS audibility, noNotif merged manifest on device, update continuity.
- **Scope:** separately authorized non-production device; checklist from audit section 8.
- **Non-goals:** production handset, real contacts, store submission.
- **Acceptance/tests:** recorded device/OS/build/flavor; checklist results in docs; release decision remains the owner's.
- **Risk:** device-dependent. **Effort:** medium. **Depends on:** the code tasks chosen for that release.

## Order

1. T-01, T-02, T-03 can start independently (T-03 is the first recommended code fix).
2. D-01 decision unlocks T-05.
3. T-04 measurement precedes T-06/T-07 implementation.
4. T-08 follows T-02.
5. T-09 gates any release the owner requests.

## Review guidance

Reviewers: comment on feasibility, missing edge cases, ordering, effort/risk realism, and any conflict with the standing constraints. Do not implement. Open questions: D-01 short-number policy; whether T-04 may use a non-production A10s; Play target-API timing if distribution goals change.
