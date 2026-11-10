# Documentation

## Current 0.14 baseline

| Document | Use it for |
| --- | --- |
| [Family guide (Arabic)](family-guide.ar.md) | Setup, everyday use, permission/speech recovery and safe handoff |
| [Build and test](build-and-test.md) | Reproducible checks, golden review, signing/update continuity |
| [Architecture](architecture.md) | Modules, data ownership, lifecycle and safety invariants |
| [0.14 audit](audit-014.md) | Evidence-ranked findings, proposals, limits and measurement plan |
| [0.14 handset fixes](handset-feedback-014.md) | Causes and regression coverage for the last runtime change |

The audit/documentation branch changes no runtime source, resources, build configuration or APK. The stable baseline is the main commit recorded in the root README. New proposals need their own approval, tests and review.

## History, not current release instructions

These records describe the source and evidence available at the time. Their test counts, candidate status, delivery/reinstall notes and pending reviews can be superseded by the current guides.

- [0.13 handset feedback](handset-feedback-013.md), [bounded audit](audit-013.md), [PR8 triage](pr8-review-triage.md).
- [PR7 handset/dialpad triage](pr7-handset-dialpad-triage.md).
- [PR6 review triage](pr6-review-triage.md).
- [Dialer and voice research, 2026-11-09](dialer-voice-research-2026-11-09.md): options research, not a shipped Egyptian voice or tablet plan.
- [Initial security audit](../SECURITY-AUDIT.md) and [earlier review follow-up](../REVIEW-FOLLOWUP.md).

Use current source/tests to settle behavior questions. Historical documents are evidence of earlier work, not permission to change a release or merge a PR.
