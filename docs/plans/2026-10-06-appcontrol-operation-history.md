# AppControl+ Phase 3 Implementation Plan

**Goal:** Persist successful Phase 2 access mutations and add drift-safe compensating undo.

**Base:** `dev@0d907777a5f5ab5c28deb754b21589f4be014eed`

**Branch:** `feat/appcontrol-operation-history`

## Task 1 — Persistent bounded operation journal

Add:
- Room entity/DAO/database;
- typed domain mapping;
- repository API;
- 30-day and 1,000-row retention;
- per-target newest-20 query;
- unique `revertOf` invariant.

TDD contract:
- exact package + user partitioning;
- newest-first ordering;
- nullable unique revert link allows many originals but only one undo per original;
- retention removes expired rows and trims overflow;
- malformed persisted operation kind/value fails closed in domain mapping.

## Task 2 — Record verified permission/AppOps mutations

Extend the Phase 2 mutation flow.

TDD contract:
- capture real before state;
- mutate;
- re-read real after state;
- write history only when observed after state matches the requested target;
- exact package + user + subject preserved;
- persistence failure surfaces without fabricating UI state;
- cancellation preserved.

## Task 3 — Safe undo controller

Add undo eligibility and inverse execution.

TDD contract:
- reject wrong package/user;
- reject compensating entries;
- reject already-reverted operations;
- reject malformed/unsupported subject/value;
- reject when current real state differs from recorded after state;
- runtime permission undo reuses runtime-dangerous guard;
- AppOps undo remains enum-bounded;
- after inverse mutation, re-read and require recorded before state;
- append exactly one compensating journal entry.

## Task 4 — History + Undo UI

Integrate recent operations into the existing Access screen.

TDD contract:
- latest 20 for exact target;
- original/reverted/undo rows render distinctly;
- only eligible rows expose Undo;
- explicit confirmation required;
- undo participates in the existing access-mutation serialization;
- no optimistic state/history changes.

## Task 5 — Preservation review and checkpoint

Run:
- AppControl fast tests;
- affected Room/history tests;
- `testCommonModules`;
- `testToolModules`;
- FOSS/GPlay debug builds;
- release lint.

Review:
- no global scan change;
- no Root/Shizuku/Porter policy change;
- no arbitrary mutation surface;
- no permission/AppOps scope expansion;
- no destructive AppControl scope added;
- no package/signing/branding changes.

Checkpoint feature branch and stop before merge to `dev`.
