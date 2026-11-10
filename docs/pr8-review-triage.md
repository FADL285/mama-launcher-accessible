# PR8 automated review triage

- Greptile: approved runtime head 4ef2ac5, no comments.
- Graphite: completed successfully, 0 comments.
- Sourcery: budget-skipped; generated guide is not a completed defect review.
- CodeRabbit: one minor actionable finding at CenteredPageHeader. Verified that the Back control was not vertically centered beside a two-line title. New regression failed before the correction (expected center 214px, observed80px), then passed with mode-specific vertical gravity. Full app186/UI84 suites and snapshot verification pass after the fix. New pixels inspected.
- CodeRabbit docstring-coverage warning: documentation quality advice, not a demonstrated runtime/security defect. No unrelated mass-docstring change.

Review status is not device-test evidence. New commits can invalidate prior approvals. No automatic merge.
