# AppControl+ Phase 2 Implementation Plan

**Goal:** Add bounded runtime-permission grant/revoke and read/edit support for the three existing SD Maid AppOps keys.

**Base:** `dev@aa72305fc476cf114b8a891e65761cc2e8270fd0`

**Branch:** `feat/appcontrol-permissions-appops`

## Task 1 — Runtime permission capability and raw-ID backend

Files:
- Modify `app-common-io/.../pkgops/PkgOps.kt`
- Modify `app-common-io/.../pkgops/ipc/PkgOpsClient.kt`
- Modify `app-tool-appcontrol/.../access/AppPermissionSnapshot.kt`
- Modify `app-tool-appcontrol/.../access/AppPermissionInspector.kt`
- Add/modify focused tests

TDD contract:
- raw permission ID overload preserves exact `InstallId` and routes through the same ADB/Root policy;
- existing typed `Permission` overload delegates to raw ID;
- dangerous permission → `runtimeMutable=true`;
- normal/signature/internal/unknown permission → false;
- exact user grant state remains sourced from queried `PackageInfo`.

## Task 2 — Permission mutation controller + UI

Create `AppAccessController`.

TDD contract:
- only a permission present in the latest snapshot with `runtimeMutable=true` can be mutated;
- grant/revoke delegates exact ID and exact user;
- mutation failure does not fabricate state;
- ViewModel re-inspects after mutation;
- revoke requires explicit confirmation path;
- cancellation is preserved.

UI:
- runtime permission rows expose Grant/Revoke action;
- non-runtime rows remain read-only;
- no optimistic state flip.

## Task 3 — AppOps query backend

Files:
- Extend `PkgOpsConnection.aidl`
- Extend `PkgOpsHost`
- Extend `PkgOpsClient`
- Extend `PkgOps`

TDD contract:
- parse explicit `KEY: allow|ignore|deny|default`;
- parse `No operations.` + `Default mode: ...`;
- reject unknown/unparseable output;
- query routing matches set routing;
- values include ALLOW, IGNORE, DENY, DEFAULT.

## Task 4 — AppOps inspector/controller/UI

Create an AppOps snapshot/inspector and integrate into the Access screen.

TDD contract:
- exactly the three declared keys are shown;
- each key displays actual queried mode;
- set accepts only enum key/value, not arbitrary strings;
- after mutation, query again and render actual mode;
- backend errors surface explicitly.

## Task 5 — Preservation review

Run:
- AppControl fast tests
- affected common-io tests
- `testCommonModules`
- `testToolModules`
- FOSS/GPlay debug builds
- release lint

Review:
- no global scan flag change
- no Root/Shizuku/Porter policy change
- no arbitrary shell/UI injection surface
- no mutation of non-runtime permission classes
- no package/signing/branding changes

Checkpoint feature branch and stop before merge to `dev`.
