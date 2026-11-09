# Dialer recognition and Egyptian speech options

Research checked 9 November 2026. Public sources only; all names and numbers in screenshots/tests are fictional.

## Recommendation

Recognize saved numbers locally as they are typed. Label partial matches as suggestions, not as the identity of the typed number. An exact match changes Save into View contact; selecting a suggestion opens confirmation, without altering the typed number or calling. Recheck the contacts provider at Save to avoid stale duplicate creation. Keep Call and Save fixed while suggestions/keypad scroll.

For this pilot, keep device speech offline. Prefer an installed Egyptian Arabic voice if the engine offers one, otherwise an installed Arabic voice. Do not describe generic Arabic as Egyptian, and do not promise better name pronunciation from a locale label. A separately bundled Egyptian model needs a measured Android prototype and a license/dependency review. Cloud synthesis is a separate consent, security and spending decision.

## Dialer findings and tradeoffs

- A Senior Tech Club walkthrough of Google Contacts describes showing Create new contact when a dialed number is not recognized, with Create new/Add to existing choices. This supports making Save conditional on whether the number is already known, rather than always opening a fresh form. It is a tutorial, not a current Google product specification.[1]
- A 2019 Google Pixel community discussion describes keypad matching and a Samsung comparison. It also reports different access paths for contacts with several numbers. This is historical user evidence, not proof of the current behavior of every Samsung or Pixel version.[2]
- Android documents PhoneLookup as an optimized number lookup URI.[3] That is useful for a direct exact-lookup implementation. This candidate instead reads contacts locally once for prefix search and re-reads the provider at Save, then uses strict normalized/E.164 equality. It does not rely on suffix-only equality or a provider null result.
- Android exposes country-aware E.164 formatting through PhoneNumberUtils.[4] The app uses network country, then locale country, without inventing a country when both are unknown. Formatting-equivalent digit strings still match. International/national equivalence may remain unrecognized when country information is unavailable; a handset retest must cover that case.
- One preview plus a results chooser keeps the keypad usable. Shared numbers retain every matching person. Only the user selects which person/number to open or call. No automatic contact writes are added.

## Egyptian voice options

| Option | Fit and limits | Decision for 0.7 |
| --- | --- | --- |
| Installed Android TTS voice | Android exposes locale, network requirement and not-installed features. The app can prefer offline ar-EG if actually offered. Availability and pronunciation depend on the installed engine/data.[5][6] | Use now, with offline-only checks. No Egyptian pack is bundled or promised. |
| NAMAA-Egyptian-TTS | Its model card says MIT, local/hosted inference and Python Chatterbox usage. It warns about pronunciation of ق, numbers and prompt consistency. No verified Samsung A10s benchmark or drop-in Android TTS installer was established.[7] | Candidate for a separate local prototype, not a promised replacement. Dependency/provenance review still needed. |
| F5 Egyptian 5K checkpoint | Model card says CC BY-NC 4.0, incomplete training and research rather than production quality.[8] | Not selected for the pilot. |
| VoiceTut GGUF | Egyptian model exists, but the card lists separate base-model noncommercial and tokenizer terms despite an Apache 2 fine-tune. Examples target audio.cpp/GPU usage; handset performance was not established.[9] | Not selected as a drop-in redistributable Android pack. |
| Piper Kareem | Model card identifies ar-JO, not Egyptian; dataset license is referred elsewhere.[10] | Do not relabel it Egyptian or bundle without reviewing all terms. |
| Microsoft Salma/Shakir | Microsoft lists ar-EG-SalmaNeural and ar-EG-ShakirNeural. Cloud synthesis would send text to a service. Embedded Speech is a separate limited-access process, even though Android support exists.[11][12] | No account, API, upload or purchase added. Cloud/embedded licensing and cost must be checked for an approved future design. |

A family-recorded name stored locally is another way to control pronunciation exactly, but recording/import UI, consent and per-contact storage are a separate feature, not part of this candidate.

## First-use speech investigation

Manual engine selection making speech work does not establish one root cause. The earlier package-visibility defect was already fixed in main. This round addresses additional code-level failure paths: unset/default engine discovery, queued initialization callback ordering, fallback only before an explicit user engine choice, initialization timeout, stale callbacks, offline voice selection and an explicit retry from Listen. The settings label names the requested engine: Android can fall back internally, and the app does not claim it can publicly verify the active-engine identity. Automated tests verify these paths with fake engines; they cannot establish audible sound or name pronunciation on the handset.

## Sources

1. Senior Tech Club, tutorial, no visible publication date: https://seniortechclub.com/android/adding-and-finding-people-in-the-google-contacts-app/
2. Google Pixel Community, user discussion, 21 November 2019: https://support.google.com/pixelphone/thread/20732138/how-do-i-get-the-phone-app-to-display-all-numbers-for-a-contact-from-keypad?hl=en
3. Android Developers, official API: https://developer.android.com/reference/android/provider/ContactsContract.PhoneLookup
4. Android Developers, official API: https://developer.android.com/reference/android/telephony/PhoneNumberUtils
5. Android Developers, official API: https://developer.android.com/reference/android/speech/tts/TextToSpeech
6. Android Developers, official API: https://developer.android.com/reference/android/speech/tts/Voice
7. NAMAA model author card: https://huggingface.co/NAMAA-Space/NAMAA-Egyptian-TTS
8. F5 model author card: https://huggingface.co/MAdel121/f5-tts-egyptian-arabic/blob/main/README.md
9. VoiceTut conversion/model author card: https://huggingface.co/mohammedaly22/VoiceTut-TTS-GGUF
10. Piper model card: https://huggingface.co/rhasspy/piper-voices/blob/main/ar/ar_JO/kareem/medium/MODEL_CARD
11. Microsoft, official language/voice list: https://learn.microsoft.com/en-us/azure/ai-services/speech-service/language-support?tabs=tts
12. Microsoft, official embedded speech overview: https://learn.microsoft.com/en-us/azure/ai-services/speech-service/embedded-speech
