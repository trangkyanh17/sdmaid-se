# AppControl+ Phase 3 Checkpoint

Date: 2026-10-06
Status: Feature branch verified; pending explicit integration approval
Feature: Persistent access-operation history + drift-safe undo

## Repository identities

- Fork: `trangkyanh17/sdmaid-se`
- Upstream: `d4rken-org/sdmaid-se`
- Upstream mirror branch: `main`
- Integration branch: `dev`
- Feature branch: `feat/appcontrol-operation-history`
- Phase 3 base / merge-base: `0d907777a5f5ab5c28deb754b21589f4be014eed`
- Verified Phase 3 code HEAD before this checkpoint document: `e53e9a807ecd2ef1a7bc8abb56e36d7af2cd373e`
- Fork `main` at review time: `21642bab7fff3dccc02fe1e2e69fa3056e205092`
- Upstream `d4rken-org/sdmaid-se@main` at review time: `2b67b22f787c98fa8dca2b3b5b3a37a8b8b4a550`
- Upstream advanced during this milestone; no upstream sync was mixed into the feature branch.

## Delivered behavior

### Persistent bounded operation journal

- AppControl owns a dedicated Room database for access mutations.
- Journal rows preserve exact package name and Android user id.
- Supported operation kinds remain bounded to:
  - runtime permission;
  - AppOps.
- Runtime-permission values persist only `granted` / `denied`.
- AppOps values persist only the typed Phase 2 values:
  - `allow`
  - `ignore`
  - `deny`
  - `default`
- Malformed persisted kind/value fails closed.
- History is bounded:
  - maximum 1,000 rows globally;
  - rows older than 30 days are pruned on record;
  - Access UI reads at most the newest 20 rows for the exact package/user.
- A unique nullable `revertOf` index prevents more than one persisted compensating undo row for one original operation.

### Verified mutation journaling

- Permission mutation captures the real pre-mutation state.
- Mutation runs through the existing Phase 2 guarded backend.
- Permission state is re-read from the real system after mutation.
- A successful history row is written only after the observed state matches the requested state.
- AppOps captures the real pre-mutation mode, mutates, and re-reads the real mode before journaling.
- History persistence failure is surfaced separately and does not fabricate or optimistically alter device state.
- Coroutine cancellation is rethrown.

### Drift-safe undo

Undo is rejected unless:
- the entry belongs to the exact package + Android user currently open;
- it is an original operation, not a compensating row;
- no existing undo already references it;
- persisted kind/value/subject remain valid;
- runtime permission is still runtime-mutable, or AppOps key remains typed and API-supported;
- current real device state still equals the operation's recorded `after` state.

If the state drifted after the recorded operation, undo refuses to overwrite the newer state.

After an inverse mutation:
- real device state is re-read;
- the result must equal the original `before` state;
- a new compensating journal row is appended with `revertOf` pointing to the original entry.

Original journal rows are immutable.

### UI

The existing AppControl Access screen now includes recent access changes.

Rows show:
- mutation type;
- subject;
- before → after;
- timestamp;
- reverted state when applicable.

Undo:
- is shown only for currently eligible rows;
- requires explicit confirmation;
- uses the existing access-mutation serialization gate;
- does not optimistically update history or access state.

## Diff audit

Relative to `dev@0d907777a5f5ab5c28deb754b21589f4be014eed` before this checkpoint document:

- feature branch: 8 commits ahead, 0 behind;
- 16 files changed;
- 1,734 insertions;
- 156 deletions.

Changed implementation is confined to `app-tool-appcontrol`:
- AppControl Room/history backend;
- AppControl access controller;
- AppControl Access ViewModel/screen;
- AppControl strings;
- AppControl module Room dependency/configuration;
- focused tests.

Additional changes are Phase 3 design/plan documentation.

## Preservation review

Verified from the complete `dev...feat/appcontrol-operation-history` diff:

- `git diff --check`: PASS.
- No Root backend implementation changed.
- No Shizuku / Porter / Dhizuku policy changed.
- No global package scan/scanner policy changed.
- No implementation outside `app-tool-appcontrol` changed.
- No arbitrary shell/exec mutation surface was introduced.
- Runtime-permission mutability was not broadened.
- AppOps key/value scope was not broadened.
- No uninstall/archive/restore/toggle rollback was added.
- No package ID, signing, manifest, or branding change was introduced.
- `main` was not modified by Phase 3.

## Verification evidence

Verified code HEAD:

`e53e9a807ecd2ef1a7bc8abb56e36d7af2cd373e`

GitHub Actions:

- Workflow: `Fork Feature CI`
- Run: `37403484905`
- Conclusion: SUCCESS

Verified gates:

- AppControl fast tests: SUCCESS — 326/326 tests passed.
- Common module tests: SUCCESS.
- Tool module tests: SUCCESS.
- FOSS/GPlay debug builds: SUCCESS.
- Release lint: SUCCESS.

Earlier failing runs were not accepted as checkpoints. The final fixes included:
- removal of an invalid MockK import;
- deterministic history-record mocking by removing wall-clock time from the public mocked method;
- Robolectric-backed Room DAO coverage;
- corrected access-controller history mocks.

## Integration boundary

Do not merge automatically.

The legal next action is an explicitly approved fast-forward integration of `dev` to the Phase 3 feature branch after:

1. the checkpoint-document commit's own CI is green;
2. `dev` is still `0d907777a5f5ab5c28deb754b21589f4be014eed`;
3. the feature branch is still 0 commits behind `dev`;
4. `main` remains untouched by the feature merge.

Upstream `main` has advanced independently and should be handled as a separate upstream-sync boundary, not mixed into this Phase 3 feature integration.

No production release is part of this checkpoint.
