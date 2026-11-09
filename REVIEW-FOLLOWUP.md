# Follow-up review reconciliation

This record covers all 21 inline findings plus 3 minor and 3 test-quality notes
from PR #2's two-batch review. A partial first-batch count was previously
reported incorrectly as the full review. Review text is evidence to check,
not approval to merge.

| Finding | Current disposition |
|---|---|
| Guard slide-off/cancel | Fixed; now also cancels if pointer leaves then returns, without overwriting view tags. |
| Dialpad `+` input | Deferred functionality: no plus key exists. The in-app dialpad does not compose a leading plus. This is an implementation limitation, not a user refusal. |
| Repeat with missing Arabic | Fixed in prior merged round; no default-voice fallback. |
| Permanent contacts denial | Fixed in prior merged round; normal first request preserved, app-settings recovery afterward. |
| Recovery button after first wrong PIN | Fixed in prior merged round. |
| Duplicate settings after PIN change | Fixed in prior merged round using CLEAR_TOP/SINGLE_TOP. |
| Settings internal recreation relock | Fixed in prior merged round; session exception consumed on resume. |
| Failed TTS installer session exemption | Fixed in prior merged round; exception cleared on failure. |
| Multiple launcher activities per package | Bounded mitigation: picker now lists each app once with its package-level app label and default launch intent. Activity-specific selection is not offered or persisted. Existing package selections stay compatible. Exact component selection remains a documented limitation, not a completed component-storage migration. |
| Speech test with missing Arabic | Fixed in prior round; explicit test refreshes Arabic setup, never falls back to another language, and distinguishes unsupported/missing/pending. |
| Backspace accessibility labels | Fixed in prior merged round. |
| Guard accessibility activation | Fixed; ACTION_CLICK/keyboard activation retained while pointer requires hold. |
| Touch protection in phone flow | Fixed for actual contact adapter tiles, visible number choices and confirmation call button; debounce retained. |
| Dialpad recreation | Fixed; composed number saved/restored. |
| Home long-label/font sizing | Fixed; natural-height tiles with 170dp minimum, no two-line truncation. |
| Backgrounded change-PIN | Fixed; replacement canceled back to authentication on leave, stale intent fails closed. |
| Recovery canceled before replacement | Fixed; existing PIN retained until confirmed replacement. |
| SHA1 provider/OS upgrade | Fixed; algorithm-tagged new values and both legacy algorithms supported. |
| TTS health after installer | Updated: selected engine shared by installer/test/name speech, exact-engine installer, TTS instance rebuilt on return, old callbacks rejected. Engine-supported setLanguage provisioning and explicit pending/error feedback; no universal silent-install promise. |
| Home accessibility changes | Fixed; home requests recreation if font or contrast preference changed. |
| High-contrast surface text | Fixed for home header and dialpad surface while light keys keep dark text. |
| Stale app intent/package changes | Fixed; package-change receiver refreshes and launch race catches unavailable activity. |
| Stale discovery worker | Fixed; UI-thread completion, destroyed-screen guard and latest saved selection. |
| Missing dialer | Fixed; user feedback instead of crash. Contacts-insert unavailable also gives feedback. |
| Intent tests only comparing constants | Actual returned dial/insert Intent contracts now tested under Robolectric. |
| Empty huge-font home snapshot | Now populated with long-label tiles matching production natural-height constraints. |
| Contrast snapshot wrong theme | Uses runtime contrast theme and black surface. |

## Validation and limits

109 app unit/flow tests pass. 40 UI methods generate 40 layout-render snapshots
in verify mode; debug assemble passes. Tests inspect intents and never place
real calls. These counts exclude the independent manifest test in PR #3. The
combined supervised candidate adds that manifest test and must be tested again.

## November 9 handset feedback

- Per-number stars are app-local, opposite Listen. With multiple numbers, a
  visible number must be chosen first and a hint explains the disabled star.
  Any favorite number pins that person first, all phone contacts remain, and
  no favorite silently selects a call target. System-starred defaults seed
  local state; explicit local unstar overrides without WRITE_CONTACTS.


- Settings now opens a five-section menu; section/back navigation stays inside
  one authenticated Activity. Font/theme recreation retains the section.
- Green dialpad Call uses deliberate ACTION_CALL, shared with contact preview.
  Permission grant never calls; a fresh press is required and duplicate taps
  before return are suppressed. Contact preview/name step remains.
- Screen-off ends settings, first setup, PIN change and recovery to everyday
  home, clearing partial input and rejecting late credential/key callbacks.
- PIN/dialpad numeric grids are spatially LTR while Arabic text remains RTL.
- Home cards use natural label height, preserving the full package label.
  Multirow bottom/nav-boundary renders include the visible Internet label
  prefix and a longer stress label at enlarged text, without shortening names.
- Sound shows a selected-engine label and chooser. Installer, explicit test and
  name speech use the same preference, not a vendor hard-code. Removed explicit
  engines fail closed. Returning from the bound installer rebuilds the engine;
  prior callbacks cannot overwrite current state. Automatic voice provisioning
  is engine-dependent: setLanguage triggers supported downloads; unsupported,
  missing and not-installed-yet states stay distinct. A synthesis callback is
  not proof the user heard audio. PR3's package-visibility fix remains needed.
- Full-width actions replace cramped side-by-side PIN/test buttons.

Android sources:
https://developer.android.com/reference/android/speech/tts/TextToSpeech
https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine

Reproduced failing on the previously shipped implementation, passing after:
recovery abandoning replacement, backgrounded change-PIN, legacy SHA1
verification on modern provider, black-surface clock contrast, and delayed
picker ordering. Flow tests additionally cover guarded accessibility clicks,
short vs held call/number taps, slide-off-and-back cancellation, dial recreation,
missing dialer feedback, actual intent contracts, home contrast refresh and
TTS language-state refresh after returning from the installer.

Layout evidence is layoutlib, not device/emulator screenshots. Phone audio,
actual system credential UI, real call flow and touch/screen-reader experience
still require a supervised physical-device trial. These corrections do not
constitute production/security certification or daily-use readiness.

Latest dial follow-up: shorter separate 24sp empty prompt, standard spatial
LTR 123/456/789/*0#, and 0 with a small + hint. Short click appends 0;
long hold on an empty number adds +, consumes the click, and works with
physical touch guard enabled too. A + cannot be appended mid-number.
Contact detail keeps Call and Back fixed, with name/photo/star/number choices
in a scrollable region. Largest-text bottom fixture proves complete number
choices and fixed actions; scrolling can be needed to reach numbers.
