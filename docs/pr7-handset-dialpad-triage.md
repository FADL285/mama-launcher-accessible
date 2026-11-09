# Stable dialpad and LTR phone text

Based on merged main d3052e386643fcc18362813d6c52c51b6560ca7c, tree
6c60d2cc0a2565bad684488e683f478ad0bc4ba1. Synthetic fixtures only.

## Handset issues independently confirmed

1. Contact number groups inherited RTL context from the Arabic preview. The
   display formatter now wraps only the phone number in an isolated LTR
   override, including grouped Arabic digits. Preview and all-results chooser
   share it. Raw contact numbers and call/save values are never rewritten.
   Typed-number display explicitly uses LTR. Arabic names remain RTL.
2. Suggestions were inserted above keys in the same scrolling content, moving
   the key hit targets. Keys now sit outside the results ScrollView, directly
   above fixed Call/Save. Only the available results area can scroll. Number
   display has a stable height. Save-label auto sizing cannot change row size.

## Tests

- Coordinate dispatch, not just performClick: all twelve key rectangles are
  captured before results. While a touch is DOWN on 1, a blocked contact read
  finishes and adds a long contact preview plus more-results. UP at the old
  coordinate and six repeated taps still append 1, never open confirmation.
- Tests cover normal font, largest font plus high contrast, loading, results
  appearing and disappearing. The suggestion area remains above the keypad.
- A local negative-control run temporarily put keys back inside results scroll;
  both coordinate tests failed on changed key rectangles. The fixed layout was
  restored before final validation and publication.
- Shared-renderer assertions verify ascending glyph positions for local,
  international, 00-prefix and grouped Arabic digit strings in previews and
  chooser labels. Raw number data stays byte-identical.
- Largest-text normal/contrast renders cover no matches, loading, errors,
  suggestion plus more-results and exact saved matches. Existing accepted
  contact photo, Apps page and speech behavior are unchanged.

No real calls, contact writes, external TTS uploads, private handset media or
real contact identifiers are used. Version 0.10/code12 is an update candidate
signed with the same pilot certificate. Merge and supervised handset approval
remain with the owner. Local tests do not guarantee handset touch/audio quality.

## Reviewer follow-up

Greptile's short-window finding is valid. Normal windows use the unchanged
bottom-anchored layout via layout-h600dp. Short windows keep Call/Save fixed,
a fixed-height results viewport, and a separately scrollable keypad. Results
cannot change the keypad viewport or move keys under an in-flight tap. A
480dp/largest-text regression also scrolls down and dispatches a real coordinate
tap to 0, proving the lower keys and actions stay reachable. Three short-window
renders show empty/results at the same coordinates and the bottom keys after
scrolling. On short windows the user must scroll keys/results separately.

Greptile's chooser-test finding is valid. The production dialog now uses an
adapter whose item view is shared with renderer tests, not a substitute Button.
The exact TextView is rendered at a narrow 288dp dialog-item width with largest
font, including grouped Latin/Arabic digits. No full-screen-width assumption.
CodeRabbit's initial pass had no functional inline findings; it warned about
0% docstrings in touched complex functions. Its README-count note is corrected
by the verified test totals in this revision. Sourcery is budget-skipped, not
approved. Final review state is recorded separately after the last push.

## Requested developer attribution

Settings menu footer adds Developed by Mohamed Fadl with a fadl.info link. The HTTPS destination was fetched and showed Mohamed Fadl, Senior Software Engineer. Link activation is tested as ACTION_VIEW to the exact URL, with no-browser fallback. Largest-font normal and actual contrast-theme footer pixels inspected. No copyright year, new ownership claim or license change.

## Expanded handset follow-up, 0.11/code13

The current candidate also includes the user's later Home ordering, common
header, equal-card and speech setup requests. This section supersedes the
older statement that Apps/speech behavior was unchanged.

- Home order now includes Phone and the Apps folder alongside selected apps.
  Old app-only choices migrate without losing order. Unavailable packages and
  a temporarily hidden empty folder retain their saved slot. App-only order
  is kept in sync for backward compatibility. Special items cannot be moved
  into the Apps folder or hidden; each app still has independent placement.
- Phone, Dialpad, Apps and settings use the same compact fixed inline
  Back/title header. Long section titles wrap to two lines rather than
  silently shrinking. Primary call decisions stay large and fixed.
- Home/Apps cards have equal two-line end-ellipsized label areas. Full label
  remains the accessibility name, a tooltip on Android26+, and a long-press
  dialog. With touch guard enabled, a 1-second hold opens the full name;
  an ordinary guarded tap still launches the app. Android24 has a regression.
- Name speech separates engine initialization, ready, retryable failure,
  preparing data and needs-setup states. An unavailable engine does not
  become a permanent unsupported-device claim. The photo's Listen icon is
  shown only when ready; failure retains separate Retry and protected Setup
  actions. Retry never places a call. Returning from setup rechecks speech;
  backgrounding stops speech and discards callbacks from the old generation.
- A dismissible first-use guide explains long-pressing the clock and the PIN.
  Its sound shortcut goes through the existing PIN gate, including entering
  a new PIN twice on first setup. Sound settings repeat the guide on demand,
  offer explicit engine recheck, and ask the listener whether the test was
  actually heard. Engine completion alone is not human confirmation.
- Missing-browser handling clears the sanctioned-leave exemption before
  showing an error. Regression triggers the actual failing launch then
  leaves/resumes settings and verifies it is locked.
- Confirmation body and unboxed sound/setup text use active theme colors;
  white-backed number-choice buttons keep dark labels for their own surface.

Validation: 169 app tests and 78 UI test cases (77 snapshot images plus
one header-completeness assertion-only case). Full fresh verification is
required before reporting ready. New visual fixtures use the shared header
and speech-state renderer rather than substitute UI. They are layoutlib
renders, not handset/emulator screenshots. The handset retest still includes
speech audibility/pronunciation, touch/drag speed, settings lock and real call
behavior under supervision. No model, cloud speech or extra permission was
added. Public fixtures contain no handset contacts, photos, numbers or PIN.

### Speech recovery discoverability

At 150% text the initial confirmation screen cannot show the full photo, body and recovery buttons at once. A fixed, labeled 48dp-minimum Sound options button now reveals Retry/Setup without requiring discovery of scrolling. It appears only for retry/preparing/setup states and never calls, speaks or bypasses the PIN. Call/Back remain fixed. Initial/revealed contrast renders and visibility assertions pass. Physical swipes and hardware navigation remain unverified.

### 0.12/code14 official-guideline risk review

Android guidance calls for context-triggered permissions, graceful denial, labeled 48dp touch targets, manual accessibility testing, lifecycle-aware resource cleanup and explicit backup policy. Reviewed these against the source and existing regression suites, not as certification. The review found an unhandled contacts-provider failure; the failing reproduction and fix now cover failed/null reads, retry and late permission revocation. New failure UI is rendered at150% in contrast with fixed Back/Dialpad. Handset permission dialogs, TalkBack/Switch Access traversal, real audio/calls, OEM backup behavior, memory/jank and physical touches remain unverified. Landscape is intentionally restricted and the tested app font range stops at150%; broader device/accessibility certification is not claimed.

Official sources:
- https://developer.android.com/training/permissions/requesting
- https://developer.android.com/guide/topics/ui/accessibility/apps
- https://developer.android.com/guide/topics/ui/accessibility/principles
- https://developer.android.com/guide/topics/ui/accessibility/testing
- https://developer.android.com/guide/components/activities/activity-lifecycle
- https://developer.android.com/reference/android/speech/tts/TextToSpeech
- https://developer.android.com/identity/data/autobackup

### Clock period and lifecycle

The prior h:mm clock had no period marker. HomeClock now owns an explicit12-hour Arabic ص/م display and date formatting from a single instant/timezone. MainActivity owns only lifecycle/timer/broadcast refresh. Six new app tests cover noon/midnight boundaries, locale independence, timezone conversion, time/timezone broadcasts and background return across midnight. Four150%renders show11:59ص,12:00م,11:59م,12:00ص; boundary renders omit the fixed renderer status bar to avoid contradictory time fixtures. Existing1PM fixtures now read1:00م, matching13:00status. No claim of200%font/landscape/all-OEM/accessibility certification.
