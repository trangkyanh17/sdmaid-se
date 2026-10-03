# AppControl+ Access Inspector Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only, on-demand Permissions & access screen to AppControl without increasing global package-scan cost or changing privileged backend routing.

**Architecture:** A new `AppPermissionInspector` queries the selected `InstallId` with `PackageManager.GET_PERMISSIONS` through existing `PkgOps`. AppControl exposes a new Access action and route; a dedicated ViewModel and Compose screen render the deterministic permission snapshot. AppOps mutation, permission mutation, components, and privilege refactors remain out of scope.

**Tech Stack:** Kotlin 2.2, Android SDK 36, Jetpack Compose, Navigation 3, Hilt, coroutines/Flow, JUnit 5, MockK, Kotest.

## Global Constraints

- Base implementation on `feat/appcontrol-plus` from synchronized upstream commit `21642bab7fff3dccc02fe1e2e69fa3056e205092`.
- Do not modify the global `NormalPkgsSource` query flags.
- Do not add a new permission request to SD Maid.
- Preserve exact `InstallId.userHandle` in the inspection query.
- Reuse `PkgOps`; do not duplicate Root/Shizuku/Porter selection.
- Permission state is read-only in this milestone.
- Missing or malformed grant flags fail closed as `granted=false`.
- No AppOps/component-manager/undo behavior in this milestone.
- Existing AppControl behavior remains protected.

---

### Task 1: Permission snapshot core

**Files:**
- Create: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/core/access/AppPermissionSnapshot.kt`
- Create: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/core/access/AppPermissionInspector.kt`
- Test: `app-tool-appcontrol/src/test/java/eu/darken/sdmse/appcontrol/core/access/AppPermissionInspectorTest.kt`

**Interfaces:**
- Consumes: `PkgOps.queryPkg(Pkg.Id, Long, UserHandle2, PkgOps.Mode = AUTO): PackageInfo?`
- Produces: `suspend fun AppPermissionInspector.inspect(installId: InstallId): AppPermissionSnapshot?`

- [ ] **Step 1: Add the focused failing test**

Cases:
- query uses `PackageManager.GET_PERMISSIONS.toLong()` and the exact package/user from `InstallId`;
- two requested permissions map in source order before deterministic sorting, with one granted bit and one denied;
- shorter `requestedPermissionsFlags` treats the unmatched permission as denied;
- null package result returns null;
- null requested permissions produces an empty permission list.

- [ ] **Step 2: Verify the relevant failure**

Run: `./gradlew :app-tool-appcontrol:testDebugUnitTest --tests '*AppPermissionInspectorTest*'`
Expected: non-zero because `AppPermissionInspector` / `AppPermissionSnapshot` do not exist.

- [ ] **Step 3: Implement the minimum behavior**

`AppPermissionInspector.inspect()` performs exactly one `PkgOps.queryPkg` call with `GET_PERMISSIONS`, maps names against flags using index-safe lookup, tests `PackageInfo.REQUESTED_PERMISSION_GRANTED`, sorts entries by `name`, and returns null only when the package query returns null.

- [ ] **Step 4: Verify the focused pass**

Run: `./gradlew :app-tool-appcontrol:testDebugUnitTest --tests '*AppPermissionInspectorTest*'`
Expected: all `AppPermissionInspectorTest` cases pass.

- [ ] **Step 5: Run the affected integration check**

Run: `./gradlew :app-tool-appcontrol:testDebugUnitTest`
Expected: AppControl unit suite passes.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/core/access app-tool-appcontrol/src/test/java/eu/darken/sdmse/appcontrol/core/access
git commit -m "feat(appcontrol): inspect app permissions on demand"
```

### Task 2: Access action and navigation route

**Files:**
- Modify: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/AppControlRoutes.kt`
- Modify: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/AppControlNavigation.kt`
- Modify: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/list/actions/items/AppActionItem.kt`
- Modify: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/list/actions/items/AppActionItemBuilder.kt`
- Modify: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/list/actions/AppActionViewModel.kt`
- Modify: `app-tool-appcontrol/src/test/java/eu/darken/sdmse/appcontrol/ui/list/actions/items/AppActionItemBuilderTest.kt`
- Modify: `app-tool-appcontrol/src/test/java/eu/darken/sdmse/appcontrol/ui/list/actions/AppActionViewModelTest.kt`

**Interfaces:**
- Consumes: `InstallId`, existing AppControl navigation/event APIs.
- Produces: `AppAccessRoute(installId)` and `AppActionItem.Action.Access(installId)`.

- [ ] **Step 1: Add focused failing tests**

Assertions:
- full current-user action list contains Access immediately after SystemSettings;
- cross-user action list also contains Access because the route preserves user identity and the loader delegates capability handling to `PkgOps`;
- tapping Access emits navigation to `AppAccessRoute` carrying the exact `InstallId`.

- [ ] **Step 2: Verify the relevant failure**

Run: `./gradlew :app-tool-appcontrol:testDebugUnitTest --tests '*AppActionItemBuilderTest*' --tests '*AppActionViewModelTest*'`
Expected: non-zero because Access item/route do not exist.

- [ ] **Step 3: Implement the minimum behavior**

Add serializable `AppAccessRoute`, Access action item, builder insertion after SystemSettings, and ViewModel navigation branch. Register the route in AppControl navigation, pointing to the screen host created in Task 3. Until Task 3 lands in the same candidate, the route registration may reference the proposed host but the integration check is deferred to Task 3.

- [ ] **Step 4: Verify the focused pass**

Run the same focused test command.
Expected: builder and ViewModel tests pass.

- [ ] **Step 5: Run the affected integration check**

Run: `./gradlew :app-tool-appcontrol:testDebugUnitTest`
Expected: AppControl unit suite passes once Task 3 completes the route host.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui app-tool-appcontrol/src/test/java/eu/darken/sdmse/appcontrol/ui
git commit -m "feat(appcontrol): add permissions access route"
```

### Task 3: Access ViewModel and Compose screen

**Files:**
- Create: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/access/AppAccessViewModel.kt`
- Create: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/access/AppAccessScreen.kt`
- Create: `app-tool-appcontrol/src/test/java/eu/darken/sdmse/appcontrol/ui/access/AppAccessViewModelTest.kt`
- Modify: `app-tool-appcontrol/src/main/res/values/strings.xml`
- Modify: `app-tool-appcontrol/src/main/java/eu/darken/sdmse/appcontrol/ui/list/actions/AppActionSheet.kt`

**Interfaces:**
- Consumes: `AppAccessRoute`, `AppPermissionInspector.inspect(InstallId)`.
- Produces: `AppAccessViewModel.State.Loading|Ready|NotFound` and `AppAccessScreenHost(route)`.

- [ ] **Step 1: Add focused failing tests**

Cases:
- bind route calls inspector once and reaches Ready with the exact snapshot;
- null inspection reaches NotFound;
- a second bind on the same ViewModel does not replace the original target.

- [ ] **Step 2: Verify the relevant failure**

Run: `./gradlew :app-tool-appcontrol:testDebugUnitTest --tests '*AppAccessViewModelTest*'`
Expected: non-zero because ViewModel/screen state types do not exist.

- [ ] **Step 3: Implement the minimum behavior**

Implement the three-state ViewModel. Build a Compose screen using existing scaffold/top-app-bar/list primitives. Ready state displays package name, requested/granted counts, each permission name and Granted/Denied status, plus an explicit empty-state row. Add the AppActionSheet rendering for the Access action.

- [ ] **Step 4: Verify the focused pass**

Run the same focused test command.
Expected: all AppAccess ViewModel cases pass.

- [ ] **Step 5: Run the affected integration check**

Run:
- `./gradlew :app-tool-appcontrol:testDebugUnitTest`
- `./gradlew app:assembleFossDebug app:assembleGplayDebug`

Expected: AppControl tests and both debug variants pass.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add app-tool-appcontrol
git commit -m "feat(appcontrol): show requested app permissions"
```

### Task 4: Integrated preservation verification

**Files:**
- No production files required unless verification finds a regression.
- Evidence source: feature branch CI plus Git comparison against `dev`.

**Interfaces:**
- Consumes: Tasks 1–3 exact commits.
- Produces: verified AppControl+ Access Inspector candidate ready for review/integration decision.

- [ ] **Step 1: Run AppControl regression**

Run: `./gradlew :app-tool-appcontrol:testDebugUnitTest`
Expected: zero failures.

- [ ] **Step 2: Run tool-module regression**

Run: `./gradlew testToolModules`
Expected: zero failures.

- [ ] **Step 3: Build both distribution lanes**

Run: `./gradlew app:assembleFossDebug app:assembleGplayDebug`
Expected: both APK variants assemble successfully.

- [ ] **Step 4: Inspect preservation diff**

Compare `dev...feat/appcontrol-plus`.
Expected: only AppControl access-inspector code/tests/resources plus approved design/plan documentation; no global package-source, Root, ADB/Shizuku/Porter, signing, package-id, or unrelated tool modifications.

- [ ] **Step 5: Review**

Perform self-review and an independent review when available, verify every Critical/Important finding before changing code, then rerun affected tests for any accepted fix.

- [ ] **Step 6: Checkpoint**

Record final feature HEAD, upstream base `21642bab7fff3dccc02fe1e2e69fa3056e205092`, verification evidence, open risks, and the next integration boundary. Do not merge to `dev` without the separate integration decision required by Workflow OS.

## Unresolved externally observable decisions

None for this milestone. Permission mutation, AppOps behavior, component management, and undo semantics are intentionally separate future design decisions.
