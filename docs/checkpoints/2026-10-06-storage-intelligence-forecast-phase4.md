# Storage Intelligence Phase 4 Checkpoint

Date: 2026-10-06
Status: Feature branch verified; pending explicit integration approval
Feature: Per-storage predictive free-space forecast

## Repository identities

- Fork: `trangkyanh17/sdmaid-se`
- Upstream mirror: `main`
- Integration branch: `dev`
- Feature branch: `feat/storage-intelligence-forecast`
- Phase 4 base: `37be19b5971860aa061470a1ae674236cfd6675a`
- Verified Phase 4 code HEAD before this checkpoint document: `e29cbdc8f9857c38f253cd3ebfdd3ffdf732e2a6`
- `main` at review time: `2b67b22f787c98fa8dca2b3b5b3a37a8b8b4a550`

## Delivered behavior

### Per-storage forecast

Each Analyzer storage row now derives its own `StorageForecast` from:
- the row's current `DeviceStorage` free/capacity values;
- only seven-day snapshots whose `storageId` matches the row's external storage UUID;
- the current custom low-storage threshold, resolved through `LowStorage.resolveThreshold()`;
- the existing, already-tested `StorageForecaster`.

No new prediction algorithm was introduced.

### Storage isolation

Forecast history is filtered by exact storage id before calling the forecaster.

A primary-storage row cannot be influenced by SD-card or USB history, and vice versa.

### Threshold behavior

`AnalyzerSettings.lowStorageThresholdBytes.flow` is part of the storage-row combine.

Changing the threshold therefore recomputes row forecasts without adding a new persistence layer or background scan cadence.

### UI behavior

On a storage card:
- `StorageForecast.Filling` replaces the retrospective seven-day delta with the localized day-count estimate;
- urgent filling forecasts use error emphasis;
- non-urgent filling forecasts use surface-variant emphasis;
- stable, erratic, insufficient-data, below-floor, and missing forecasts retain the existing trend/delta behavior;
- the existing Storage Trend action and Pro lock behavior are unchanged.

## Tests added

Coverage includes:
- exact per-storage history partitioning;
- foreign-storage history isolation;
- custom threshold forecast recomputation;
- forward forecast rendering on the storage card;
- preservation of existing low-space hint behavior.

## Verification evidence

Verified code HEAD:

`e29cbdc8f9857c38f253cd3ebfdd3ffdf732e2a6`

GitHub Actions:

- Workflow: `Fork Feature CI`
- Run: `37431406020`
- Conclusion: SUCCESS

Verified gates:
- AppControl fast tests: SUCCESS
- Common module tests: SUCCESS
- Tool module tests: SUCCESS
- FOSS/GPlay debug builds: SUCCESS
- Release lint: SUCCESS

## Diff audit

Relative to `dev@37be19b5971860aa061470a1ae674236cfd6675a` before this checkpoint document:

- feature branch: 2 commits ahead, 0 behind;
- 6 files changed;
- implementation changes are limited to the Analyzer storage ViewModel/card and focused tests;
- additional changes are the Phase 4 design and plan documents.

`git diff --check`: PASS.

## Preservation review

The complete `dev...feat/storage-intelligence-forecast` diff shows:

- no implementation outside `app-tool-analyzer`;
- no Root/Shizuku/Porter/Dhizuku changes;
- no package signing, branding, manifest, or release-config changes;
- no Room/database/schema/DAO/entity changes;
- no arbitrary shell or exec surface;
- no destructive delete/remove/uninstall/archive/wipe mutation added;
- no AppControl mutation/history changes;
- no storage enumeration or reconciliation backend changes;
- no snapshot-retention changes;
- no low-space notification policy changes;
- no entitlement/Pro-gating changes;
- `main` remains untouched by Phase 4.

## Integration boundary

Do not merge automatically.

The legal next action is an explicitly approved fast-forward integration of `dev` to the feature branch only after:

1. this checkpoint-document commit's CI is green;
2. `dev` is still `37be19b5971860aa061470a1ae674236cfd6675a`;
3. the feature branch remains 0 commits behind `dev`;
4. `main` remains untouched.

No release is part of this checkpoint.
