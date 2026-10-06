# Storage Intelligence Phase 4 Implementation Plan

**Goal:** Add per-storage predictive free-space forecasts to Analyzer storage cards by reusing the existing forecast backend.

**Base:** `dev@37be19b5971860aa061470a1ae674236cfd6675a`

**Branch:** `feat/storage-intelligence-forecast`

## Task 1 — Define per-storage forecast mapping

Add a pure mapping seam that accepts:
- `DeviceStorage`;
- that storage's history;
- custom low-space threshold.

TDD contract:
- history is scoped to the exact storage id;
- current storage capacity/free values are used;
- custom threshold is resolved through `LowStorage.resolveThreshold`;
- no history / invalid history yields the existing fail-closed forecast states;
- a foreign storage's history cannot influence the row.

## Task 2 — Wire forecast into DeviceStorageViewModel

Extend `Row` with `forecast: StorageForecast?`.

Update the existing combine so each row receives:
- its seven-day snapshots;
- its forecast;
- existing Pro state.

Add `AnalyzerSettings.lowStorageThresholdBytes.flow` as a source so threshold changes recompute rows immediately.

Do not add a new scan timer or persistence layer.

## Task 3 — Render forecast on storage cards

Reuse `analyzer_storage_forecast_days`.

Behavior:
- `Filling` replaces the seven-day delta text;
- urgent forecast uses error emphasis;
- calm forecast uses surface-variant emphasis;
- all other forecast states retain the current delta line;
- existing Storage Trend button and Pro lock remain unchanged.

## Task 4 — Verification and preservation checkpoint

Add/extend tests for:
- exact storage-history partitioning;
- automatic/custom thresholds;
- forecast recomputation inputs;
- urgent/calm forecast rendering;
- non-filling forecast fallback to trend;
- existing low-space hint and history navigation behavior.

Run:
- Analyzer/AppControl targeted tests;
- common module tests;
- tool module tests;
- FOSS/GPlay debug builds;
- release lint;
- `git diff --check`.

Audit that no scanner, deletion, AppControl, Root/Shizuku/Porter, signing, branding, persistence-schema, or entitlement scope changed.

Create a Phase 4 checkpoint and stop before integration into `dev`.
