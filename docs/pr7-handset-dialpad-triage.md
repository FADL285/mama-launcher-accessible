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
