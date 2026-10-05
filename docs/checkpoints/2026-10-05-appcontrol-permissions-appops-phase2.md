# AppControl+ Phase 2 Checkpoint

Date: 2026-10-05
Status: Feature branch verified; pending explicit integration approval
Feature: Runtime permission controls + bounded AppOps read/edit

## Repository identities

- Fork: `trangkyanh17/sdmaid-se`
- Upstream: `d4rken-org/sdmaid-se`
- Upstream mirror branch: `main`
- Integration branch: `dev`
- Feature branch: `feat/appcontrol-permissions-appops`
- Phase 2 base / current merge-base: `aa72305fc476cf114b8a891e65761cc2e8270fd0`
- Verified feature code HEAD before this checkpoint document: `22f529c15ca5dc3d0cae256600e89b0bbf202b76`
- Upstream mirror at review time: `main@21642bab7fff3dccc02fe1e2e69fa3056e205092`
- Upstream `d4rken-org/sdmaid-se@main` matched the same commit at review time

## Delivered behavior

### Runtime permissions

- Existing typed `Permission` grant/revoke calls are preserved as compatibility wrappers.
- Raw permission-ID grant/revoke overloads preserve the exact `InstallId` and Android user.
- A permission is mutable only when Android permission metadata resolves its base protection to `PROTECTION_DANGEROUS`.
- Unknown, normal, signature, internal, privileged and special-permission flows remain read-only.
- Grant/revoke is revalidated against a fresh permission snapshot in `AppAccessController`.
- UI requires confirmation before mutation and serializes permission/AppOps mutations.
- After a successful permission mutation, the full access state is reloaded from the real backend; there is no optimistic state flip.

### AppOps

- Read/query backend added through the existing privileged PkgOps IPC route.
- Parser accepts only canonical `allow`, `ignore`, `deny`, and `default` modes.
- Unparseable or unsupported output fails closed instead of guessing.
- Supported AppOps keys remain bounded to:
  - `GET_USAGE_STATS`
  - `MANAGE_EXTERNAL_STORAGE`
  - `ACCESS_RESTRICTED_SETTINGS`
- Per-key minimum Android API availability is enforced.
- UI/controller use typed `AppOpsKey` and `AppOpsValue`; no arbitrary key/value text entry is exposed.
- After AppOps mutation, modes are queried again and the returned real state is rendered.

## Diff audit

Relative to `dev@aa72305fc476cf114b8a891e65761cc2e8270fd0`:

- Feature branch was 18 commits ahead and 0 commits behind before this checkpoint document.
- 24 files changed.
- 1,625 insertions and 58 deletions.
- Changed production code is confined to:
  - existing PkgOps permission/AppOps backend and IPC files;
  - AppControl Access inspector/controller/ViewModel/screen;
  - AppControl strings.
- Additional changes are focused tests, Phase 2 design/plan docs, and the fork feature CI gate.
- No unrelated tool module implementation was changed.

## Preservation review

Verified from the complete `dev...feat/appcontrol-permissions-appops` diff:

- No Root backend files changed.
- No Shizuku / Porter / Dhizuku policy files changed.
- Existing PkgOps AUTO routing remains ADB first, then Root; NORMAL remains unsupported for these privileged mutations.
- No global package scan/scanner policy files changed.
- No package ID, signing, branding, manifest, Gradle build configuration, or release packaging files changed.
- No arbitrary shell-command UI was introduced.
- AppOps UI remains enum-bounded to the three declared keys and four declared modes.
- Runtime permission mutation remains guarded by a fresh snapshot plus `runtimeMutable=true`.
- Exact package and Android user are preserved across permission and AppOps IPC calls.
- Cancellation continues to be rethrown in ViewModel mutation/load paths.
- Concurrent permission/AppOps mutation requests are rejected while another access mutation or confirmation is active.

## Verification evidence

Pre-checkpoint feature verification:

- GitHub Actions workflow: `Fork Feature CI`
- Run: `37248705866`
- Verified code HEAD: `22f529c15ca5dc3d0cae256600e89b0bbf202b76`
- Conclusion: SUCCESS

Verified gates:

- AppControl fast tests: SUCCESS
- Common module tests, including affected common-io tests: SUCCESS
- Tool module tests: SUCCESS
- FOSS debug build: SUCCESS
- GPlay debug build: SUCCESS
- Release lint: SUCCESS

The workflow executes:

- `./gradlew :app-tool-appcontrol:testDebugUnitTest`
- `./gradlew testCommonModules`
- `./gradlew testToolModules`
- `./gradlew app:assembleFossDebug app:assembleGplayDebug`
- `./gradlew lintVitalFossBeta lintVitalFossRelease lintVitalGplayBeta lintVitalGplayRelease`

## Integration boundary

Do not merge automatically.

The legal next action is an explicitly approved fast-forward integration of `dev` to the verified Phase 2 feature branch, after confirming the checkpoint-document commit's CI is green and that:

- `dev` is still at `aa72305fc476cf114b8a891e65761cc2e8270fd0`;
- the feature branch is still 0 commits behind `dev`;
- `main` remains untouched.

No production release is part of this checkpoint.
