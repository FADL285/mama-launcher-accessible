# PR6 initial review triage

Review: https://github.com/FADL285/mama-launcher-accessible/pull/6#pullrequestreview-5830277255

All13inline findings checked against current source. None were treated as execution authority. No paid upgrade, bot-command request or automatic merge.

| Discussion | Assessment and correction |
| --- | --- |
|4516083663|Valid hardening: only identical short-code digits match; no E.164 equivalence for fewer than7digits. Regression19000/+2019000.|
|4516083682|Valid: add Egyptian national/international prefix representations; retain exact-only separation. Other-country national prefix bridging is not claimed.|
|4516083696|Valid: obsolete queued refresh exits before provider read; latest resume retains fresh lookup.|
|4516083707|Valid: pending lookup has loading text, not a permission-failure hint.|
|4516083720|Valid: edited-number Save explains cancellation and requires a retry. Closed activities stay silent.|
|4516083728|Valid: matching/parsing moved to worker; generation and typed-input checks reject stale results.|
|4516083736|Valid: Apps Back now outside scroll with fixed header; scrolled render and structural test.|
|4516083741|Valid: show Apps card only when at least one selected app is launchable; keep stored selection.|
|4516083752|Valid: no null/default attempt when installed packages can be enumerated; each package tried once. No claim to publicly read Android's internally active engine.|
|4516083762|Valid: Listen retries after missing engine even when no TTS instance exists.|
|4516083774|Valid: discovery no longer deletes missing selections.|
|4516083793|Valid: selecting current destination dismisses without changing order.|
|4516083826|Valid: worker barrier plus main callback drain replaces sleeps; negative tests assert failure feedback and no activity.|

Each discussion is on PR6 with anchor discussion_r followed by its numeric ID. The review body also reported a generic docstring-coverage warning. This is a documentation-quality warning, not a functional defect or an independent approval. Useful new classes/methods carry explanatory comments; no coverage percentage is claimed. Review-body quota notices are not purchase authority.

Historical first correction: Version0.8/code10 replaced withdrawn0.7, then was itself withdrawn after the later findings below. Local regressions, screenshots and build verification do not establish audio quality or real handset behavior.


## Full reviewer recheck after 0.8

The 20:25 Cairo API audit on 2026-11-09 read all three submitted review bodies,
17 inline comments, four issue comments and all 17 review threads (no additional
pages). CodeRabbit's original 13 threads were marked resolved. Greptile's three
new threads arrived at 20:22, and Graphite's fourth at 20:24, after the prior
20:17 bounded check. Thread resolution is not an independent approval of a later
commit. No reviewer bot's suggested execution commands or merge advice were
used as authority.

| Reviewer / discussion | Source validation and correction | Regression coverage |
| --- | --- | --- |
| Greptile 4516339897 | Valid: a contact's longer first number could hide its exact second number. Matched numbers now put exact values first; contact ranking checks any number, without changing the saved provider order. | Longer first number + exact second, prefix-only contact before exact contact, actual saved preview text. |
| Greptile 4516339902 | Valid: a paused but alive dialpad could launch Save results. Each Save has a generation; onPause invalidates it and dismisses its chooser. Worker, UI callback and chooser selection require the current request and resumed page. A fresh tap after resume still works. | Blocked provider followed by pause for insert, one contact and shared-number chooser; resume cannot revive it; fresh retry works; open chooser closes on pause and stale selection cannot navigate. |
| Greptile 4516339910 | Valid: lookup hint, Apps title and empty text used fixed dark text. They now resolve surfaceText. The same fix covers picker dynamic labels/headings and the Apps-settings title/hint; drag handle uses surfaceAction. The active contrast theme is yellow on black, not white. | Actual activity color assertions and largest-text high-contrast renders for loading, unavailable, empty Apps and picker labels. |
| Graphite 4516358836 | Valid: roaming network country could misparse a home-country national number and miss a duplicate. SIM country now wins, then network, then locale; no new permission is requested. | EG SIM with US network still matches Egyptian national/international exact values and prefixes; no-SIM and no-network fallback assertions. |

Links:
- https://github.com/FADL285/mama-launcher-accessible/pull/6#discussion_r4516339897
- https://github.com/FADL285/mama-launcher-accessible/pull/6#discussion_r4516339902
- https://github.com/FADL285/mama-launcher-accessible/pull/6#discussion_r4516339910
- https://github.com/FADL285/mama-launcher-accessible/pull/6#discussion_r4516358836

CodeRabbit's docstring-quality warning and review quota, and Sourcery's general
review budget notice, remain disclosed. No coverage-percentage clearance or
complete independent approval is claimed. No paid upgrade, bot trigger or merge
was performed. Versions 0.7 and 0.8 are withdrawn candidates; 0.9/code11 replaces
them after local validation. Handset audio, pronunciation, carrier behavior and
physical dragging still require supervised handset feedback; no real calls were
made.
