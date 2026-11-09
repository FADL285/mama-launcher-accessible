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
