# Storage Intelligence Phase 4: Per-storage predictive forecast

## Objective

Surface the existing robust storage forecast on every Analyzer storage card, using the card's current storage reading, the exact storage's seven-day history, and the user's configured low-storage threshold.

This milestone does not invent another forecasting algorithm. It reuses the already-tested `StorageForecaster` that is used by the dashboard and low-space warning pipeline.

## Baseline

- Integration base: `dev@37be19b5971860aa061470a1ae674236cfd6675a`
- Feature branch: `feat/storage-intelligence-forecast`
- Phase 3 and the 2026-10-06 upstream sync are already integrated and verified.
- Existing storage forecast backend already handles:
  - insufficient history;
  - sparse history;
  - erratic growth;
  - stable/non-filling storage;
  - custom low-space thresholds;
  - urgent forecasts;
  - already-below-threshold state.

## Why this is the next slice

The Analyzer storage list already shows:
- live capacity/free-space values;
- seven-day historical delta;
- history navigation.

The forecast backend and localized day-count string already exist, but the per-storage cards do not consume them. This leaves useful storage intelligence available on the dashboard for primary storage while secondary/portable storage cards only show retrospective deltas.

Phase 4 closes that gap with a narrow UI/data-flow change before moving to broader profiles/privilege/diagnostic work.

## Data model

Extend `DeviceStorageViewModel.Row` with:

`forecast: StorageForecast?`

The forecast is derived from:
- the exact `DeviceStorage` row;
- only snapshots whose `storageId` matches that storage's external id;
- `AnalyzerSettings.lowStorageThresholdBytes`;
- `LowStorage.resolveThreshold()`;
- `StorageForecaster.forecast()`.

No forecast state is persisted.

## Freshness model

The current reading is the `DeviceStorage` value emitted by the Analyzer scan. History is seven days from `SpaceHistoryRepo`.

A forecast is recomputed whenever any of these change:
- Analyzer storage data;
- seven-day history;
- configured low-storage threshold.

No background scan cadence is added in this milestone. The existing refresh behavior remains authoritative for refreshing the current storage reading.

## UI behavior

On each storage card:

- `StorageForecast.Filling` replaces the seven-day delta sentence with the localized day-count forecast.
- Urgent `Filling` uses the error emphasis already used by the dashboard.
- Calm `Filling` uses the secondary/on-surface-variant emphasis.
- `Stable`, `Erratic`, `InsufficientData`, `BelowFloor`, and `null` keep the existing retrospective delta behavior.

This mirrors the dashboard rule that a meaningful forward estimate replaces, rather than duplicates, the trend line.

The existing Pro lock/history button behavior is unchanged. The forecast itself is not newly paywalled; the dashboard already exposes the same forecast result without a Pro gate.

## Failure and safety behavior

- Invalid capacity/history continues to fail closed through `StorageForecaster`.
- A missing history sequence yields no actionable forecast.
- Snapshot data from another storage must never affect a row's forecast.
- The custom threshold is resolved per storage capacity.
- No destructive operation is introduced.
- No optimistic storage values are introduced.
- No scanner, deletion, Root/Shizuku/Porter, package-management, signing, or branding behavior changes.

## Preservation boundary

This milestone must not:
- modify AppControl mutation/history semantics;
- modify storage scanner enumeration/reconciliation;
- change snapshot retention;
- change low-space notification policy;
- change Pro entitlement rules;
- add new persistence/schema;
- add background jobs;
- change global task routing;
- merge to `dev` without a separate verified integration boundary.
