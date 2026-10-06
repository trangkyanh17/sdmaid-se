# Profiles Phase 5 Foundation Checkpoint

Date: 2026-10-06
Status: Feature branch verified; pending explicit integration approval
Feature: Shared typed Android user/profile inventory

## Repository identities

- Fork: `trangkyanh17/sdmaid-se`
- Upstream mirror: `main`
- Integration branch: `dev`
- Feature branch: `feat/profiles-foundation`
- Phase 5 base: `218d43cfe61ef35863154c7519358462fcf8dffd`
- Verified Phase 5 code HEAD before this checkpoint document: `61913aefb50e988726f07a3d52062c9201bc602d`
- `main` at review time: `2b67b22f787c98fa8dca2b3b5b3a37a8b8b4a550`

## Delivered behavior

### Shared typed profile metadata

`UserProfile2` now carries conservative profile metadata with source-compatible defaults:

- `SYSTEM`
- `FULL_USER`
- `WORK_PROFILE`
- `PRIVATE_PROFILE`
- `OTHER_PROFILE`
- `UNKNOWN`

Additional metadata:
- raw Android user type;
- normalized flags;
- current-user state;
- visible state when explicitly reported;
- quiet-mode state.

The exact `UserHandle2`, label, legacy code and running state remain intact.

### Modern and legacy discovery

When privileged shell access already exists:

- Android 13+ first attempts `cmd user list -v`;
- if that command fails or yields no parseable users, it falls back to `pm list users`;
- older Android uses `pm list users`;
- when shell discovery produces no usable inventory, the existing `UserManager.userProfiles` fallback remains.

No new privilege backend was introduced.

### Fail-closed parser

The user-list parser is isolated from shell execution and independently tested.

It:
- isolates malformed lines;
- preserves valid lines when another line is malformed;
- handles names containing punctuation/colons;
- preserves unknown future flags;
- distinguishes managed Work Profile and Private Space;
- does not infer profile type from user id alone.

## Tests added

Coverage includes:
- modern full/system user;
- Work Profile;
- Private Space;
- unknown future profile types;
- legacy managed-profile flags;
- legacy full users;
- punctuation in names;
- malformed-line isolation;
- unknown future flags.

## Verification evidence

Verified code HEAD:

`61913aefb50e988726f07a3d52062c9201bc602d`

GitHub Actions:

- Workflow: `Fork Feature CI`
- Run: `37450832795`
- Conclusion: SUCCESS

Verified gates:
- AppControl fast tests: SUCCESS
- Common module tests: SUCCESS
- Tool module tests: SUCCESS
- FOSS/GPlay debug builds: SUCCESS
- Release lint: SUCCESS

## Diff audit

Relative to `dev@218d43cfe61ef35863154c7519358462fcf8dffd` before this checkpoint document:

- feature branch: 3 commits ahead, 0 behind;
- implementation changes are limited to `app-common-io` user/profile inventory and tests;
- additional changes are the Phase 5 design and plan documents.

`git diff --check`: PASS.

## Preservation review

The complete `dev...feat/profiles-foundation` diff shows:

- no implementation outside `app-common-io`;
- no profile lifecycle mutations such as create/remove/start/stop/switch/lock/unlock;
- no Root/Shizuku/Porter/Dhizuku implementation-path changes;
- no AppControl, Analyzer, or AppCleaner implementation changes;
- no manifest, signing, branding, release-config, Room/database/schema/DAO/entity changes;
- no permission additions.

A text audit matched `ACCESS_HIDDEN_PROFILES` only inside the design document's explicit “must not add” preservation rule; no manifest or production-code permission addition exists.

## Integration boundary

Do not merge automatically.

The legal next action is an explicitly approved fast-forward integration of `dev` to this feature branch only after:

1. this checkpoint-document commit's CI is green;
2. `dev` is still `218d43cfe61ef35863154c7519358462fcf8dffd`;
3. the feature branch remains 0 commits behind `dev`;
4. `main` remains untouched.

No release is part of this checkpoint.
