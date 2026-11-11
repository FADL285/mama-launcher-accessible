# Mama Launcher — independent Codex GPT audit

## 1. Executive summary and bounded verdict

**Audit date: 2026-11-11 (Africa/Cairo). Audited main: `564f4556841032ba95f29c6fb3fca0e1fd5fce71`. Report only; no application fixes or release.**

No confirmed Critical/High application defect or release-blocking security defect was established. This is **not release approval**, a penetration-test pass, an accessibility certification, or evidence that candidate20 works on Samsung A10s. The main Android build/test/manifest verification could not run in this environment. In particular, absence of a confirmed blocker is weaker evidence than a successful device acceptance run.

Seven actionable findings: **0 Critical, 0 High, 4 Medium, 3 Low**. Two have narrow isolated Java reproductions, five are source-verified risks/gaps. The important issues are a cancelled call-permission request becoming a persistent denial hint, synchronous photo I/O/decode, contacts work without effective cancellation/bounds, and missing reproducible build-assurance gates. Three lower-priority issues concern stale photo caching, misleading short-number Call appearance, and current-documentation drift. `GPT-01/02/03/05` revalidate debt recorded in `docs/audit-014.md` (A02/A01/A03/A05 respectively); `GPT-04` is the related photo-cache issue already noted under A01. These are not newly discovered defects. `GPT-06` demonstrates a UI/action mismatch; the short-input policy remains open for the owner’s decision.

The small framework-Views runtime, deliberate call boundary, permission rechecks, explicit multi-number selection, provider-failure handling, read-only history, notification capability guards, and targeted regression suites are worth preserving. A rewrite is not justified by this audit. Profile and validate before optimizing.

Gradle's current release notes list three advisories published on November 10 that affect the pinned7.6.4 tooling. Repository inspection did not establish the catalog/project-supplied-registry triggers, but cannot exclude the third advisory's stale-daemon/local-attacker prerequisites on a build host. No exploitation or installed-APK vulnerability is asserted. See section9 for applicability and mitigations, independent of upstream severity.

## 2. Audit identity, method and exclusions

| Item | Evidence / scope |
| --- | --- |
| Reviewer | Codex GPT; independent source review, no delegated agents |
| Repository | https://github.com/FADL285/mama-launcher-accessible |
| Fetched base and owner baseline | Both `564f4556841032ba95f29c6fb3fca0e1fd5fce71`; PR17, candidate20 phone-tab surface correction |
| Audit branch | `audit/codex-gpt-2026-11-11`, created from that exact fetched commit in a fresh checkout |
| Baseline difference | None at start; final main check recorded in section 14 |
| Environment | Linux x86_64; OpenJDK runtime 21.0.13; no compatible JDK11/Android SDK, adb or emulator available in inspected locations/PATH; no cached Gradle distribution |
| Bootstrap result | `./gradlew --version` exited 1: `java.net.SocketException: Network is unreachable` downloading Gradle7.6.4 |
| Review inventory | All 37 app and 18 UI production Java files; two modules; build scripts/wrapper properties; manifests/overlay; resources; backup rules; 39 app test-source files and 19 UI test-source files; historical documentation listed below |
| Static inventory counts | 248 app `@Test` annotations and 106 UI `@Test` annotations; these are source counts, **not executed test counts**. SDK parameterization expands cases |
| Pixel inspection | 16 existing committed synthetic PNG renders inspected; no goldens generated or changed |
| Local probes | Exact copies of four current Java production files with minimal synthetic boundary stubs, compiled/run outside the repository; 15 assertions; details in section 11 |
| Contribution instructions | No tracked AGENTS.md or contribution guide found in the checkout; supplied assignment and user instructions applied |

Read README, docs/README, architecture, build-and-test, family-guide.ar, no-notification-candidate, notification-history-validation, handset-feedback-013/014, audit-013/014, PR6/7/8 triage, SECURITY-AUDIT and REVIEW-FOLLOWUP. Historical claims were checked against current implementation; no other model's new audit was read. Historical test totals were not reused as current execution evidence.

Excluded: real handset operations, real calls/service codes, contact edits, permission/default-app changes, storage clearing, release builds/distribution/signing, Drive, store settings, malware submissions, paid services, external source-upload scanners, secrets or personal contact inspection. Only synthetic names/numbers were used. The PR adds this single Markdown file; probes/contact sheets stay outside the checkout.

## 3. Product/flavor/permission map and critical invariants

| Surface | Current source contract | Evidence |
| --- | --- | --- |
| Build shape | `app` application plus `ui` Android library, Java8 source, framework Views | [app/build.gradle](../../app/build.gradle), [ui/build.gradle](../../ui/build.gradle), [settings.gradle](../../settings.gradle) |
| Common identity | `info.fadl.mamalauncher`, min24 / compile33 / target33 | `app/build.gradle:4-11` |
| full | versionCode16, `0.14-pilot-candidate`, `NOTIFICATION_ACCESS=true`; listener retained | `app/build.gradle:10-18`; `app/src/main/AndroidManifest.xml:46-50` |
| noNotif | versionCode20, `0.15-no-notif-footer-candidate`, capability false; listener removal overlay | `app/build.gradle:20-25`; `app/src/noNotif/AndroidManifest.xml:3-6` |
| Both flavors | READ_CONTACTS, CALL_PHONE, READ_CALL_LOG; no INTERNET/SMS/WRITE_CONTACTS/WRITE_CALL_LOG | `app/src/main/AndroidManifest.xml:6-9` |
| Exported boundary | Main only, HOME/LAUNCHER, singleTask, portrait; internal activities non-exported | `app/src/main/AndroidManifest.xml:53-99` |
| Listener boundary | Non-exported, system binding protected by BIND_NOTIFICATION_LISTENER_SERVICE | Main manifest service declaration; full only after overlay application |
| Runtime reduction | noNotif rejects stale grants/settings launch, hides grant/restricted-settings help, masks app dots | `NotificationAccess.java:12-29`; `SettingsActivity.java:81-99,263-281`; `BadgeCoordinator.java:79-81`; `NotificationAccessUi.java:10-14` |

Paths without a module prefix below refer to `app/src/main/java/info/fadl/mamalauncher/`; UI paths refer to `ui/src/main/java/info/fadl/mamalauncher/ui/`. Line links in findings are pinned to the audited commit. Relative links elsewhere resolve in this report branch; all cited production files remain identical to the base.

**Merged-manifest limitation:** the overlay and guards support the intended capability reduction, and `NoNotificationBuildTest` asserts service absence across API24/25/33. Actual Gradle-merged manifests/APKs were not produced or inspected here. Do not describe source overlay inspection as a fresh binary verification. On a capable runner, discover manifest tasks via `:app:tasks --all`, run each variant's manifest processing, and inspect service, binding permission/action, applicationId, exported components and permissions in both outputs.

Critical invariants traced:

- Call is initiated by `DirectCallAction.tap`, wired to explicit Call actions; permission callbacks, contact matching, Listen, Favorite and history completion do not call. `pending` suppresses repeated taps until return/result. No carrier interaction was executed.
- Single-number confirmation displays/preselects that value; multiple numbers require visible choice. Selection/highlight survive recreation from saved state and original intent (`ConfirmCallActivity:74-119`, `UiBinder:25-100`). No provider revalidation of an already-open confirmation is promised; edited/deleted contacts can leave its original visible snapshot until exit.
- History uses exact clicked raw number, resolving current optional identity before opening confirmation (`CallHistoryController:407-433`). Invalid/private/sentinel records have an informational dialog, not a call path (`CallLogEntry:24-32`). Dialpad intentionally accepts service-symbol input; no emergency/service call was exercised.
- Null/error provider responses are distinct from empty success. Current grant, lifecycle and generation checks reject obsolete publication. These controls do not themselves cancel underlying work (F03).
- Missed-in-log, locally unacknowledged, and visible system notification are different states. No write to call logs or notification cancellation was found.
- Settings is gated with salted PIN/device credentials and a memory-only session; ordinary departure and screen-off invalidate it. Sanctioned external excursions are a documented exception, including the browser link; see section 14.

## 4. Findings table

Severity reflects this application's evidenced impact and prerequisites, not an upstream advisory score. “Reproduced” below means an isolated stub-boundary Java reproduction, **not Android/OEM reproduction**.

| ID | Severity | Confidence / status | Finding | History |
| --- | --- | --- | --- | --- |
| GPT-01 | Medium | High / reproduced (isolated Java) | Empty call-permission result poisons future retry | Previously documented A02, still present |
| GPT-02 | Medium | High / source-verified risk | Photos perform provider I/O and decoding on UI thread | Previously documented A01, deferred |
| GPT-03 | Medium | High / source-verified risk | Contacts reads lack cancellation/bounds; optional resolution can stall history | Prior A03, broader current consumers checked |
| GPT-05 | Medium | High / source-verified gap | Build integrity and repeatable Android checks are not enforced | Prior A05; current advisories separately assessed |
| GPT-04 | Low | High / source-verified risk | Positive/negative photo cache never invalidates during Phone lifetime | Prior A01 secondary risk, still present |
| GPT-06 | Low | High / reproduced handoff + source-verified UI | Dim short-number Call remains actionable | Newly reported here; not established as regression |
| GPT-07 | Low | High / source-verified gap | Current entry-point documents contradict current flavors/candidate | Newly reported drift; historical records are not defects merely for being old |

No unverified hypothesis or subjective recommendation is counted as a confirmed actionable finding. Device validation gaps are separate from this count. The source-copy Java probes are not Android/OEM tests. In particular, GPT-06 establishes an availability/action inconsistency, not a requirement to prohibit short numbers; only the owner can decide that policy.

## 5. Detailed findings

### GPT-01 — Interrupted call permission becomes a persistent denial hint

**Medium; high confidence; reproduced with synthetic Java activity/preferences stubs.** Both flavors, API24+; confirmation and dialpad, when CALL_PHONE is absent and the next rationale result is false.

Evidence: [DirectCallAction.java:29-41](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/DirectCallAction.java#L29). `permissionResult` treats an empty result as denial and persists `call_denied_once`; the next tap sees this plus false rationale and only shows blocked feedback.

Executed P1: denied permission → tap → one synthetic permission request → empty result → `denied=true` → second tap with false rationale → still one request, zero calls. A grant control verifies zero calls from callback and one only after a fresh tap; repeated tap is suppressed. Boundary stubs deliberately control rationale/grant; the probe does not establish how often Samsung delivers that exact combination. Android documents interrupted empty callbacks [E1].

Impact: a cancellation can prematurely divert the family to setup recovery. This fails closed, not an unintended-call vulnerability. Existing Contacts retry/cancellation tests cover a different callback; `CallDecisionTest` tests a helper, not this persistence branch. Actual grant is checked at tap time and already-granted users bypass it.

Minimal direction: ignore interrupted/empty or unrelated results when recording completed denial; keep a fresh explicit Call press mandatory. Small effort/low-to-medium permission-flow risk. Add actual activity regressions for empty arrays, unrelated permission, first/repeated denial, false/true rationale, revoke/regrant, and pending-state clearing across API24/30/33. Previously recorded in `docs/audit-014.md` A02, independently rechecked; no fix applied.

### GPT-02 — Synchronous photo work competes with touch/rendering

**Medium; high confidence; source-verified risk, latency/ANR not measured.** Both flavors/API24+, photo Contacts grid and confirmation, especially uncached/slow/large images.

Evidence: [ContactGridAdapter.java:56](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/ui/src/main/java/info/fadl/mamalauncher/ui/ContactGridAdapter.java#L56), [ConfirmCallActivity.java:89](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/ConfirmCallActivity.java#L89), [ContactPhotoLoader.java:31-73](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/ui/src/main/java/info/fadl/mamalauncher/ui/ContactPhotoLoader.java#L31). UI binding/onCreate calls streams, bounds/pixel decode and possible scaling synchronously. New confirmation creates a new loader, so repeated visits do not share its decode cache.

Reasoning: a cache miss blocks that UI callback until provider and decoding finish. Potential impact is delayed scrolling/confirmation/taps; no frame-time, peak-memory or ANR number was measured. Android bitmap guidance [E5] explains dimensions/subsampling, not a measured regression for this app.

Protections: bounds pass, power-of-two sample, stream closing, 6MiB bitmap LRU per loader, recycled raw bitmap after scaling, unreadable-photo fallback and clearing recycled views. The byte cap is not a whole-process or transient-allocation guarantee; OutOfMemoryError is not caught by `catch(Exception)`. There is no evidence here that ordinary images currently exhaust memory. Grid/detail use different loaders, so URI-only keys are **not** a demonstrated cross-size cache bug.

Minimal direction: first profile section 10's workload. If material, use a small bounded, cancellable loader with immediate placeholder, URI/contact/size identity checks on completion, invalidation (F04), and lifecycle cleanup. Medium effort/medium concurrency risk. Tests: rapid reuse, null/corrupt/revoked streams, large/rotated photos, navigation/destruction, memory pressure, and never applying a delayed image to another contact. A01 remains deferred; this report does not request an image-library/framework migration.

### GPT-03 — Contacts pipeline is not cancellable or resource-bounded

**Medium; high confidence; source-verified risk.** Both flavors/API24+, granted Contacts; slow provider, large address book or repeated navigation/retry.

Evidence: [PhoneActivity.java:92-127,142-149](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/PhoneActivity.java#L92), [ContactsLoader.java:79-100](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/ContactsLoader.java#L79), [HistorySnapshot.java:53-70](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/HistorySnapshot.java#L53), [CallHistoryController.java:225-234](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/CallHistoryController.java#L225), `DialpadActivity:25,34-45,50-59,85-97,123`.

Reasoning: each Phone refresh creates a raw thread; pause increments a generation but neither cancels nor bounds active reads. Loader has no CancellationSignal, timeout or row cap, and materializes raw rows plus grouped structures. Dialpad has one executor, but an uncooperative contacts read prevents later work progressing; generation guards skip obsolete jobs only when they run. Optional history labels run a complete contacts load before publishing otherwise-valid call-log results. The history worker's six-second UI timeout hides stale publication, but cannot interrupt this Contacts query; retries remain behind it until return. Even without selecting History, Phone refreshes Contacts on resume.

Impact: retained activity/work and delayed recovery under slow providers; transient memory growth for very large books. No observed process crash, leak duration, exhaustion threshold or handset timing is claimed. `SettingsActivity:301-320` also owns uncancelled discovery work, usually a smaller separate workload.

Contrary evidence: verified loader/null handling, cursor try-with-resources, on-completion grant checks, Phone stale/lifecycle guards, Dialpad shutdownNow, and CallLogWorker cancellation/timeout are real protections. `ContactsFailureTest` tests failure/revoke/retry; call-log concurrency tests cover their own worker, not cancellation inside ContactsLoader. They do not bound raw contacts threads or make optional resolution nonblocking.

Minimal direction: one small shared contacts repository with bounded ownership, coalescing, provider CancellationSignal and in-loop deadline/cancel checks; explicit policy for uncooperative providers. Let valid history render number-only when optional contacts resolution is unavailable/late. Medium effort/medium risk. Add latch-controlled overlapping refresh/pause/destroy, blocked Contacts with valid call log, repeated retries, large result sets, and cursor-close/permission-loss tests. Do not replace unknown/failure with empty contacts. Prior A03 remains; current history coupling is an additional checked path, not a separately counted root cause.

### GPT-04 — Photo success and failure caches remain stale

**Low; high confidence; source-verified risk, provider mutation not run.** Both flavors/API24+, Phone instance retained across ordinary returns, same photo URI reused or unreadable image later repaired.

Evidence: [ContactPhotoLoader.java:21-35,73](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/ui/src/main/java/info/fadl/mamalauncher/ui/ContactPhotoLoader.java#L21), [PhoneActivity.java:38,65-70,110](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/PhoneActivity.java#L38).

Reasoning: the loader survives adapter refresh; cache hit returns before reading current provider content and `misses` prevents any later attempt at that URI. Neither has refresh invalidation. Thus unchanged URI plus changed pixels yields old pixels while cached; one temporary read failure yields placeholder until Phone is recreated. Negative entries are also unbounded by count. This does not prove arbitrary wrong-person recycling: binding is synchronous and fallback clears old image state.

Minimal direction: invalidate at a defined completed refresh/photo version boundary; bound/expire negative cache entries and clear them after recovered permission. Small-to-medium effort/medium identity/concurrency risk when combined with F02. Tests: same URI/new pixels, failed-then-readable URI, eviction, revoke/regrant and no stale reused image. Previously noted as A01 secondary risk; do not count it as a new exploit.

### GPT-05 — Build assurance is local/documentary, not enforced

**Medium; high confidence; source-verified tooling gap, not evidence of compromise.** Both flavors, developer/release workflow; a fresh unprimed runner or dependency/wrapper change.

Evidence: [gradle-wrapper.properties:1-7](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/gradle/wrapper/gradle-wrapper.properties#L1), [build.gradle:1-8](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/build.gradle#L1), tracked-tree absence of `.github/workflows`, dependency lock/verification metadata; live GitHub workflow-runs API returned an empty list for base (push-triggered query). No Android build CI was established. Bot checks are not Android CI.

Pinned dependencies and official repository URLs are positive. Local wrapper SHA256 `14dfa961b6704bb3decdea06502781edaa796a82e6da41cd2e1962b14fbe21a3` **matches Gradle's official 7.6.4 Wrapper JAR checksum** [E6]. This corrects the weaker claim that nothing was verified. The distribution lacks `distributionSha256Sum`; transitive resolved versions/artifact integrity are unverified because resolution was blocked. Source pinning is not a full reproducibility/integrity policy.

Impact: clean check badges or historical counts cannot establish both flavors still build/test/lint on a clean runner. Fresh retrieval is less strongly verified. The three upstream advisories in section9 require controlled toolchain planning. The stale-daemon port-squatting prerequisites cannot be excluded from repository contents; build-host exposure is unresolved, not evidence of exploitation or an installed-APK vulnerability.

Minimal direction: separate PR for CI running both suites, Paparazzi verify, app/UI lint and local debug assembly without release keys; verify and add official distribution checksum, review dependency verification and action pinning. Plan toolchain security upgrades with AGP/Paparazzi/JDK compatibility checks, not an untested automatic bump. Small-to-medium initial effort/medium build-maintenance risk. Regression gates must fail on golden mismatch, relevant lint errors and checksum tampering; record intentional flavor skips. Previously documented A05, still open.

### GPT-06 — Dimmed short-number Call still dispatches

**Low; high confidence; isolated handoff reproduced, UI branch source-verified.** Both flavors/API24+, dialpad, one/two typed characters with CALL_PHONE already granted.

Evidence: [DialpadActivity.java:145-146,207-218](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/DialpadActivity.java#L207), [DirectCallAction.java:17-21](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/app/src/main/java/info/fadl/mamalauncher/DirectCallAction.java#L17), `DialpadActivity:48-49`.

Reasoning: `sync()` treats length<3 as unavailable-looking via alpha only; Call remains wired/enabled and its handler accepts any nonblank string. Save, by contrast, returns early for length<3. P3 passed synthetic `"7"` to the exact DirectCallAction with granted permission and observed one stub handoff. No call/service/emergency number was sent to Android. The real screen/touch path was not executed.

Impact: visible availability and activation disagree for the intended low-literacy audience. This still requires a deliberate action and permission; it does **not** establish silent calling, carrier acceptance, emergency bypass, or a product requirement to ban all short codes.

Minimal direction: leave the short-input policy open for the owner’s decision, then make visual state, accessibility enabled state and handler agree; if short numbers are supported, avoid disabled-looking Call. Do not blindly impose a three-digit safety rule on all call sources. Small effort/low-to-medium behavior risk. Tests: empty, one/two digits, accepted short inputs, ordinary number, touch guard/accessibility activation and Save consistency. Newly reported, no regression origin asserted.

### GPT-07 — Current documentation exposes conflicting release/capability state

**Low; high confidence; source-verified documentation gap.** Both flavors, developer/family handoff; selecting instructions based on entry-point docs.

Evidence: [README.md:7-13,32](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/README.md#L7) calls merged features a branch and identifies PR10 as “Current source”; [docs/build-and-test.md:3](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/docs/build-and-test.md#L3) says delivered noNotif19 while [docs/no-notification-candidate.md:1-9](https://github.com/FADL285/mama-launcher-accessible/blob/564f4556841032ba95f29c6fb3fca0e1fd5fce71/docs/no-notification-candidate.md#L1) and `app/build.gradle:20-25` identify20. Family guide's final feature section still describes changes as review-only and broadly gives notification-access/restricted-settings guidance without that section distinguishing noNotif.

Impact: readers can confuse stable handset14, full16 and listener-free candidate20, or follow inapplicable notification setup. Runtime noNotif guards correctly hide those controls; the candidate document explicitly explains the boundary. Those protections reduce severity. Historical reports appropriately retain older results and are not defects for doing so.

Minimal direction: one canonical current-source/candidate/flavor table at entry points, preserve dated historic records, and separate handset acceptance from compiled source. Small effort/low risk; link/manual review should compare metadata, commands and noNotif disclosure with both flavors. No README or guide was changed here.

## 6. Design inventory, conformance and coverage

No complete formal design-system specification was found. This inventory derives from actual themes/resources, shared binders, approved behavior and PR17. It is not conformance to an invented specification.

| Token / rule | Actual implementation |
| --- | --- |
| Surface/type | Warm background `#FAF8F4`, dark text `#1A1A1A`; bundled Cairo400/700; surfaceText/action semantic attributes |
| High contrast | Black `#000000`, yellow `#FFD600`, 2dp outlined14dp cards; some action/key surfaces intentionally remain light or blue/green |
| Actions | Call `#1E7B34`, Back/primary blue `#2F5D8A`; press/focus selectors; confirmation minimum84dp Call/72dp Back |
| Home | 170dp minimum tiles,68dp icon box,24sp label fitted to18sp floor, up to two lines,6dp gap; equal-height two-pass measurement is intentional |
| Phone/history | 18dp corners, pale inactive fill, explicit focus/pressed states; `PhoneUi.select` removes animator/elevation/translationZ; filters10dp gap; history12dp end padding/10dp transparent separators |
| Directionality | Clock marker physically left through LTR row; numbers have LTR isolation/direction; call direction symbols intentionally do not mirror; Arabic names retain bidi-aware text |
| Scale/support | App overrides fontScale to1/1.25/1.5 rather than inheriting arbitrary system scaling; confirm this family-facing policy during accessibility testing |

Source-color arithmetic (not device certification): dark text on warm surface ≈16.4:1, white on Call green ≈5.3:1, white on Back blue ≈6.9:1, yellow on black ≈14.9:1. These pairs do not establish contrast of every translucent/disabled/icon/state pixel; see F06 and the manual matrix.

| Screen/state | Source/tests and inspected pixels | Assessment |
| --- | --- | --- |
| Home/Apps, long Arabic, largest selected text, contrast | HomeLabelFit/Feedback/PinGateFit tests; populated largest Home and contrast Home PNGs | Visible centering/physical clock ordering supported; no live services/device evidence |
| Contacts, denied/empty/failure/retry | PhoneActivity, ContactsFailure/ContactsPermissionRetry tests, scrollable recovery layout | Source controls supported; real grid/photo performance and full-name announcements unverified |
| Confirmation single/multi, speech recovery | UiBinder/ConfirmCall; single-number and speech-options contrast pixels | Explicit target/actions supported; largest full list/real TalkBack not certified |
| Dialpad before/after suggestions | StableDialpad tests and both actual PNGs inspected | Keys align in selected renders; short-window scrolling/physical guard still needs runtime checks |
| History with either tab inactive | Four normal/large PNGs for both selections plus largest contrast | Rounded lower tab corners visible, no old clipped lower-shadow defect observed in selected pixels; PR17 code removes Z/animator |
| History rows/filter/footer | Normal/large/contrast history pixels, CallDirection tests | Icon+word pairing and spacing visible; clipped partial next row is scrolling content, not proof footer unreachable |
| Settings/noNotif permissions | Largest noNotif contrast bottom, Sound/Protection/picker pixels | Disclosure/footer visible in selected render; dynamic unknown/disabled announcements unverified |
| PIN/device credential | PinGate fixture image + gate/protection regression sources | Large keypad and recovery visible; system credential interaction remains outside render evidence |

Coverage status is intentionally conservative: “reviewed with evidence” does not mean every runtime permutation passed.

| Assignment section | Status | Evidence and principal limit |
| --- | --- | --- |
| A Functional/safety | Partially reviewed | All navigation/call/session branches traced, P1/P3; Android lifecycle/touch permission dialogs not run |
| B Contacts/photos/concurrency | Reviewed with evidence | Load/group/match/cache/ownership traced; F02-F04; real provider timing/decoder behavior unmeasured |
| B2 Performance/resources/package | Partially reviewed | Source workload/bounds and exact source sizes; no APK/profiler/runtime available |
| C History/notifications | Partially reviewed | Repository/controller/reconciliation/listener/overlay/guards and tests read; merged manifests/OEM flags not rerun |
| D Accessibility/Arabic/design | Partially reviewed | Resource inventory,16 pixel renders and test sources; no live TalkBack/Switch Access |
| E Speech/audio | Partially reviewed | Engine preference/voice/readiness/generation/shutdown paths traced; no binding/audio/offline engine run |
| F Security/privacy/settings | Partially reviewed | Manifest, local storage/PIN/session/intents/log/network boundaries; no dynamic security testing |
| G Build/release/tests/docs | Partially reviewed | Scripts/tests/docs/live workflow query; wrapper bootstrap blocked; Android checks unrun |
| H Dependencies/licenses | Partially reviewed | Direct pins/JAR checksum/fonts/notices/current primary advisories; resolved transitives, actual APK notices and full advisory closure unavailable |
| I Resilience/observability/integrity | Partially reviewed | Exceptions/unknown states/atomic writes/stale callbacks traced; storage-full/process-kill/slow OEM providers not exercised |
| J Compatibility/localization/maintainability | Partially reviewed | API guards/locale/bidi/portrait/package visibility/module seams reviewed; device/API/OEM matrix unrun |

## 7. Edge-case matrix

“Handled” here means source plus existing test assertions support the branch, not fresh Android execution.

| Case | Disposition | Evidence / remaining work |
| --- | --- | --- |
| Permission grant/Listen/Favorite/lookup starts call | Handled in inspected paths | Explicit action only; P1 grant control, DirectCallAction/flow tests |
| Empty call permission callback | Confirmed failure in isolated logic | F01; no OEM frequency claim |
| Multiple contact numbers/recreation | Handled | Visible choice/highlight saved; stale provider edits retain original visible snapshot |
| Short typed number appears inactive but acts | Confirmed handoff mismatch | F06; screen activation source-traced |
| Contacts provider empty vs null/error | Handled | Verified loader, catch/Retry; dedicated regression source |
| Slow contacts provider/repeated refresh | Partial | Generation protects UI; F03 underlying work unbounded |
| Permission revoked during read | Partial | Publication recheck; real process restart/revoke behavior unrun; reads/photo cache do not physically cancel |
| Corrupt/unreadable/changed photo | Partial | Fallback/subsample/stream close; F02/F04; EXIF orientation and peak memory not tested |
| Shared number/national-vs-international | Handled within region policy | E164 identity/ambiguity removal; SIM/network/locale fallback; no suffix-only exact claim |
| Service/pause/wait/vanity contact strings | Missing adversarial test | Grouper strips non-digits except leading plus for dedup; raw call values preserved. Distinct service strings can collide; validate policy before broadening normalization |
| Call log hidden/-1/-2/-3/malformed | Handled in history | Presentation + regex/global-number check; no claimed universal validation at every other call entry |
| Exact history number and optional identity | Handled/partial | Re-resolves current contacts, keeps clicked value; optional read may stall (F03); cached labels refreshed on next reload |
| Large pending set >10,000 | Handled unavailable | Rejects partial snapshot; no partial baseline/count |
| Pagination | Partial | Explicit +200 prefix reload and preserved anchor; no server-side LIMIT/keyset, no absolute display cap; very deep history work unmeasured |
| New call after pending snapshot | Handled locally | Snapshot precedes history, draw/focus acknowledgement, concurrent revision tests |
| System pending clear/deletion | Handled on refresh | Reconcile/prune, debounce200ms, no system writes |
| Revoke/regrant while process absent | Accepted limit | Cannot infer lost permission from current grant; not fixed by process restart |
| Restart after acknowledgement apply | Accepted durability limit | Async disk persistence can resurface badge; not a log write or false clearing of system state |
| noNotif stale access/store | Source-handled; binary unverified | Capability false, overlay, invalidation, masked dots; build/runtime tests blocked |
| full grouping/ranking/reconnect | Partial | Coalesced120ms complete snapshots, API26 ranking/current profile; live channel/OEM/work-profile behavior absent |
| Missing external handler/security denial | Partial | DirectCallAction/history handle exceptions; Phone app-settings, Settings app-info/TTS installer/browser/credential have uneven SecurityException/handler containment; no crash reproduced |
| Lock/unlock/recreation/process kill | Partial | Content pages opt in, pending saved, confirmation excluded, protected screen-off closes; OEM order and process-death state untested |
| Stale app install/uninstall | Partial | Stored choices retained, receiver rebuild, missing launch caught; restricted/disabled package launch races require runtime checks |
| No telephony/dual SIM/work profile | Missing device test | Phone app owns call/SIM choice; app has no custom Telecom role or SIM chooser; no broad tablet promise |
| Full storage/corrupt prefs/reset | Partial | Baseline checks commit return; other applies have no durable-success signal; typed getters have no corruption repair layer; destructive recovery not executed |
| Accessibility/keyboard/touch guard | Partial | performClick retained; pointer hold400ms, slide-off/cancel; no live traversal/multitouch/OEM result |

## 8. Accessibility and precise unperformed device checklist

No live TalkBack, Switch Access, keyboard, Accessibility Scanner or handset test was run. Static XML/lint/goldens cannot establish announcements, focus, roles or gesture usability [E7]. Contact tiles/fallback names may expose repeated text and text-based custom actions may expose less useful roles; these are **inspection targets, not confirmed screen-reader defects**. History owns a consolidated description and excludes its decorative child nodes; clock similarly consolidates time.

On a separately authorized synthetic-data device, record device model, Android/One UI, display size, actual font-scale policy, navigation mode, build/flavor, permission state and service versions:

1. Start Home with TalkBack. Traverse clock/date/guide/all tiles once; require full untruncated names and one useful announcement per actionable tile. Verify marker remains physically left and date/time changes make sense.
2. Open Phone. Read Contacts/History selected states, dialpad role/name and Back. Repeat with text choice1.5, high contrast, smallest usable height, gesture and three-button insets. Scroll first/last cards and reach all fixed controls.
3. With synthetic same/long/mixed-script names and multiple numbers, inspect contact tile focus, no-name fallback, photos/stars as decorative or meaningful as appropriate; hear full names and actual selected number. Ensure focus stays sensible when a number enables Call/Favorite.
4. Activate Listen, Favorite, Retry and permission grant through accessibility actions; assert none places a call. Stop before real call handoff or use an isolated non-carrier environment. Check touch guard OFF/ON, exploration versus activation, hold, slide-off, cancellation and rapid taps.
5. Exercise denied/blocked/loading/null/error/revoked contacts/history; require useful state-change announcements without repeated chatter and reachable recovery. Verify no invisible node traps.
6. History: inspect number/type/date reading order, international-number order, unknown/private rows, selection/filter changes, Load more and position preservation. Acknowledge only current completed focused draw; do not equate it with reading every record.
7. Navigate settings gate, keypad/backspace/errors/lockout, all sections, switches, engine chooser, reorder handle/tap-to-position, dialogs and Back using TalkBack, Switch Access and hardware Tab/Enter/D-pad. Confirm roles, selected/disabled states, focus restoration and accessible alternatives to dragging/long press.
8. Screen off while in settings/PIN recovery or external system screen; reject late results. Test ordinary departures and sanctioned excursions separately, including browser-to-another-app limit. Do not change the production handset.
9. With owner-approved engine on test device and network disabled, audibly verify Arabic names, volume, pronunciation and failure/retry. Locale metadata and onDone are insufficient. Do not download models or disclose real contact names.

## 9. Security, privacy, build/update, policy, dependencies and licenses

### Application boundaries and storage

No runtime third-party library dependency beyond the internal UI module, network client/WebView, analytics, dynamic code loader, contact/log write, notification cancel/PendingIntent execution or message-extras parsing was found in production source. This is a source assessment, not proof about separately installed TTS/browser/system apps. Main's exported HOME entry does not consume external call payloads; internal call/settings components are non-exported. Query visibility declarations target launchable apps/TTS and named packages, not QUERY_ALL_PACKAGES.

PIN uses random16-byte salt, algorithm-tagged PBKDF2 (12,000 iterations,256 bits, SHA256 with SHA1 fallback), constant-time derived-value comparison, legacy migration and persisted progressive lockout30s to10min. Four digits and app-private preferences provide a convenience barrier only. KDF work is on UI thread; measure low-end cost before tuning. Wall-clock lockout can move with system time; it is not tamper-proof. Session validity is process-local, rechecked on resume and screen-off; browser/system excursions have the known exception below. Screen capture/overlays are not actively hardened with a secure-window/touch-obscuration policy; no tapjacking exploit was demonstrated. Do not turn that absence into a penetration-test verdict.

`pilot` contains setup, selections/order, favorite overrides and salted PIN state. `missed_review_v1` stores initialized plus `_ID:DATE:TYPE` acknowledgements, not phone numbers/names; no notification database. Local favorite keys do include normalized numbers and contact IDs in app-private storage. IDs/number changes can orphan favorite overrides; aggregation/ID reuse remains an unperformed integrity test. Clearing app data/uninstall loses PIN/preferences/overrides/acknowledgements, while system contacts remain; no destructive recovery was performed.

Backup is disabled and cloud/device-transfer rules exclude preference/file/database domains. Actual OEM transfer enforcement was not exercised. Baseline/pruning `commit()` is worker-owned and checks failure; foreground acknowledge uses immediate memory plus `apply()`, with an explicitly accepted crash-before-disk window. `reconcile` holds a class lock during commit; main-thread acknowledge/permissionLost can wait behind it under slow storage. Profile lock wait rather than declaring a measured deadlock. Multi-key app placement writes are atomic per editor; home-order and selected compatibility writes occur in separate edits. Full-storage/type-corruption/migration fuzzing is unperformed. No local diagnostics upload is proposed; any future support log must be opt-in, redacted, bounded and omit names/numbers/notification text/PIN.

### Update and distribution distinctions

Both flavors share the application ID. full16 cannot normally update an installed noNotif20; this is the **documented version-policy boundary**, not a newly found exploit. If the owner later requests a full candidate that updates noNotif20, it will need a higher appropriate version code, the correct signing certificate and compatible preferences, with owner-approved migration testing. This accepted limitation is not a reason to change full16’s version now. No keys/certificates from private storage were accessed; historical certificate/APK hashes in docs are not newly verified artifacts. Debug signing is not interchangeable with the dedicated release certificate. Neither flavor was packaged/installed here.

READ_CALL_LOG is dangerous and hard-restricted on modern Android: installer allowlisting and actual runtime grant are separate conditions [E2]. Home role is not default Phone/Assistant. Google Play restricts call-log access to qualifying handlers or approved exceptions [E3]; this source's convenience history does not itself establish eligibility. Optional use/read-only behavior/boolean dots do not waive those requirements. noNotif reduces notification capability but retains call-log permission. No bypass, fake role, accessibility workaround, hidden API, root or installer-source trick is recommended.

As checked on the audit date, the official Play requirements describe API36 for ordinary new app/update submissions from August31,2026 and API35 for broad existing-app availability, subject to the stated device categories/exceptions [E4]. The app targets33, so **current source is not ready for an ordinary new Play submission**. This does not prevent a compatible authorized private install, establish Play Protect classification, or explain a specific Play Protect warning. No store approval/rejection guarantee is made.

Notification listener guidance [E8] confirms low-RAM devices on AndroidQ/API29 and below cannot bind a listener; `NotificationAccess.unsupported` checks that flag/range. Memory capacity/model name alone does not prove `isLowRamDevice()`. Work-profile listeners are ignored and policy may restrict profile-originated notifications. On API30/Android11 this particular Q-and-below restriction is not a blanket prohibition, but actual device grants/binding remain unverified. noNotif avoids this listener path entirely.

### Dependency/supply-chain inventory

| Category | Declared pin/provenance | Verification and limit |
| --- | --- | --- |
| Runtime | Android framework + internal `:ui` | No external runtime dependency declared; actual DEX tree unavailable |
| Build | Gradle7.6.4 wrapper, AGP7.4.2, Paparazzi plugin1.2.0 | Versions fixed, google()/mavenCentral(); Wrapper JAR checksum matched official [E6]; no distribution checksum enforcement |
| App tests | JUnit4.13.2, Robolectric4.11.1 | Direct pins; transitive Android-all/resources and dependencies not resolved here |
| UI tests | JUnit4.13.2 plus Paparazzi plugin/tooling | Test/render dependency, not a shipped runtime model |
| Transitive surface | AGP, Gradle, Robolectric and Paparazzi artifacts | No resolved dependency graph/cache; exact transitive versions, hashes and comprehensive advisories unresolved |
| Bundled assets | Cairo400/700 TTF, vector drawables, OFL asset | Sizes/hash/provenance metadata inspected; no bundled speech model or raster contact fixture in runtime resources |
| External execution | Gradle distribution/repos; separate installed TTS engines/browser | Build dependencies and external apps are distinct trust boundaries; no new scanner/service used |

Official AGP7.4 requirements include JDK11/Gradle7.5 minimum and Build Tools30.0.3 [E9]. Java8 source compatibility does not imply Gradle7.6.4 supports running on Java21. No dependencies/SDK/JDK were upgraded or checks disabled.

Current primary advisory applicability, checked 2026-11-11 and supplemented in this report-only follow-up: Gradle9.9.1 release notes list **three**, not two, security advisories [E15].

| Advisory | Upstream range / prerequisite | Project-specific conclusion |
| --- | --- | --- |
| GHSA-3cfg-jpcr-gfp3 (upstream Critical) [E10] | Gradle7.0–8.14.6,9.0–9.9.0; malicious version-catalog value compiled into accessors; fixed8.14.7/9.9.1 | Pinned7.6.4 is in range. No version catalog/import/settings catalog block in audited tree; triggering path not established. Build-time concern, no Android runtime exposure proven |
| GHSA-gqrr-4j89-5267 (upstream High) [E11] | Gradle≤8.14.6 or9.0–9.9.0; crafted daemon registry/redirected registry path; fixed8.14.7/9.9.1 | Version in range, but no `org.gradle.daemon.registry.base` or committed registry.bin found. `daemon=false` is not asserted as a security fix. No malicious endpoint/registry or exploit tested |
| GHSA-pcp8-98xm-c6pr (upstream High), published2026-11-10 [E14] | Gradle≤8.14.6 or9.0–9.9.0; stale daemon entry plus a co-located local attacker binding its freed loopback port; fixed8.14.7/9.9.1 | Pinned7.6.4 is affected tooling. Prerequisites depend on build-host state, not catalog files or a project-supplied registry; repository absence cannot exclude them. Host conditions/exploitation unverified; not an installed-APK vulnerability |

For GHSA-pcp8-98xm-c6pr, an abnormal daemon exit can leave its port in the registry. A local attacker binding that port can receive build requests and return hostile replies. Shared/multi-user build hosts are especially exposed; no build-host forensic test was performed here. Mitigation pending upgrade: trusted local access and isolated, single-tenant ephemeral runners with fresh daemon state, avoiding shared hosts. These are proposed safeguards, not changes made in this audit. `org.gradle.daemon=false` is not treated as a verified mitigation. Any upgrade requires a separate reviewable PR and AGP/Paparazzi/JDK compatibility validation [E14].

These are three conditional upstream vulnerabilities in affected build tooling, not three confirmed project exploits, and are not counted as Critical/High application findings. No CVE identifier is invented where sources provide GHSA identifiers. Gradle7.6.4 release notes describe older fixes [E12], not current security clearance. The full Gradle/AGP/test-transitive advisory set and actual build-host exposure remain unresolved. Neither absent repository triggers for the first two cases nor an uninspected daemon registry establishes absence of past exploitation.

### License/provenance status

No project-wide code license is present; root README acknowledges that. Public visibility is not a broader reuse license. Do not invent one or accept legal terms for the owner.

Cairo TTFs are91,500 and91,664 bytes; metadata includes OFL URL. Full OFL1.1/Cairo attribution is shipped in `ui/src/main/assets/OFL.cairo.txt` and NOTICE references it. No exact upstream font revision/hash provenance is recorded in the repository. The two weights have different hashes, not duplicate assets. NOTICE attributes named Material vectors to Apache2.0/Google, but no full Apache license text or comprehensive mapping of every icon path was found among runtime assets. Actual APK notices/distribution obligations and original-icon provenance need review; this is unresolved evidence, not a legal determination of infringement. Test tooling notices are not automatically runtime attribution obligations.

## 10. Performance, resource use and package size

### Measured or directly counted here

No startup time, frame/jank percentage, CPU, battery draw, retained/peak heap, ANR rate or APK/Dex metric was measured. No emulator or handset was available. Local Java probe durations are **not app performance measurements**.

| Observable | Current result | Interpretation |
| --- | --- | --- |
| UI source resource bytes | 293,562 bytes (sum of files under `ui/src/main/res`) | Source bytes, not compiled/download/installed size |
| Bundled fonts | 183,164 bytes total; Cairo40091,500 +70091,664 | About62.4% of those source resource bytes; different weight files, not duplicates |
| Goldens | 106 committed PNGs under test snapshots | Test assets, not evidence of production APK bloat |
| Bitmap LRU | Source cap6MiB per loader | Excludes transient decode/scaling and view-retained bitmaps; no process-wide cap |
| Call-log display | Source starts200 rows; +200 prefix each Load more | App iteration bound at current limit; provider query has no SQL LIMIT; very deep prefixes are not globally capped |
| Pending missed snapshot | Source max10,000 distinct identities,5s read budget; UI timeout6s | Time checks between cursor moves and after read cannot preempt an uncooperative provider |
| Event coalescing | Call-log invalidation200ms; notification snapshots120ms | Source settings, not observed power numbers |

Historical candidate document reports a257,829-byte signed noNotif20 APK. **Not independently rebuilt/measured here**; do not compare this with source resource bytes or a nonexistent current debug APK. Composition, ZIP compression/duplicates, DEX methods/classes, debug/release difference, stripping/unused resources and installed footprint remain blocked. Follow-up: ImgBot PR16 is now merged at `f1a6ff9059863725df2fa50a656c6355c94954a1`. Compared with the audit base, only16 test snapshot PNGs changed; this follow-up independently compared decoded RGBA dimensions/pixels and found them identical. No production source changed. The audit remains pinned to564f455; the merge was not incorporated into this report branch. These fixture-file savings are neither APK-size savings nor runtime-performance improvements.

### Source-supported risks and safeguards

- F02/F03/F04 are the main likely low-end hot paths. Main also reconstructs all tiles and requests package labels/icons on every resume/package broadcast (`MainActivity:115-176`); `UiBinder:182-221` fits words and measures twice for deliberate equal heights. Do not remove this safety/readability behavior for a speculative speedup.
- Phone reads/sorts entire contacts on resume even if History is selected. History builds a full contact index and repeated prefix pages; dialpad copies contact snapshots and queues matching on each input. Generation counters stop obsolete publication/work when reached, not unbounded queue retention while an earlier call is stuck. Profile before adding elaborate caches.
- No app jobs, alarms, wake locks, polling foreground service or network sync found. Home clock schedules30s only while resumed and unregisters time receivers on pause; Apps inherits scheduling despite updateClock returning early for secondary pages (minor inefficiency, not a release finding).
- Full listener remains system-bound and schedules a complete active-notification scan for matching events/ranking changes even when UI is hidden. It filters configured packages/profile and avoids content retention, but it is not foreground-only battery behavior. noNotif removes that service registration. Test bursts/idle/Doze without promising battery figures.
- TTS confirmation stops/shuts down on pause, generation-guards stale completion and reinitializes on return without auto-repeating saved speech. Settings initializes TTS and app discovery even at its landing screen, and keeps TTS until destruction rather than stopping on every pause; stale/continued audio during allowed external excursions is an unperformed test. No measured leak is asserted.
- Snapshot acknowledgement's shared lock plus disk commit, UI PBKDF2, SharedPreferences apply flush-at-lifecycle, package manager lookups and bitmap work are StrictMode/trace targets. No StrictMode violation or deadlock was actually observed.
- Subsampling limits decode dimensions but large/corrupt/rotated images, format changes between bounds/pixel passes, unbounded negative cache and storage/heap pressure need measurements. Cache mutation is currently UI-owned; an async implementation must introduce explicit synchronization/ownership rather than reuse HashSet unsafely.

### Reproducible unperformed measurement plan

Use a separately authorized emulator with synthetic data first; later validate on an authorized non-production A10s. Record actual OS/One UI/API, `isLowRamDevice`, RAM/heap class, display/density, build/flavor/compiler/signature, thermal/battery-saver state and tool versions. Emulator results must remain labelled emulator results.

Create workload sets: 50/500/2,000 contacts with one and multiple numbers,20/200/2,000/10,001 pending/history records as appropriate; no photos versus thumbnail/large12MP/corrupt/unreadable/rotated images; long Arabic/Latin labels, app fonts1/1.25/1.5 and contrast. Use a controlled delayed/failing provider for timeout/retry; no real personal provider data. No profiling libraries/modules need be committed.

Run at least five repeated trials per scenario after a documented reset/cache condition. Cold: process absent (test environment only), launch to first usable Home. Warm: activity recreation with process warm. Hot: background/resume. Measure first/repeated Phone/Contacts/History/Dialpad/confirmation with Perfetto/Android Studio startup/CPU traces; separate TTS binding/provider time from drawing. Never call the carrier.

Collect frame timing through repeatable fling/navigation and maximum label scenarios; report distributions and workload, not a single frame or JVM timing. Capture peak and settled heap/native bitmap memory, live activities/threads/executors/cursors, cancellation after provider release and repeated50 navigation cycles. Inspect slow-storage lock waits and StrictMode main-thread provider/disk events. Test trim/low-memory/process death, storage-full and preference recovery in the isolated environment only. Compare battery-saver/Doze/screen-off for foreground observers and full listener, using traces/wakeup counts rather than fabricated mAh.

When a local debug/release comparison is available without release secrets, use APK Analyzer/aapt/apkanalyzer or ZIP/DEX equivalents for per-entry compressed/uncompressed sizes, font/resource reachability, methods and dependencies. Do not publish a new release or alter images to improve a chart. Select optimization only after baseline evidence, preserve safety/equal-height/RTL contracts, and rerun identical workloads.

## 11. Executed checks, test quality and blocked checks

### Actually executed

| Check | Method / result |
| --- | --- |
| Repository identity/branch | Clone, fetch origin main, rev-parse; clean initial tree; branch created at expected SHA; success |
| Inventory/manual review | 55 production Java files,58 test-source Java files, resource/build/manifest/docs review; source counts not runtime coverage |
| XML parse | Python ElementTree parsed63 XML files under app/ui sources; pass for well-formedness only, not Android linking/manifest merge |
| Wrapper bootstrap | `./gradlew --version`; exit1 before Gradle launch, distribution download network unreachable; not an application compile failure |
| Java probes P1/P2/P3 | Exact copies of DirectCallAction/PinLock/CallDecision/AppSelection;15 assertions passed using synthetic stubs; details below |
| Font/resource inventory | Python/fontTools plus byte counts; two font hashes/metadata and source sizes inspected; no fonts changed |
| Wrapper checksum | sha256sum matched official7.6.4 Wrapper JAR [E6]; distribution itself not downloaded/validated |
| Pixel review | 16 committed synthetic PNGs inspected as contact sheets; no renderer/verify/record run; no files changed |
| Live GitHub | Base workflow-runs query empty, combined statuses empty; repository has no tracked Actions workflow; no Android CI result claimed |
| External requirements | Official Android/Play/Gradle pages fetched2026-11-11; application of sources bounded in section9 |
| Final scope/whitespace | `git diff --check` and staged/base diff restricted to this one Markdown report; final verification recorded before commit/push |

Probe reproduction method: files were copied byte-for-byte into a sibling temporary source tree. Minimal fake Activity supplies denied/granted permission, false rationale, a boolean SharedPreferences store and request/handoff counters; fake DialIntents returns a local object. Toast/resources/manifest/PackageManager are inert stubs. The real DirectCallAction implementation was not rewritten. Four pure/current production classes were compiled using the installed compiler module, then executed:

```sh
java -m jdk.compiler/com.sun.tools.javac.Main -d classes <production-copies-and-stubs.java>
java -cp classes info.fadl.mamalauncher.AuditProbe
```

Initial `javac` invocation failed because executable absent; `--release 8` also failed because the runtime image lacks that release's symbols. Retried with installed Java21 compiler module and its default target, solely for these small probes. This **does not validate the project's Java8 Android toolchain**. No SDK/dependency was installed or modified.

Core P1 sequence and observed values:

```java
// Synthetic activity: denied CALL_PHONE, false rationale, counters only.
action.tap("01000000001");                 // requests=1, calls=0
action.permissionResult(42, new int[0]);   // denied=true, calls=0
action.tap("01000000001");                 // requests=1, calls=0
```

P2 grant control: first tap requests; set stub current grant; grant callback yields zero calls; fresh tap yields one; immediate duplicate yields no second. Pure controls: correct/wrong PIN verification, lockout at4/5/10/100 failures, post-grant WAIT_FOR_EXPLICIT_TAP, and legacy Home order inclusion. P3: granted fake Activity plus `tap("7")` yields one synthetic handoff. Overall15 assertions passed. No actual Android Intent dispatcher, provider, permission dialog or carrier was involved. Temporary stubs/probes are deliberately not committed.

### Blocked / unrun, not failures

All Android tasks below are **unrun**, sharing the failed bootstrap and missing compatible JDK/SDK. Repeating identical wrapper downloads would not produce independent evidence. Task discovery was not claimed successful.

```sh
./gradlew :app:testNoNotifDebugUnitTest --max-workers=1
./gradlew :app:testFullDebugUnitTest --max-workers=1
./gradlew :ui:verifyPaparazziDebug --max-workers=1
./gradlew :app:lintNoNotifDebug :app:lintFullDebug --max-workers=1
./gradlew :ui:lintDebug --max-workers=1
./gradlew :app:assembleNoNotifDebug :app:assembleFullDebug --max-workers=1
./gradlew :app:tasks --all
# After task discovery: each flavor's manifest-processing task, inspect outputs.
```

No fresh Android pass/failure/skip/warning totals, APK signing result, Semgrep run, dependency resolution, frame trace, emulator or handset run is available. Candidate docs report noNotif278passed/12skipped, full281passed/9skipped, UI106passed; those are **historical statements, not this audit's execution**. Flavor-specific assumptions explain listener-only12 versus noNotif-only9 skipped executions (`NotificationListenerFlowTest`, `NoNotificationBuildTest`, SDK24/25/33 expansion); intentional cross-flavor skips are not successful behavioral tests. Older docs'206/88 or274/95 totals refer to earlier baselines.

Warning categories documented historically include portrait lock, API-dependent attributes, monochrome icon, custom touch accessibility, library unused resources and style suggestions. They were not rerun or recounted. HomeLabel currently has a narrowly explained WrongConstant suppression using an inlined compatible constant; old standalone UI lint failure is not asserted still present.

Test quality: meaningful controls exist for no-autocall, exact-number history, provider failure not empty, deny/cancel/retry, signal/generation staleness, pending snapshot ordering/revision, draw/focus acknowledgement, E164 ambiguity, short-height fixed keys, bidi glyph placement, tab animator reset and screen-off/session recovery. Negative controls are documented historically but were not rerun. `CallDecision` helper tests alone do not establish DirectCallAction behavior; P1 independently exercises it. Several provider/lifecycle tests settle with fixed sleeps/polling (`ContactsFailureTest:8`, `CallLogConcurrencyTest:85,97`, HistoryFlow/ReviewRegression settle loops); timeout tests use synthetic looper time, not indefinitely unresponsive binder cancellation. Prefer latches/barriers for future concurrency regressions. No `androidTest` suite or real system/OEM permission integration was found. Coverage percentages/test counts cannot close those gaps.

Pixel ledger: inspected exact committed snapshot basenames below, under `ui/src/test/snapshots/images/`. These are existing layoutlib outputs, not fresh runtime captures; date text in a fixture is not handset evidence.

- `info.fadl.mamalauncher.ui_CallDirectionRenderTest_historyScreenHasBreathingRoom_history-arrows-spacing-normal.png`
- `info.fadl.mamalauncher.ui_CallDirectionRenderTest_contactsTabInactiveHistoryBottomEdge_phone-tabs-contacts-selected.png`
- `info.fadl.mamalauncher.ui_CallDirectionLargeNormalRenderTest_historyScreenLargestFontNormal_history-arrows-spacing-largest-normal.png`
- `info.fadl.mamalauncher.ui_CallDirectionLargeNormalRenderTest_contactsTabLargestFontBottomEdge_phone-tabs-contacts-selected-largest.png`
- `info.fadl.mamalauncher.ui_CallDirectionLargeRenderTest_historyScreenLargestFontContrast_history-arrows-spacing-largest.png`
- `info.fadl.mamalauncher.ui_NoNotificationLargeRenderTest_disclosureAtLargestTextContrastScrollsToBottom_no-notification-largest-contrast-bottom.png`
- `info.fadl.mamalauncher.ui_UiSnapshotTest_confirmSingleNumber_confirm-single.png`
- `info.fadl.mamalauncher.ui_RuntimeContrastTest_everydayHomeHighContrast_everyday-home-contrast.png`
- `info.fadl.mamalauncher.ui_PinGateFitTest_homeWithAppsPageLargestText_home-with-apps-page-largest.png`
- `info.fadl.mamalauncher.ui_PinGateFitTest_settingsSoundLargestText_settings-sound.png`
- `info.fadl.mamalauncher.ui_PinGateFitTest_settingsProtectionLargestText_settings-protection.png`
- `info.fadl.mamalauncher.ui_PinGateFitTest_settingsAppsTop_settings-apps-top.png`
- `info.fadl.mamalauncher.ui_StableDialpadRenderTest_beforeSuggestion_keys-before-suggestion.png`
- `info.fadl.mamalauncher.ui_StableDialpadRenderTest_groupedLocalSuggestion_keys-grouped-local-suggestion.png`
- `info.fadl.mamalauncher.ui_ExpandedRenderTest_speechOptionsInitialContrastIsVisibleAndOpensRecovery_speech-options-initial-contrast.png`
- `info.fadl.mamalauncher.ui_UiSnapshotTest_pinGateDeviceUnlockOffered_pin-gate-device-unlock.png`

## 12. Good safeguards to preserve

- Explicit call dispatch, current permission check, visible multi-number choice, pending-tap suppression; no callback/Listen/Favorite implicit calling.
- Exact clicked history number retained even if current identity resolution changes; private/sentinel records cannot call through history; unknown contacts cannot create favorites.
- Cursor/stream cleanup and completed-read-versus-empty semantics; revoke/lifecycle/generation checks and visible retry paths.
- Full pending snapshot independent of200-row display, identities-only acknowledgement, revision guard against old pruning, focus/draw/lifecycle checks; no system flag writes or notification cancellation.
- Real noNotif capability guard plus removal overlay, distinct unknown/disconnected/ready states in full, filtered current-profile metadata without message content retention.
- Bound bitmap cache/subsampling, recycled image fallback, semantic contrast colors, physical clock/call-direction rules, equal-height readable cards and footer-first reachability.
- Offline-reported Arabic voice selection, explicit engine preference before app-managed fallback, generation/retry/shutdown; no bundled cloud model. Actual engine fidelity/audibility remains separate.
- Salted algorithm-tagged PIN/legacy compatibility, progressive lockout, process-local session, screen-off and stale credential rejection, backups excluded.
- Pure logic seams and adversarial provider/coordinate/golden tests. Keep the existing UI and preference contracts while improving ownership; Compose/Kotlin/MVVM/DI is not a prerequisite.

## 13. Prioritized roadmap (recommendations only)

| Priority | Work | Effort / risk | Gate |
| --- | --- | --- | --- |
| Immediate small fix proposal | F01 interrupted call-permission handling | Small / low-to-medium | Actual activity regressions API24/30/33, no callback call, reviewed separately |
| Immediate validation | Restore capable isolated runner; run all section11 tasks/merged manifests | Small-to-medium / low app risk | Fresh outputs/counts, intended flavor skips, no changed goldens |
| Build follow-up | F05 CI/checksum/verification; assess shared-host stale-daemon risk, propose isolated ephemeral runners and a compatible security-toolchain upgrade | Medium / medium | Official checksum, AGP/Paparazzi/JDK compatibility, controlled separate PR; no host/toolchain change authorized here |
| Small follow-ups | F06 clarify short input action; F07 reconcile entry-point docs | Small / low-to-medium | Owner product decision for short codes, enabled/visual/a11y consistency; source/flavor table review |
| Measurement first | F02/F03 workload traces, large photos/provider stalls/repeated navigation | Medium / low evidence risk | Record source/API/build/workload, then choose targeted change |
| Targeted implementation later | Bounded contacts ownership; cancellable image pipeline with F04 invalidation | Medium / medium concurrency risk | Latch-driven stale/revoke tests, correct identity, optional-history degradation, repeat measurements |
| Acceptance evidence | Section8 accessibility/audio/device matrix | Medium / device-dependent | Separately authorized synthetic device; no production permission/default/storage changes |
| Optional later | Extract contacts/provider and settings sound controller seams; investigate full listener energy/deep pagination if measured | Medium / medium | Preserve preference/session/safety/RTL behavior; no rewrite for style |

No recommendation is implemented by this report. Proposed fixes belong in separate reviewable PRs. noNotif20 remains listener-free; full16’s update limitation remains accepted without a version change now. Call callbacks never place calls and call-log access stays read-only. No rewrite, targetSdk bump, signing/distribution change or Drive upload is authorized by this audit. Real handset/TalkBack/audio and any test-device permission remain separate decisions. Release/Drive work begins only after the owner asks, not as a consequence of merging documentation.

## 14. Accepted boundaries, open questions and limits

Accepted/documented: full16 versus noNotif20 update direction; portrait baseline; Home-after-lock defaultON for opted-in content pages, confirmation excluded; total pending acknowledgement on successful focused History draw even beyond the visible200-row page; asynchronous acknowledgement may resurface after process termination; unobserved revoke/regrant cannot be inferred; external-system/browser session exception. None is relabelled as a new exploit.

Browser exception precisely: Settings sets `externalInFlight` before developer-site launch. Leaving that browser for another app can keep the memory-only settings session unlocked until Settings resumes/clears the exception, screen-off locks it, or process ends. Returning to Settings clears the exception, not the unlocked session; next ordinary departure locks. Current comments promising a narrower system-only single excursion are not a stronger enforced boundary.

Open questions: actual A10s OS/One UI and low-RAM flag; whether candidate20 has been accepted on handset; intended short-code policy and app-vs-system font policy; current distribution goal/Play exception eligibility; license choice and full icon provenance; resolved transitive vulnerabilities; real offline Arabic output; storage-full/ID-reuse durability behavior. Do not infer answers from device model, historical reports, locale labels or bot approvals.

Highest-impact untested paths: Android permission interruption/revoke under real lifecycle, merged flavor manifests, provider/Contacts hangs, sustained photo navigation/heap pressure, system pending-call flags, full listener reconnect/ranking/work-profile behavior, live accessibility traversal and audible speech, update continuity on an isolated test installation. No finite source audit eliminates all edge cases.

Final pre-commit main freshness: fetched again at 2026-11-11T18:41:06+02:00; origin/main remains `564f4556841032ba95f29c6fb3fca0e1fd5fce71`. No intervening main changes required re-audit. Conclusions remain pinned to this SHA; later changes are outside this report.

Report-only follow-up, 2026-11-11: fetched main is now `f1a6ff9059863725df2fa50a656c6355c94954a1` after ImgBot16. Tree comparison found only16 recompressed test PNGs and independently verified identical decoded pixels, with no production-source change. This is a bounded fixture comparison, not a re-audit/rebase or a new Android/OEM/performance result. Original dated audit results above and older audit documents remain unchanged; the follow-up only supplements advisory applicability, clarifies finding provenance/policy and records this main movement.

## 15. Source ledger

Repository evidence is [commit-pinned tree](https://github.com/FADL285/mama-launcher-accessible/tree/564f4556841032ba95f29c6fb3fca0e1fd5fce71), exact finding links above, tests/resources listed in sections6/11, and historical guides listed in section2. No other model's new audit or paid bot review was used. The follow-up's separate ImgBot fixture-only comparison is recorded in sections10/14; audit conclusions remain pinned to564f455. GitHub live workflow/status queries support only the limited CI observation stated above.

Primary external sources fetched2026-11-11; paraphrased for the stated applicability, not OEM or legal certification:

| Ref | Official source | Use / limitation |
| --- | --- | --- |
| E1 | [Activity permission callback](https://developer.android.com/reference/android/app/Activity#onRequestPermissionsResult(int,%20java.lang.String[],%20int[])) and [runtime permission guidance](https://developer.android.com/training/permissions/requesting) | Empty/interrupted result and contextual grants; no Samsung dialog guarantee |
| E2 | [Manifest READ_CALL_LOG](https://developer.android.com/reference/android/Manifest.permission#READ_CALL_LOG) | Dangerous/hard-restricted permission and installer allowlist; current grant still authoritative |
| E3 | [Play SMS/Call Log policy](https://support.google.com/googleplay/android-developer/answer/10208820) | Default handlers/exception review, not automatic eligibility for a launcher |
| E4 | [Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878) | Audit-date submission/availability requirements; distinct from private installation |
| E5 | [Android large bitmap loading](https://developer.android.com/topic/performance/graphics/load-bitmap) | Subsampling/memory reasoning; no app benchmark inferred |
| E6 | [Gradle official checksums](https://gradle.org/release-checksums/) and [Wrapper verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification) | Wrapper JAR match and missing distribution enforcement; not compromise proof |
| E7 | [Android Views accessibility testing](https://developer.android.com/guide/topics/ui/accessibility/views/testing-views) | Tool limitations and need for device interaction |
| E8 | [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) | Binding, low-RAM Q-and-below and work-profile limits; no future/OEM guarantee |
| E9 | [AGP7.4 compatibility](https://developer.android.com/build/releases/agp-7-4-0-release-notes) | Pinned toolchain requirements; not approval to upgrade |
| E10 | [Gradle version-catalog advisory](https://github.com/gradle/gradle/security/advisories/GHSA-3cfg-jpcr-gfp3) | Affected range/attack inputs/fixed releases; no catalog trigger found here |
| E11 | [Gradle daemon-registry advisory](https://github.com/gradle/gradle/security/advisories/GHSA-gqrr-4j89-5267) | Affected range/attack inputs/fixed releases; no supplied registry trigger found here |
| E12 | [Gradle7.6.4 release notes](https://docs.gradle.org/7.6.4/release-notes.html) | Historical fixes, not comprehensive current security clearance |
| E13 | [TextToSpeech API](https://developer.android.com/reference/android/speech/tts/TextToSpeech) | Asynchronous synthesis, engine/locale semantics; installed engine/offline metadata is not audible or independent privacy proof |
| E14 | [Gradle stale-daemon port-squatting advisory](https://github.com/gradle/gradle/security/advisories/GHSA-pcp8-98xm-c6pr) | Follow-up checked2026-11-11: affected tooling, host prerequisites, shared-host risk, isolation mitigation and fixed versions; no project exploitation established |
| E15 | [Gradle9.9.1 release notes](https://docs.gradle.org/9.9.1/release-notes.html) | Follow-up checked2026-11-11: all three current advisories; not a recommendation to adopt9.9.1 without compatibility validation |

This report's only repository change is this file. It does not certify an APK or apply any recommendation.
