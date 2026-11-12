# Independent audit: Mama Launcher, Claude, 2026-11-12

Report-only. This document changes no runtime code, test, resource, build file or other document. Every recommendation below is a proposal for a separate, owner-approved pull request.

## 1. Executive summary and bounded verdict

I audited the fetched `main` at `79774e4f81a21aa317d334243109bafa9cffd051`. That is the merge of PR #18 (the Codex GPT audit report). The production source is identical to the owner-confirmed PR #17 baseline `564f455`: everything merged since then is test PNG recompression (PR #16) and documentation (PR #18). Section 2 has the diff.

**Verdict.** I found **no Critical or High defect and no confirmed release blocker** for the delivered `noNotif` candidate. The core safety contract held under every probe I ran:
- A call starts only from an explicit green-button press with a current grant check.
- No permission callback, speech, Listen or Favorites path places a call.
- Multi-number contacts call nothing until a number is visibly chosen.
- Call-log access is read-only.
- The `noNotif` merged manifest has no notification-listener service.

That is a bounded statement about the code and the checks listed in section 11. It is not a certification of TalkBack behaviour, audible Arabic speech, Samsung A10s performance, or the absence of all edge cases.

**Confirmed actionable findings: 12 = 0 Critical, 0 High, 3 Medium, 9 Low.**
- Four reproduce known debt: CL-01, CL-02, CL-03 and CL-07 match earlier `docs/audit-014.md` items, which this report re-verifies independently against current source.
- Eight were not found in the documents I read before writing (`docs/audit-014.md`, `docs/audit-013.md`, README, build/test docs, validation docs). Section 14 records how I checked overlap with the 2026-11-11 Codex report afterwards.

Most important:
1. **CL-01 (Medium, reproduced).** An interrupted `CALL_PHONE` request (empty result arrays) is stored as a completed denial. The next Call tap then shows the "blocked" help instead of asking again. The Contacts permission path in the same app already handles this case correctly (`PhoneActivity.java:162-167`), so the fix pattern exists in-repo.
2. **CL-04 (Low, reproduced render + contrast arithmetic).** The high-contrast theme leaves the **PIN gate** title, subtitle, error and Back label, and the Settings menu hint, in fixed light-theme colours on the black window. The title measures 1.21:1.
3. **CL-02 / CL-03 (Medium, source-verified).** Contact photos are decoded on the main thread, the confirmation screen decodes up to 900 px on every open, and contacts are re-read in full on every Phone resume. Device measurement is still missing (section 10).
4. **CL-05 / CL-06 (Low, reproduced).** A fast double tap on a contact tile opens two stacked confirmation screens. Returning from the confirmation screen rebuilds the grid adapter and loses the scroll position.

## 2. Audit identity, method and scope

| Item | Value |
| --- | --- |
| Reviewer | Claude (Anthropic), Claude Code cloud session |
| Audit date | 2026-11-12 (UTC) |
| Audited commit | `79774e4f81a21aa317d334243109bafa9cffd051` (main, committed 2026-11-11T23:23:45+02:00) |
| Owner baseline | PR #17 merge `564f4556841032ba95f29c6fb3fca0e1fd5fce71` |
| Branch | `audit/claude-2026-11-12`, created from the audited commit in a separate worktree |
| Environment | Linux container, OpenJDK 11.0.33 (Ubuntu package), Gradle wrapper 7.6.4, Android SDK platform 33 + build-tools 30.0.3 (fresh `sdkmanager` install), Robolectric 4.11.1 runtimes for API 24/25/30/33 (SHA-512 verified against Maven Central). No emulator, no handset. |
| Data | Synthetic names/numbers only. No real contacts, call log, notification or device data was read. |

**Diff from the baseline.** `git diff --stat 564f455 79774e4` touches only `docs/audits/2026-11-11-codex-gpt-audit.md` and 16 PNGs under `ui/src/test/snapshots/images/` (ImgBot recompression, PR #16). There are no Java, XML resource, manifest or Gradle changes, so production behaviour is identical to PR #17. I did not touch PR #16.

**Method.**
1. I read all 37 production Java files in `app/` and the 18 in `ui/`, the manifests and flavour overlay, layouts/themes/colours/drawables, the Gradle files, and the current docs listed in the brief.
2. I ran the repository's own checks (section 11).
3. I wrote two **untracked** probe test files to reproduce runtime claims, ran them, recorded the output, and deleted them. They are not in this PR.
4. I inspected committed Paparazzi renders as images.

**Order of reading, disclosed.** The brief asked me not to read the other model's new audit before finishing mine. Before this audit started I had already reviewed PR #19 (an enhancement plan built on the 2026-11-11 Codex report). During that review I read three excerpts of the Codex report: its Gradle advisory table (lines 294-296), its roadmap and its accepted boundaries (lines 438-466). I did not read its findings section before completing this report. Every claim here was re-verified against current source or a primary source; nothing is carried over on trust. Section 14 lists the overlap I checked after writing.

**Exclusions.** No handset or emulator runtime, TalkBack/Switch Access session, audio verification, Perfetto/heap profiling, release build, signing, Play/Play Protect interaction or Drive. No changes to other PRs.

## 3. Product, flavours, permissions and critical invariants

| | `full` | `noNotif` |
| --- | --- | --- |
| applicationId | `info.fadl.mamalauncher` | same |
| versionCode / name | 16 / `0.14-pilot-candidate` (`app/build.gradle:10-11`) | 20 / `0.15-no-notif-footer-candidate` (`app/build.gradle:21-22`) |
| `BuildConfig.NOTIFICATION_ACCESS` | true | false |
| Listener service | declared (`AndroidManifest.xml:37-41`) | removed with `tools:node="remove"` (`app/src/noNotif/AndroidManifest.xml:6`); see section 11 for the merged-manifest check |
| Permissions | `READ_CONTACTS`, `CALL_PHONE`, `READ_CALL_LOG`; no INTERNET | same |
| SDK | min 24, compile 33, target 33 | same |

Update continuity: both flavours share one applicationId. Android refuses a lower versionCode as an update, so `full` 16 cannot install over an installed `noNotif` 20. That is the documented, accepted boundary, not a defect. Any future `full` release that should update an existing `noNotif` installation needs versionCode > 20 and the same signing key. No build type defines signing (`app/build.gradle` has no `signingConfigs`/`buildTypes`), so release signing stays outside the repository.

Invariants verified against source (section 12 lists them as safeguards):
- I1: only `DirectCallAction.tap` creates `ACTION_CALL` (`DirectCallAction.java:21`; `DialIntents.call` has no other caller). It is only reached from a guarded click.
- I2: `permissionResult` never calls (`DirectCallAction.java:36-43`).
- I3: multi-number contacts preselect nothing (`UiBinder.java:39-41`; `ConfirmCallActivity.java:115`).
- I4: call-log access is query-only (`CallLogRepository`, `MissedCallState`), and the listener never cancels notifications or reads extras (`LauncherNotificationListener.java:12`).
- I5: the `noNotif` runtime rejects stale grants: `NotificationAccess.allowed` returns false when `!BuildConfig.NOTIFICATION_ACCESS` (`NotificationAccess.java:12`).

## 4. Findings table

| ID | Severity | Confidence / status | Title | New or known |
| --- | --- | --- | --- | --- |
| CL-01 | Medium | High / reproduced (Robolectric probe) | Interrupted CALL_PHONE request is persisted as a completed denial | Known (audit-014 A02) |
| CL-02 | Medium | High / source-verified risk | Photo I/O and decode on the main thread; 900 px confirmation decode; stale, unbounded negative cache | Known (audit-014 A01) |
| CL-03 | Medium | High / source-verified risk | Contacts read uncancellable, unbounded, repeated on every Phone resume, and blocking a history-row tap | Known (audit-014 A03) |
| CL-04 | Low | High / reproduced (render probe + contrast arithmetic) | High-contrast theme: PIN gate and Settings hint keep dark light-theme colours on black | Not found in earlier docs |
| CL-05 | Low | High / reproduced (Robolectric probe) | Double tap on a contact tile opens two confirmation screens | Not found in earlier docs |
| CL-06 | Low | High / reproduced (Robolectric probe) | Returning to Phone resets the contacts grid to the top | Not found in earlier docs |
| CL-07 | Low | High / reproduced (Robolectric probe) | Dimmed short / `+`-only dialpad input still dispatches a call | Known (owner decision D-01 pending) |
| CL-08 | Low | High / reproduced (pure probe) | Un-starring one number of a system-starred multi-number contact does not unpin it | Not found in earlier docs |
| CL-09 | Low | High / source-verified, arithmetic probe | PIN lockout deadline uses the wall clock with no upper bound | Not found in earlier docs |
| CL-10 | Low | High / source-verified gap | No Android CI, no wrapper distribution checksum, no dependency verification; Gradle 7.6.4 in advisory ranges | Known (audit-014 A05); advisories checked fresh |
| CL-11 | Low | High / source-verified | Entry-point documentation drift | Partly known |
| CL-12 | Low | High / source-verified | `CallDecision` is test-only dead code that documents a contract production does not use | Not found in earlier docs |

Subjective recommendations and accepted boundaries are kept out of this count (sections 7, 14).

## 5. Detailed findings

Links are pinned to `79774e4`. Base: `https://github.com/FADL285/mama-launcher-accessible/blob/79774e4f81a21aa317d334243109bafa9cffd051/`.

### CL-01: Interrupted CALL_PHONE request is persisted as a completed denial

- **Severity / status:** Medium, reproduced. Affects both flavours, all APIs, the Dialpad and confirmation screens.
- **Evidence:** [`DirectCallAction.java:36-41`](https://github.com/FADL285/mama-launcher-accessible/blob/79774e4f81a21aa317d334243109bafa9cffd051/app/src/main/java/info/fadl/mamalauncher/DirectCallAction.java#L36-L41). `granted=results.length>0 && ...`, so an empty array counts as not granted. That writes `call_denied_once=true` and calls `feedback.show(true,false)`, which displays the blocked banner. On the next tap, `tap()` takes the blocked path whenever `call_denied_once` is true and `shouldShowRequestPermissionRationale` is false (`:30-32`). Rationale is false on a fresh install that has never seen a completed denial.
- **Platform contract:** the `onRequestPermissionsResult` reference says the interaction can be interrupted and that empty arrays "should be treated as a cancellation". [S1]
- **Reproduction (probe P1, Robolectric API 33, synthetic contact):** open confirmation without the grant and tap Call, which fires the permission request intent. Then deliver `onRequestPermissionsResult(42, new String[0], new int[0])`. Output: `call_denied_once after empty result = true; blocked banner visible = true`, then `second tap started = nothing (blocked path)`. The interrupted dialog alone turned the next tap into the "blocked" help without asking again.
- **Contrary evidence / existing protection:** the same app handles this correctly for Contacts (`PhoneActivity.java:162-167` ignores empty arrays). Unrelated request codes are already ignored (`DirectCallAction.java:37`). No callback path places a call. `LauncherFlowTest`/`HandsetUxTest` cover grant and deny, but no test passes empty arrays to `DirectCallAction`.
- **Impact:** after one interrupted dialog (rotation is not possible because the screens are portrait-locked, but a home press, incoming call or process death mid-dialog can do it), the Call button explains that calling is blocked and never asks again. Recovery needs the family to grant from system Settings.
- **Fix direction (small, low-to-medium risk):** treat `results.length==0` (or `permissions` missing `CALL_PHONE`) as cancellation: clear `pending`, persist nothing, and show neutral or no feedback. Also clear `call_denied_once` when a grant is observed. Regression tests for both host activities: empty arrays, mismatched permissions, first and repeated denial, rationale true/false, grant clears the flag, and no `ACTION_CALL` from any callback.

### CL-02: Photo I/O and decode on the main thread; stale, unbounded negative cache

- **Severity / status:** Medium, source-verified risk (not measured). Both flavours; Phone grid and confirmation.
- **Evidence:**
  - `ContactGridAdapter.getView` calls `photos.load(c.photoUri, 360)` synchronously ([`ContactGridAdapter.java:56`](https://github.com/FADL285/mama-launcher-accessible/blob/79774e4f81a21aa317d334243109bafa9cffd051/ui/src/main/java/info/fadl/mamalauncher/ui/ContactGridAdapter.java#L56)). `ContactPhotoLoader.load` opens the provider stream twice and decodes on the calling (UI) thread (`ContactPhotoLoader.java:39-67`).
  - The confirmation screen constructs a **new** loader, so there is no cache, and decodes at `maxDim` 900 inside `onCreate` (`ConfirmCallActivity.java:89`) before the screen first draws.
  - The `misses` set is an unbounded `HashSet` that is never cleared during the Phone activity's lifetime (`ContactPhotoLoader.java:27,35,72`). A photo that failed once, for example while the provider was busy, stays a name tile until Phone is recreated. The positive `LruCache` is keyed by URI only (`:33`), so an edited photo behind the same URI keeps showing the cached bitmap.
- **Mitigations present:** two-pass bounds decode with power-of-two subsampling (`:40-50`) and a 6 MB byte-bounded LRU (`:18-26`) limit peak memory. Failures fall back to the coloured name tile.
- **Impact (hypothesis until measured):** first-open jank on the confirmation screen and scroll hitching on a low-end device with large photos. The cache staleness is a correctness issue: an updated photo isn't shown.
- **Fix direction (medium effort, medium risk):** a bounded background loader with immediate placeholder, completion checks on the view's current contact id and URI, cancellation on recycle and pause, a thread-safe bounded or expiring negative cache, and invalidation on Phone refresh. Measure first (section 10). Committed Paparazzi snapshots construct the loader (`UiSnapshotTest`), so the change needs a synchronous test executor to keep goldens stable.

### CL-03: Contacts read uncancellable, unbounded, repeated per resume, and blocking a history-row tap

- **Severity / status:** Medium, source-verified risk.
- **Evidence:**
  - `PhoneActivity.refresh()` starts a raw `new Thread` that reads **every** phone row on each `onResume` (`PhoneActivity.java:69,92-120`). Returning from the confirmation screen therefore re-queries the provider, re-sorts and replaces the adapter (see CL-06).
  - `ContactsLoader.load` queries without a `CancellationSignal` and loops without cancel or deadline checks (`ContactsLoader.java:90-98`).
  - `HistorySnapshot.contacts` receives a signal but checks it only after the full read (`HistorySnapshot.java:62`).
  - Tapping a history row runs the full contacts read again before opening confirmation (`CallHistoryController.java:416-434`). If that read exceeds the 6 s worker timeout (`CallLogWorker.java:37`), the row tap ends in "history failed" even though the number itself was valid and callable.
- **Contrast:** the call-log path is bounded: 5 s budget and signal checks inside the loop (`CallLogRepository.java:119-123`), plus a single worker per owner.
- **Generation guards present:** stale results are dropped (`PhoneActivity.java:105-107`; `DialpadActivity.java:40,45`), and failure is distinguished from empty (`ContactsLoader.java:91`).
- **Fix direction (medium effort and risk):** one owned, bounded contacts worker with a provider `CancellationSignal`, in-loop deadline checks, and coalescing of resume refreshes (or a `ContentObserver` dirty flag instead of an unconditional reload). A history tap should fall back to a number-only confirmation if contact resolution is slow; the number is already exact.

### CL-04: High-contrast theme leaves the PIN gate and Settings hint unreadable

- **Severity / status:** Low (family-setter screens, not everyday mode, but the PIN gate is reachable from mama's Home by long press). Reproduced by render probe and contrast arithmetic. Both flavours.
- **Evidence:** `BaseActivity` applies `MamaTheme.Contrast` to every activity, including `PinGateActivity` and `SettingsActivity` (`BaseActivity.java:30`). That theme's window background is `#000000` (`themes.xml:25`). These views use fixed light-theme colours with no card behind them:

  | View | Colour | Contrast on `#000000` |
  | --- | --- | --- |
  | `activity_pingate.xml:20-22` `pin_title` | `@color/text_main` `#1A1A1A` | **1.21:1** |
  | `activity_pingate.xml:23-25` `pin_subtitle` ("enter / set / confirm PIN") | `#5A5348` | 2.77:1 |
  | `activity_pingate.xml:36-39` `pin_error` ("wrong / locked / mismatch") | `@color/danger_text` `#8A1F1F` | 2.30:1 |
  | `activity_pingate.xml:13-14` Back label | `@color/back_blue` `#2F5D8A` | 3.05:1 |
  | `activity_settings.xml:7` settings menu hint | `@color/text_main` | 1.21:1 |

  WCAG 2.x AA requires 4.5:1 for this text size. The same strings measure 6.5-16.4:1 on the normal `#FAF8F4` background, so they are fine in the standard theme. Ratios are computed with the WCAG relative-luminance formula.
- **Probe:** an untracked Paparazzi test inflated `activity_pingate` and `activity_settings` under `MamaTheme.Contrast` (Nexus 5 config). It printed `pin_title text=#ff1a1a1a`, `pin_subtitle text=#ff5a5348`. In the rendered images, the PIN gate title and subtitle are barely distinguishable from the black background, the settings gear and backspace icons are dark-on-black, and the Settings menu hint ("اختار قسم. التغييرات بتتحفظ تلقائيًا.") is unreadable. The section cards remain readable because they keep a white card background. Renders are layoutlib output, not a handset photo.
- **Why tests miss it:** there are contrast renders for other screens (`ContrastRegressionTest`, `DeveloperFooterRenderTest`), but the PIN gate goldens render only `MamaTheme`, and the Settings contrast footer render is scrolled to the bottom, below the hint.
- **Fix direction (small, low risk):** use `?attr/surfaceText` / `?attr/surfaceSecondaryText` / `?attr/surfaceAction` (already defined for both themes, `themes.xml:14-16,26-28`) and a contrast-safe error colour. Add PIN gate and Settings-top renders in `MamaTheme.Contrast`.

### CL-05: Double tap on a contact tile opens two confirmation screens

- **Severity / status:** Low, reproduced. Phone grid; the same pattern applies to the dialpad match rows (`DialpadActivity.java:101,111`) by inspection.
- **Evidence:** the tile action is `startActivity(ConfirmCallActivity.intentFor(...))` inside `guard()` (`PhoneActivity.java:111-114`). `guard()` has no repeat suppression (`BaseActivity.java:109-139`), the touch guard defaults to off (`Prefs.java:30`), and `ConfirmCallActivity` uses the standard launch mode (`AndroidManifest.xml:61-64`).
- **Probe P3:** with a 3-contact synthetic provider, `PhoneActivity` loaded the grid. Two `performClick()` calls on the first tile's view (as returned by the activity's adapter, which wires `guard(...)`) produced `confirmation activities started by two taps = 2`.
- **Impact:** for a user with a tremor, a double tap stacks two identical confirmation screens. Back then returns to a duplicate, which is confusing for a low-literacy user. This is not a safety issue: no call starts without the green button.
- **Fix direction (small):** ignore repeat navigation taps while a launch is pending (cleared on `onResume`), or launch with `FLAG_ACTIVITY_SINGLE_TOP` / `REORDER_TO_FRONT` semantics. Test: two clicks produce one started intent.

### CL-06: Returning to Phone resets the contacts grid position

- **Severity / status:** Low, reproduced. Phone grid with more contacts than one screen.
- **Evidence:** every `onResume` calls `refresh()`, which calls `grid.setAdapter(new ContactGridAdapter(...))` on completion (`PhoneActivity.java:69,110`). `AbsListView.setAdapter` resets the list to its first position. No code saves or restores `getFirstVisiblePosition()` across the refresh.
- **Probe P4:** with a 120-contact synthetic provider, the grid was laid out at 720×1000, `setSelection(80)` gave first visible position 80, and the activity went `pause → stop → restart → start → resume` (the Back-from-confirmation lifecycle). Output: `adapterReplaced=true firstVisible before=80 after=0`.
- **Impact:** someone who scrolls to a contact, opens confirmation and presses Back lands at the top of the list again. Each look-up becomes a re-scroll.
- **Fix direction (small):** keep the adapter and update its data when the sorted list is unchanged, or save and restore the first visible position around `setAdapter`. Test with a 120-contact fixture.

### CL-07: Dimmed short / `+`-only dialpad input still dispatches a call

- **Severity / status:** Low, reproduced. The policy is the owner's open decision D-01 (PR #19); this finding does not decide it.
- **Evidence:** `sync()` only changes alpha below 3 characters (`DialpadActivity.java:215-217`). Both buttons stay enabled, so accessibility services announce them as actionable. The Call handler only rejects null or blank (`DirectCallAction.java:18`). Long-press on 0 with empty input appends `+` (`DialpadActivity.java:182`). Save already returns early below 3 characters (`DialpadActivity.java:49`).
- **Probe P2 (CALL_PHONE granted, synthetic input):** tap `7` then Call gave `call.alpha=0.45 call.enabled=true` and `started=android.intent.action.CALL tel:7`. Long-press `0` on empty input then Call gave display `+` and `started=android.intent.action.CALL tel:%2B`.
- **Platform note:** `ACTION_CALL` cannot be used for emergency numbers; Android hands those to the system dialer (Intent reference [S2]). A length rule therefore does not decide emergency behaviour.
- **Fix direction:** after D-01, gate in `DialpadActivity` before `callAction.tap(...)`, not inside the shared `DirectCallAction`, which also serves history and contact confirmation. Make visual, `setEnabled`/accessibility state and handler agree. Decide explicitly whether a lone `+` is callable.

### CL-08: Un-starring one number of a system-starred multi-number contact does not unpin it

- **Severity / status:** Low, reproduced (pure probe).
- **Evidence:** the local override is per number, with the system star as default (`LocalFavorites.java:7-8`). The contact is pinned if **any** number is a favourite (`LocalFavorites.java:10-12`). For a system-starred contact with numbers A and B, un-starring A on the confirmation screen stores `false` for A only. B still defaults to the system star, so the tile stays pinned first.
- **Probe P5:** system-starred contact with numbers A and B; `LocalFavorites.set(A,false)`. Output: `contact still pinned = true`.
- **Existing tests:** `LocalFavoritesTest` covers single-number un-starring of a system favourite and the multi-number "no target preselected" rule, but not this combination.
- **Impact:** the star toggles visibly, but the contact never leaves the top group. The family setter may think the feature is broken.
- **Fix direction (small, behaviour decision):** either un-starring a number of a system-starred contact records an explicit contact-level "not pinned" override, or the confirmation screen explains that pinning is per contact. Needs owner choice plus a regression test.

### CL-09: PIN lockout uses the wall clock with no upper bound on the remaining time

- **Severity / status:** Low, source-verified (arithmetic probe). Both flavours.
- **Evidence:** the deadline is `System.currentTimeMillis() + lock` (`PinGateActivity.java:203`) and is checked with `until > now` (`PinGateActivity.java:212-213`, `PinLock.java:87-89`). Nothing clamps `until - now` to `MAX_LOCK_MS`.
  - If the device clock moves backwards (manual change, a bad network time, or a reset to factory time after the battery runs flat), an active lockout lasts as long as the backwards jump.
  - The device-credential button bypasses the lockout only when the phone has a secure lock (`PinGateActivity.java:94-98`). Without one there is no recovery except waiting or clearing app data.
  - A forward clock change ends a lockout early. That doesn't matter for a convenience PIN.
- **Probe P6:** lockout armed at time *t* (5 failures, 30 s), then "now" moved back one day. Output: `locked = true, remaining ms = 86430000 (cap 600000)`. The 10-minute cap does not bound the remaining time.
- **Fix direction (tiny):** treat `until - now > MAX_LOCK_MS` as expired or clamped, or store `elapsedRealtime` plus boot count. Test: a deadline far in the future relative to "now" unlocks after at most `MAX_LOCK_MS`.

### CL-10: Build assurance gaps and Gradle 7.6.4 advisories

- **Severity / status:** Low, source-verified gap. Build-host risk only; no app-runtime exposure.
- **Evidence:**
  - There is no `.github/` directory, so GitHub runs no Android build or test CI. The checks on past PRs were review bots.
  - `gradle-wrapper.properties` has no `distributionSha256Sum`.
  - There is no `gradle/verification-metadata.xml` and no dependency lock files.
- **Verified:** `gradle/wrapper/gradle-wrapper.jar` SHA-256 `14dfa961…be21a3` matches the official 7.6.2-7.6.6 wrapper checksum in Gradle's published list [S6].
- **Advisories, read directly on 2026-11-12 [S3-S5]:**
  - GHSA-3cfg-jpcr-gfp3 (Critical, version catalog values run as code): 7.0-8.14.6 affected. No version catalog exists in this repository, so the trigger is not present.
  - GHSA-gqrr-4j89-5267 (High, daemon registry trust) and GHSA-pcp8-98xm-c6pr (High, stale-daemon port squatting): ≤8.14.6 affected. These depend on build-host conditions.
  - Open-source fixes are 8.14.7 / 9.9.1. A 7.6.8 fix is listed only under the commercial Gradle Security Subscription.
  - None of this affects the installed APK.
- **Fix direction:** separate PRs. (a) A CI workflow running both flavour unit suites, `verifyPaparazziDebug`, app lint and merged-manifest assertions, with actions pinned by SHA and wrapper validation. (b) `distributionSha256Sum` from the official `.sha256`. (c) A toolchain decision: 8.14.7 needs AGP/Paparazzi compatibility work. Meanwhile, build only on single-tenant ephemeral hosts.

### CL-11: Entry-point documentation drift

- **Severity / status:** Low, source-verified.
- **Evidence:**
  - `README.md:11` names "Current source: merged PR #10, commit `46c966d…`" while main is at PR #18.
  - `README.md:9` describes 0.14/versionCode 16 as unchanged without a matching line for `noNotif` 20 in the same paragraph.
  - `README.md:12` and `docs/build-and-test.md:47` give 206/88 tests and lint 0/11. `docs/notification-history-validation.md:76` already records 274/95 and 0/12, and this audit measured 290 tests per flavour with app lint at 0 errors / 12 (noNotif) and 13 (full) warnings (section 11).
  - `docs/build-and-test.md:116` says the standalone UI lint has an unchanged `WrongConstant` error. On this commit `:ui:lintDebug` **passes with 0 errors**: the suppression at `UiBinder.java:185` is in place. That sentence is now stale. It also misled my own PR #19 review comment, which called UI lint red; I correct that comment separately.
- **Fix direction:** one dated current-state table naming flavour, versionCode, commit and the newest validation doc. Counts should be dated or replaced by links.

### CL-12: `CallDecision` is test-only dead code

- **Severity / status:** Low (maintainability), source-verified.
- **Evidence:** `CallDecision` (`app/src/main/java/info/fadl/mamalauncher/CallDecision.java`) is referenced only by `CallDecisionTest`; `grep` finds no production caller. The real decision logic is `DirectCallAction.tap/permissionResult`. Green `CallDecisionTest` results therefore say nothing about production. CL-01 shows the two can diverge: `CallDecision` has no notion of cancellation.
- **Fix direction (tiny):** delete it with its test, or route `DirectCallAction` through it and test the real path. Behaviour-preserving either way.

## 6. Design inventory, screen conformance and coverage checklist

There is **no formal design-system specification** in the repository. The inventory below is derived from resources and is not a claimed conformance target.

- **Colours** (`ui/src/main/res/values/colors.xml`): background `#FAF8F4`, card `#FFFFFF` with `#D8D2C6` border, text `#1A1A1A`, action blue `#2F5D8A`, call green `#1E7B34`, danger `#8A1F1F`, star gold, three app-brand tints. Contrast theme: black background with `#FFD600` text (14.87:1).
- **Theme attributes** `surfaceText`, `surfaceAction`, `surfaceSecondaryText` and `surfaceCardBackground` exist for both themes. Most newer code uses them (`SurfaceText`, `CallLogRow`, `PhoneUi`); older layouts (PIN gate, parts of Settings and Dialpad) hard-code `@color/text_main` (CL-04).
- **Type:** Cairo 400/700 bundled. Base sizes are 15-27 sp, and Home labels auto-fit 24 sp down to 18 sp, then ellipsize (`UiBinder.java:191-206`). App font scale is 1.0/1.25/1.5 on top of the system scale (`Prefs.java:38-45`).
- **Shapes:** 18 dp corners on phone controls; tiles use `tile_bg` borders. Phone tabs have no elevation, state-list animator or translationZ (`PhoneUi.java:23-25`). PR #17's goldens show both lower corners of each tab unclipped, with either tab inactive (`CallDirectionRenderTest_contactsTabInactiveHistoryBottomEdge`, `history-arrows-spacing-normal`, inspected as images).
- **Targets:** Home tiles at least 170 dp tall; Call/Back/number buttons at least 64-72 dp; dial keys at least 62 dp (`activity_dialpad.xml`).
- **States:** selected tab = filled blue + bold (`PhoneUi.select`). Inactive tab `#E7EEF5` on `#FAF8F4` is 1.1:1 non-text contrast. Tab identity is carried by bold text and the filled active tab, so I classify this as a subjective note, not a defect.

Screen/state conformance (sources: code + committed renders; renders are layoutlib output, not handset evidence):

| Screen / state | Normal theme | Contrast theme | Largest text | Notes |
| --- | --- | --- | --- | --- |
| Home, clock, badges | renders present | `ClockRenderTest`, `BadgeRenderTest` | `home-a10s-largest` | equal-height cards preserved |
| Phone tabs / history | renders present | `history-*-contrast` | `*-largest` | PR #17 edges correct |
| Contacts grid / failure | renders present | `contacts-failure-contrast` | yes | CL-06 scroll reset |
| Dialpad | renders present | lookup states only | yes | CL-07 alpha-only |
| Confirmation | renders present | none found | `confirm-largest-bottom` | CL-02 main-thread decode |
| PIN gate | renders present | **none; CL-04** | `pin-gate-a10s-largest` | |
| Settings menu / sections | renders present | footer only (scrolled) | yes | CL-04 hint |

**Coverage checklist (brief sections A-J, B2):**

| Area | Status | Notes |
| --- | --- | --- |
| A Functional / accidental action | Reviewed with evidence | Probes P1-P3, P5; invariants I1-I3 |
| B Contacts, photos, concurrency | Reviewed with evidence | CL-02, CL-03, CL-06; no runtime timing |
| B2 Performance / size | Partially reviewed | Source review plus debug APK sizes; no profiling (section 10) |
| C Call history / notifications | Reviewed with evidence | Repository, MissedCallState, listener, noNotif manifest |
| D Accessibility / RTL / design | Partially reviewed | Static, renders and contrast arithmetic; no TalkBack runtime (section 8) |
| E Speech | Partially reviewed | Source only; no audio |
| F Security / privacy | Reviewed with evidence | Manifest, exports, backup rules, PIN, session gate |
| G Build / tests / docs | Reviewed with evidence | Section 11 |
| H Dependencies / licences | Reviewed with evidence | Section 9; repository licence unresolved |
| I Errors / data integrity | Reviewed with evidence | CL-01, CL-09; prefs atomicity reviewed |
| J Compatibility / l10n / maintainability | Partially reviewed | No device/OEM runtime; CL-12 |

## 7. Edge-case matrix

| Case | Status | Evidence |
| --- | --- | --- |
| Permission grant never calls | Handled | `DirectCallAction.java:42`; probe P1 |
| Interrupted CALL_PHONE dialog | **Confirmed failure** | CL-01 |
| Interrupted READ_CONTACTS dialog | Handled | `PhoneActivity.java:162-167` |
| Interrupted READ_CALL_LOG dialog | Handled (loop over arrays, no write when empty) | `CallLogAccess.java:17-25` |
| Multi-number contact, nothing chosen | Handled | `ConfirmCallActivity.java:115`; `LocalFavoritesTest` |
| Chosen number survives recreation | Handled | `ConfirmCallActivity.java:108-113` |
| Duplicate numbers on one contact | Handled (digit-normalised dedup) | `ContactsLoader.java:60` |
| Private / unknown / `-1` / `-2` / `-3` history numbers | Handled (dialog, never callable) | `CallLogEntry.java:24-32`; `CallHistoryController.java:407-413` |
| Exact clicked history number reaches confirmation | Handled | `CallHistoryController.java:427-433` |
| Double tap Call | Handled (`pending`) | `DirectCallAction.java:18-19`; `LauncherFlowTest:231` |
| Double tap contact tile | **Confirmed failure (UX)** | CL-05 |
| Return to Phone keeps position | **Confirmed failure (UX)** | CL-06 |
| Null contacts cursor vs empty | Handled | `ContactsLoader.java:91`; `ContactsFailureTest` |
| Slow / hung contacts provider | Partial (generation guard, no cancel or deadline) | CL-03 |
| Call-log provider slow / huge | Handled (5 s budget, 10 000 pending cap, 200-row pages) | `CallLogRepository.java:16-18` |
| Permission revoked mid-read | Handled (re-check before bind) | `PhoneActivity.java:109`; `MissedCallState.java:41` |
| Unreadable / corrupt photo | Handled (name tile), but sticky (CL-02) | `ContactPhotoLoader.java:69-72` |
| Large photo memory | Partial (subsample + 6 MB LRU; confirmation uncached at 900 px) | CL-02 |
| No dialer / handler for ACTION_CALL | Handled (toast) | `DirectCallAction.java:22-25` |
| SecurityException at call time | Handled (blocked feedback) | `DirectCallAction.java:26-28` |
| Short / `+` / `*#` input | Policy open | CL-07, D-01 |
| Screen off in Settings / PIN gate | Handled (lock + Home) | `ProtectedModeActivity.java:25-39` |
| External settings / browser leave | Accepted boundary | `SessionGate.java`; `SettingsActivity.java:66-76` |
| Process death in Settings | Handled (static session resets to locked) | `SessionGate.java:15-16` |
| Wall-clock change during PIN lockout | **Confirmed gap** | CL-09 |
| Uninstalled / disabled selected app | Handled (tile disappears; package receiver rebuilds) | `MainActivity.java:128-129,33-35` |
| Time / timezone change on Home | Handled (receivers) | `MainActivity.java:93` |
| Minute rollover | Partial: clock may lag up to about 30 s (fixed 30 s tick, not minute-aligned) | `MainActivity.java:71-76` (subjective; `ACTION_TIME_TICK` would be exact) |
| Media volume 0 while speaking | Partial: Settings shows a hint, confirmation does not | `SettingsActivity.java:104-107`; `NameSpeaker.java:154` |
| TTS init hang | Handled (6 s timeout, then next engine) | `NameSpeaker.java:87` |
| noNotif with a stale listener grant | Handled | `NotificationAccess.java:12`; `NoNotificationBuildTest` |
| Work-profile notifications | Excluded by design | `LauncherNotificationListener.java:84` |
| Landscape / tablet / multi-window | Not applicable (portrait-only boundary) | Manifest `screenOrientation="portrait"` |

## 8. Accessibility and TalkBack

Static findings:
- Home tiles expose the full label as `contentDescription` and tooltip, and long press shows the full name (`UiBinder.java:155-156`). The badge appends an Arabic description with correct plural handling even on a non-Arabic device locale (`BadgeCoordinator.java:86-94`).
- History rows merge name, number and detail into one description. Children are excluded and the direction arrow is decorative (`CallLogRow.java:40,50,75`). For a private/unknown row the number is empty, giving "title، ، detail". That is harmless but produces an audible double pause.
- Touch guard affects only pointer input. Accessibility click actions stay available (`BaseActivity.java:110-113`).
- Dial short-input buttons are alpha-dimmed but announced as enabled (CL-07).
- The confirmation favourite star is a `TextView` with `clickable`/`focusable` and a state-specific description (`ConfirmCallActivity.java:171-175`). Its `★/☆` glyph is not announced because the description overrides it.
- Contrast-theme failures on PIN gate / Settings: CL-04.

**Not performed (precise handset checklist for a separately authorised, synthetic-data device):**
1. TalkBack linear traversal on Home, Phone (both tabs), Dialpad, confirmation and PIN gate. Expect: one announcement per tile, Phone badge text, tab "selected" state, no duplicate number announcement on confirmation.
2. With touch guard ON: double-tap activation through TalkBack opens items and the Call button. Physical short taps do nothing; 400 ms holds act.
3. Switch Access / external keyboard: focus reaches every number row, Call, Back, star, Listen; visible focus ring on phone tabs (`phone_*` focused states).
4. Largest system font plus app "huge" (1.5): the PIN pad fits, confirmation bottom actions are reachable, and Home labels don't split Arabic words.
5. High contrast: CL-04 screens, Toasts and system `AlertDialog`s (Light parent theme).
6. Arabic TTS: auto-speak on confirmation, Listen repeat, behaviour at media volume 0, offline-only engine selection. Record the engine package and voice.
7. Permission dialogs: interrupt CALL_PHONE with Home and return (CL-01). Deny twice, then grant from Settings.
8. noNotif: merged manifest on device (`dumpsys package` shows no listener service) and the notification-access UI hidden.

## 9. Security, privacy, build/update and policy; dependency and licence inventory

**Security / privacy (reviewed with evidence):**
- Only `MainActivity` is exported (launcher/home filters). All other activities and the listener are `exported="false"`, and the listener is protected by `BIND_NOTIFICATION_LISTENER_SERVICE`.
- No INTERNET permission, no network code, no logging calls in production (`grep Log.` finds none).
- `allowBackup="false"` plus exclusion rules for cloud and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- PIN: PBKDF2-HMAC-SHA256, 12 000 iterations, 128-bit random salt, constant-time compare, legacy fallback (`PinLock.java`), with lockout 30 s doubling to a 10 min cap.
  - This is a convenience gate, not a hardware-backed boundary. With prefs access (root/backup extraction, which is disabled) a 4-digit PIN is brute-forceable offline. Online, the cap allows about 14 days for the full space. CL-09 covers clock handling.
- Missed-call state stores only `id:date:type` identities, never numbers or names (`MissedCallState.java:9`). The notification store is process-memory metadata only.

**Policy distinctions:** runtime permission behaviour (above) is separate from Google Play eligibility. `READ_CALL_LOG` and notification-listener access are both subject to Play policy review if the app is ever distributed there. That is not assessed or guaranteed here. A sideloaded build is unaffected by Play's target-API deadline, but Play Protect warnings are outside this audit.

**Dependencies:**

| Scope | Artifact | Version |
| --- | --- | --- |
| Runtime | none beyond the `:ui` project module | n/a |
| Build | AGP `com.android.tools.build:gradle` | 7.4.2 |
| Build / test | Paparazzi plugin | 1.2.0 |
| Test | JUnit | 4.13.2 |
| Test | Robolectric (+ android-all-instrumented 24/25/30/33 runtimes) | 4.11.1 |
| Wrapper | Gradle | 7.6.4 (jar checksum verified, distribution not pinned) |
| Repositories | `google()`, `mavenCentral()` | n/a |

**Licences:**
- The Cairo font is OFL-1.1, with the licence shipped in `ui/src/main/assets/OFL.cairo.txt`.
- Material icon paths are Apache-2.0 (`NOTICE.md`).
- **The repository itself has no LICENSE file**, so the project's own licence is unresolved. That is an owner decision; this report draws no legal conclusion.

## 10. Performance, resource use and package size

**Measured here (local debug builds; not release, not R8):**
- `app-noNotif-debug.apk` is 273,166 bytes and `app-full-debug.apk` is 273,630 bytes.
- The noNotif APK holds 66 entries (554,489 bytes uncompressed). The largest are `classes3.dex` (168 KB, the app code in this debug multidex layout) and the two Cairo font files (91.5 KB and 91.7 KB). There are no bitmaps in `src/main`: icons are vectors.
- Package size is not a concern. ImgBot's PNG savings (PR #16) are test fixtures and do not affect the APK.

No timing, frame, heap or battery numbers were measured; I report none.

**Source-supported risks (unmeasured):**
- Main-thread photo decode (CL-02), and the confirmation decode at 900 px in `onCreate`.
- A full contacts read, sort and adapter replacement on every Phone resume (CL-03, CL-06).
- Home rebuilds all tiles on every resume. That includes package label/icon lookups on the main thread (`MainActivity.java:97,116-142`) and a two-pass tile measure (`UiBinder.java:210-222`). `HomeLabel.onMeasure` calls `setTextSize`/`setMaxLines` during measurement (`UiBinder.java:191-206`), which can request extra layout passes.
- `NotificationBadgeStore` listeners run binding on every listener snapshot (120 ms debounce).

**Bounded by design:** call-log reads (5 s, 200 rows), pending set (10 000), photo LRU (6 MB), the clock tick runs only while Home is resumed, the call-log observer is registered only while visible, there is no service other than the system-bound listener, and there are no jobs or alarms.

**Measurement plan (not performed):**
- Use a release-like, non-debuggable build of `noNotif` on an emulator: API 30, 2 GB RAM, `ro.config.low_ram` both ways. Use the A10s only if separately authorised.
- Workloads:
  - 50 / 500 / 2 000 synthetic contacts, with and without 1-4 MP photos, plus corrupt and rotated photos.
  - 2 000-row synthetic call log.
  - Largest text, high contrast.
- Capture with Perfetto: cold/warm start to the first Home frame, Phone first and second entry, confirmation open (before/after photo), and grid fling frame times. Add `dumpsys meminfo` peak and settled values after 20 open/back cycles, and StrictMode disk/network-on-main logs.
- Repeat 5×. Report median and P90 with build, API and device.

## 11. Executed checks

All checks were run in the worktree at `79774e4` with JDK 11, `--max-workers=1`, sequentially. Environment setup notes:
- Maven Central rate-limited (HTTP 429) and redirected large artifacts to a mirror host that the sandbox proxy initially blocked. I added that host to this session's network allowlist and re-ran.
- I pre-fetched the Robolectric runtimes and checked their SHA-512 against Maven Central.
- Project dependencies and versions were not changed.

| Command | Result | Notes |
| --- | --- | --- |
| `./gradlew --version` | OK | Gradle 7.6.4, JVM 11.0.33 |
| `:app:testNoNotifDebugUnitTest` | **PASS**: 290 tests, 0 failures, 0 errors, 12 skipped | Skips are the 4 `NotificationListenerFlowTest` methods × API 24/25/33, guarded by `Assume.assumeTrue(BuildConfig.NOTIFICATION_ACCESS)`. Intentional. |
| `:app:testFullDebugUnitTest` | **PASS**: 290 tests, 0 failures, 0 errors, 9 skipped | Skips are the 3 `NoNotificationBuildTest` methods × 3 APIs (`assumeFalse`). Intentional. |
| `:ui:verifyPaparazziDebug` | **PASS**: 106 UI tests, 0 failures; committed goldens unchanged | First two attempts failed only on Maven HTTP 429 dependency downloads, not on image comparison |
| `:app:lintNoNotifDebug` | **PASS**: 0 errors, 12 warnings | 7 LockedOrientationActivity (portrait boundary), ClickableViewAccessibility, InlinedApi (`CATEGORY_NAVIGATION` string constant, harmless), UnusedAttribute (`dataExtractionRules` < 31, intended), RedundantNamespace, AppBundleLocaleChanges (Arabic plural context; irrelevant to APK sideload) |
| `:app:lintFullDebug` | **PASS**: 0 errors, 13 warnings | Same + MonochromeLauncherIcon |
| `:ui:lintDebug` | **PASS**: 0 errors, 156 warnings | 69 UnusedResources (library resources used by `:app`), 38 HardcodedText, 25 UnusedAttribute, 11 ContentDescription, others ≤2 each. The previously documented `WrongConstant` error is no longer reported. |
| `:app:assembleNoNotifDebug :app:assembleFullDebug` | **PASS** | Local only, not distributed; sizes in section 10 |
| `:app:processNoNotifReleaseMainManifest :app:processFullReleaseMainManifest` | **PASS** | Merged noNotif release manifest: 0 occurrences of `LauncherNotificationListener` / `NotificationListenerService` / `BIND_NOTIFICATION_LISTENER_SERVICE`; versionCode 20 / `0.15-no-notif-footer-candidate`; permissions exactly READ_CONTACTS, CALL_PHONE, READ_CALL_LOG. Full: listener present (3 occurrences), versionCode 16. |

Counts differ from older docs (206/88, 274/95) because tests were added since. The counts are not a proxy for correctness.

**Untracked probes (not committed; deleted after use):** `app/src/test/.../ClaudeAuditProbeTest.java` (P1-P6, Robolectric API 33) and `ui/src/test/.../ClaudeContrastProbeTest.java` (Paparazzi render, `MamaTheme.Contrast`). Outputs are quoted in section 5. `git status` after deletion showed a clean tree apart from this report.

**Blocked / not run:** emulator and handset runtime, TalkBack/Switch Access, audio, Perfetto/heap, release assemble/signing, Semgrep (not installed; no new tool added).

**CI:** the repository has no GitHub Actions workflow. Earlier PR checks were review bots, not Android builds.

**Test quality notes:**
- The suites are strong on adversarial lifecycle cases: latches for slow providers, permission revoke mid-read, generation discards, API 24/25/30/33 matrices, and visual fit at A10s dimensions.
- Gaps found:
  - No empty-array CALL_PHONE case (CL-01).
  - No PIN gate or Settings-top contrast renders (CL-04).
  - No navigation double-tap test (CL-05).
  - No grid position retention test (CL-06).
  - No system-star multi-number un-pin test (CL-08).
  - `CallDecisionTest` tests dead code (CL-12).

## 12. Good existing safeguards worth preserving

- The single call entry point with a `pending` debounce, and the documented "never call from a callback" contract (`DirectCallAction.java:8,18-19,42`).
- Multi-number "choose visibly first", with the chosen number restored after recreation and a separate visible "selected number" line (`UiBinder.java:39-41,77`).
- History confirmation uses the exact clicked number, and contact matching is identity-safe: the short-number threshold plus E.164 validation, never suffix matching (`DialContactMatcher.java:20-27`).
- Provider failure is distinguished from empty results, and stale results are discarded via generation counters across Phone, Dialpad, History and badges.
- Call-log reads are bounded, cancellable and read-only. Missed-call acknowledgement stores identities only.
- The `noNotif` capability is removed at manifest and runtime level, not merely hidden.
- PIN: a slow KDF, salt and persisted lockout, recovery through the device credential, and a protected session that ends on any leave or screen-off.
- Accessibility click paths bypass the touch-hold requirement, and pointer input alone is guarded.
- Backups are disabled with explicit exclusion rules, there is no INTERNET permission, and no production logging.
- Paparazzi goldens at A10s geometry, largest text and RTL, with documented "never regenerate to hide a failure" discipline.

## 13. Prioritised roadmap (recommendations only)

| Priority | Work | Effort / risk | Gate |
| --- | --- | --- | --- |
| Immediate small fix | CL-01 cancellation handling (both hosts) | Small / low-medium | Empty-array + revoke/regrant tests; no call from callbacks |
| Small follow-ups | CL-04 theme attributes on PIN gate / Settings; CL-05 navigation debounce; CL-06 keep grid position; CL-09 lockout clamp; CL-12 remove dead class | Small each / low | Contrast renders added; one-intent test; 120-contact position test |
| Owner decisions | D-01 short numbers (CL-07); CL-08 favourite semantics | n/a | Decision recorded, then a small PR |
| Docs | CL-11 dated current-state table | Small / none | Versions match `app/build.gradle` |
| Build assurance | CL-10 CI + distribution checksum; toolchain path for the Gradle advisories | Medium / medium | CI green on both flavours; goldens unchanged |
| Measure first | Section 10 plan | Medium / low | Recorded baseline |
| Targeted later | CL-02 photo pipeline, CL-03 contacts worker | Medium / medium concurrency | Measured improvement, identity tests |

No rewrite, language or UI-framework migration, targetSdk bump or new dependency is recommended.

## 14. Accepted boundaries, open questions, limits

**Accepted / documented boundaries (not counted as findings):**
- `full` 16 cannot update `noNotif` 20.
- Portrait only.
- The settings session survives exactly one sanctioned external leave (app info, TTS installer, notification access, developer site). Within that leave the user can navigate elsewhere until Settings resumes or the screen turns off.
- The pending-missed acknowledgement is applied asynchronously.
- Work-profile notifications are ignored.
- The 30 s clock tick.

**Open questions for the owner:**
- D-01 short-number policy.
- CL-08 favourite semantics.
- The project licence.
- Whether Play distribution is a goal (it changes the policy and target-API analysis).
- Actual A10s Android / One UI version and the low-RAM flag.

**Overlap check after writing.** After this report was complete, I compared its finding titles with the 2026-11-11 Codex report's findings table (GPT-01…07). CL-01/02/03/07/10/11 correspond to GPT-01/02+04/03/06/05/07. CL-04, CL-05, CL-06, CL-08, CL-09 and CL-12 do not appear in that table. I did not change any finding after the comparison. Two partial overlaps, for transparency:
- The Codex report's GPT-01 notes that `CallDecisionTest` "tests a helper, not this persistence branch". That is related to CL-12, but it does not record that the class has no production caller.
- Its section 6 computes yellow-on-black (14.9:1) and normal-theme pairs, but not the fixed dark colours left on black in the PIN gate and Settings (CL-04).

**Limits:** this is a source-plus-JVM audit. Robolectric and layoutlib are not Samsung One UI. Contrast ratios are computed from declared colours, not measured on a panel. No finite audit establishes the absence of all defects.

## 15. Source ledger

Repository (pinned to `79774e4`): every `file:line` above. Key files: `DirectCallAction.java`, `PhoneActivity.java`, `ConfirmCallActivity.java`, `DialpadActivity.java`, `ContactPhotoLoader.java`, `ContactsLoader.java`, `CallHistoryController.java`, `CallLogRepository.java`, `MissedCallState.java`, `LocalFavorites.java`, `PinGateActivity.java`, `PinLock.java`, `BaseActivity.java`, `themes.xml`, `colors.xml`, `activity_pingate.xml`, `activity_settings.xml`, both manifests, `app/build.gradle`, `gradle-wrapper.properties`.

External:
- [S1] Android API reference, `Activity.onRequestPermissionsResult` (interrupted request → empty arrays, treat as cancellation): https://developer.android.com/reference/android/app/Activity#onRequestPermissionsResult(int,%20java.lang.String[],%20int[])
- [S2] Android API reference, `Intent.ACTION_CALL` (cannot be used for emergency numbers; use `ACTION_DIAL`): https://developer.android.com/reference/android/content/Intent#ACTION_CALL
- [S3] GHSA-3cfg-jpcr-gfp3: https://github.com/gradle/gradle/security/advisories/GHSA-3cfg-jpcr-gfp3
- [S4] GHSA-gqrr-4j89-5267: https://github.com/gradle/gradle/security/advisories/GHSA-gqrr-4j89-5267
- [S5] GHSA-pcp8-98xm-c6pr: https://github.com/gradle/gradle/security/advisories/GHSA-pcp8-98xm-c6pr
- [S6] Gradle wrapper checksum list (gradle/actions): https://raw.githubusercontent.com/gradle/actions/main/sources/src/wrapper-validation/wrapper-checksums.json
- [S7] WCAG 2.2 contrast minimum (1.4.3) and non-text contrast (1.4.11): https://www.w3.org/TR/WCAG22/

S1 and S2 were read on 2026-11-12. The full reference pages are too large for this session's fetcher, so the quoted wording was confirmed through search results that mirror the reference text. Re-check the live pages before quoting them elsewhere.
