# AppControl+ Access Inspector Design

## Objective

Add the first AppControl+ capability without changing the existing global app scan or privileged-routing architecture: an on-demand **Permissions & access** inspector for one installed app.

The feature must work for the exact `InstallId` selected in AppControl, including secondary users when the existing Root/ADB package-query path can resolve that user.

## Scope

- Add an AppControl action named **Permissions & access**.
- Open a dedicated AppControl screen for the selected `InstallId`.
- Query package permission metadata only when that screen is opened.
- Display requested permissions and whether Android reports each requested permission as granted.
- Preserve the selected Android user in all package queries.
- Keep the screen read-only in this milestone.
- Add focused unit tests for permission mapping, action visibility/order, navigation, and state loading.

## Non-Scope

This milestone does not:
- grant or revoke permissions;
- query or mutate AppOps;
- manage activities, services, receivers, or providers;
- add Dhizuku or change Root/Shizuku/Porter priority;
- add permission data to the global AppControl scan;
- add operation history or rollback.

Those are separate AppControl+ milestones after this read-only seam is verified.

## Current Authority / Baseline

- Fork: `trangkyanh17/sdmaid-se`.
- Upstream: `d4rken-org/sdmaid-se`.
- Feature base: upstream-synchronized commit `21642bab7fff3dccc02fe1e2e69fa3056e205092`.
- `main` is the fast-forward-only upstream mirror.
- `dev` is the fork integration branch.
- Implementation branch: `feat/appcontrol-plus`.
- Existing package inventory uses `PackageManager.MATCH_ALL`; it does not intentionally request permission metadata.
- `NormalPkg` already implements `PermissionDetails`, but permission arrays are only useful when the underlying `PackageInfo` was queried with `GET_PERMISSIONS`.
- `PkgOps.queryPkg(...)` already preserves a target `UserHandle2` and can route current-user queries normally or cross-user queries through existing privileged backends.

## Approaches Considered

### A. Add GET_PERMISSIONS to the global package inventory

This would make permission metadata available everywhere with minimal new code, but every AppControl/package refresh would fetch data that only one detail screen needs. It increases inventory cost and couples AppControl+ to global package loading.

Rejected.

### B. Query permissions on-demand from the AppAction sheet

This avoids global scan overhead but couples sheet state to a second asynchronous package query and leaves no clean place for later AppOps/component sections.

Rejected.

### C. Dedicated Access Inspector screen with an on-demand loader

Add a small AppControl-owned loader that queries the exact `InstallId` with `GET_PERMISSIONS`, then render the result in a dedicated route/screen.

Selected because it keeps the existing scan unchanged, gives AppOps a future home, and makes the permission mapping independently testable.

## Selected Architecture

### Core model

Create `AppPermissionSnapshot` in the AppControl module:

- `installId: InstallId`
- `permissions: List<Entry>`

Each `Entry` contains:
- `name: String`
- `granted: Boolean`

Requested permission names come from `PackageInfo.requestedPermissions`.
Grant state comes from the corresponding `PackageInfo.requestedPermissionsFlags` entry using `PackageInfo.REQUESTED_PERMISSION_GRANTED`.

If the flags array is shorter than the permissions array, missing flags are treated as not granted. This is fail-closed display behavior and avoids index errors from malformed/OEM data.

Permissions are sorted lexicographically by full permission name for deterministic UI and tests.

### Loader

Create `AppPermissionInspector` and inject `PkgOps`.

`inspect(installId)` calls:

`PkgOps.queryPkg(installId.pkgId, PackageManager.GET_PERMISSIONS.toLong(), installId.userHandle)`

If the package no longer exists, return `null`. Otherwise map the returned `PackageInfo` into `AppPermissionSnapshot`.

No Root/ADB selection logic is duplicated. The existing `PkgOps` routing remains authoritative.

### Navigation

Add:

`@Serializable data class AppAccessRoute(val installId: InstallId) : NavigationDestination`

Register it in `AppControlNavigation`.

Add `AppActionItem.Action.Access` and emit it for installed AppControl rows. The action is positioned immediately after **System settings** so access/permission management stays grouped with app configuration.

Tapping the action navigates to `AppAccessRoute`.

### ViewModel

Create `AppAccessViewModel` with a single bound route.

States:
- `Loading`
- `Ready(installId, packageName, permissions)`
- `NotFound`

Binding a route launches one on-demand inspection. A missing package produces `NotFound`; the host screen navigates up rather than showing stale information.

The first milestone intentionally does not auto-refresh continuously because permission mutations are outside scope. Rebinding the same ViewModel instance is ignored, matching existing detail-screen patterns.

### UI

Create `AppAccessScreen` using existing `SdmScaffold`, `TopAppBar`, and list conventions.

Ready state shows:
- title: package name;
- summary: requested count and granted count;
- one row per requested permission;
- a clear Granted/Denied status for each row.

An empty permission set renders a stable “No requested permissions” row instead of a blank screen.

No custom branding or new dependency is introduced.

## Data Flow

`AppActionSheet`
→ `AppActionItem.Action.Access`
→ `AppAccessRoute(installId)`
→ `AppAccessViewModel.bindRoute()`
→ `AppPermissionInspector.inspect(installId)`
→ `PkgOps.queryPkg(... GET_PERMISSIONS ..., userHandle)`
→ `AppPermissionSnapshot`
→ `AppAccessScreen`

## Failure / Recovery Behavior

- Missing package: `NotFound`, then navigate up.
- Privileged cross-user query unavailable: existing `PkgOps` exception flows through the ViewModel error channel; do not silently fall back to the wrong user.
- Malformed permission flag array: missing flag entries display as denied.
- No requested permissions: Ready state with an empty list and explicit empty-state row.
- Process recreation: route is rebound by the navigation host and the snapshot is reloaded.

## Compatibility / Preservation

Protected behavior:
- existing AppControl list scan inputs and caching remain unchanged;
- existing AppAction actions retain behavior and order except for insertion of Access after System settings;
- Root/Shizuku/Porter routing remains inside `PkgOps`;
- cross-user identity is never collapsed to current user;
- FOSS/GPlay variants receive the same feature implementation;
- no package/branding/signing changes.

## Security / Privacy

Permission metadata is read locally from Android package state. No network, telemetry, credential, or export path is added.

The feature must not request new Android permissions for SD Maid itself.

## Test / Verification Strategy

Focused tests:
1. `AppPermissionInspectorTest`
   - maps requested names and granted flags;
   - missing flags fail closed;
   - empty permission arrays produce an empty snapshot;
   - missing package returns null;
   - exact target user is passed to `PkgOps.queryPkg`.
2. `AppActionItemBuilderTest`
   - Access appears after System settings.
3. `AppActionViewModelTest`
   - tapping Access emits navigation to the exact `InstallId`.
4. `AppAccessViewModelTest`
   - Loading → Ready;
   - missing package → NotFound/navigation behavior.

Broader verification:
- `./gradlew :app-tool-appcontrol:testDebugUnitTest`
- `./gradlew testToolModules`
- `./gradlew app:assembleFossDebug app:assembleGplayDebug`

## Rollback Strategy

The feature is isolated to AppControl module files and one action/route. Rollback is a normal revert of the feature commits; no persisted data or schema migration is introduced.

## Approval State

The user approved beginning implementation after reviewing the AppControl+ direction and Workflow OS branch model. This milestone is the bounded first implementation slice of that approved scope.
