# AppControl+ Phase 2: Permission Controls and AppOps

## Objective

Extend the read-only AppControl+ Access Inspector with bounded, explicit mutation controls:

1. Grant/revoke only permissions that Android classifies as runtime-dangerous.
2. Read and set the small AppOps set already modeled by SD Maid.
3. Preserve exact `InstallId` / Android user and existing Root/Shizuku/Porter routing.
4. Re-read real package/AppOps state after every mutation; never rely on optimistic state.

This milestone does not generalize SD Maid into a full arbitrary package-policy editor.

## Baseline

- Integration base: `dev@aa72305fc476cf114b8a891e65761cc2e8270fd0`
- Upstream mirror: `main@21642bab7fff3dccc02fe1e2e69fa3056e205092`
- Feature branch: `feat/appcontrol-permissions-appops`
- Phase 1 access screen is integrated and verified.
- `PkgOps.grantPermission/revokePermission` currently accepts SD Maid's sealed `Permission` type even though the IPC boundary already accepts a raw permission string.
- `PkgOps.setAppOps` exists for three keys but there is no read/query operation and `AppOpsValue` only contains ALLOW.

## Android semantics

Runtime permission mutation is only exposed when the requested permission resolves to a `PermissionInfo` whose base protection is `PROTECTION_DANGEROUS`. Unknown permissions, normal permissions, signature/internal permissions, and special-permission flows stay read-only.

AppOps is treated independently from runtime permission grant state. The supported modes in this milestone are:
- `ALLOW` → `allow`
- `IGNORE` → `ignore`
- `DENY` → `deny`
- `DEFAULT` → `default`

Supported keys remain:
- `GET_USAGE_STATS`
- `MANAGE_EXTERNAL_STORAGE`
- `ACCESS_RESTRICTED_SETTINGS`

No arbitrary AppOps key entry is accepted from UI.

## Backend design

### Raw permission ID seam

Add raw-string overloads:
- `PkgOps.grantPermission(id, permissionId: String, mode)`
- `PkgOps.revokePermission(id, permissionId: String, mode)`
- corresponding `PkgOpsClient` overloads

Keep the existing `Permission` overloads as delegating compatibility wrappers.

The AIDL and host already transport `permissionId: String`, so no protocol expansion is needed for permission mutation.

### Permission classification

Extend `AppPermissionSnapshot.Entry` with:
- `runtimeMutable: Boolean`

`AppPermissionInspector` resolves `PermissionInfo` using the local PackageManager because permission definitions are global metadata, while grant state remains user-specific and comes from the exact `PackageInfo` queried through `PkgOps`.

Compatibility:
- API 28+: use `PermissionInfo.getProtection()`
- API 26–27: use `protectionLevel and PROTECTION_MASK_BASE`

If metadata lookup fails, `runtimeMutable=false`.

### AppOps read seam

Add IPC method:
`String getAppOpsMode(String packageName, int handleId, String key)`

Host executes:
`appops get --user <user> <package> <key>`

Host parses only canonical modes from command output. If no explicit operation exists and the command prints a default mode, return that default. Unknown/unparseable output fails instead of guessing.

Add:
`PkgOps.queryAppOps(id, key, mode=AUTO): AppOpsValue`

Routing mirrors `setAppOps`: ADB first in AUTO, then Root. NORMAL is unsupported.

## AppControl mutation layer

Create `AppAccessController` in the AppControl module:
- `grantRuntimePermission(installId, permissionId)`
- `revokeRuntimePermission(installId, permissionId)`
- `setAppOp(installId, key, value)`

It delegates only to `PkgOps`. UI never calls Root/ADB clients directly.

The controller validates permission mutation against the current inspector snapshot: a permission must exist on the target and be marked `runtimeMutable`. This prevents stale UI from mutating arbitrary strings.

## UI behavior

### Runtime permissions

Rows show:
- permission ID
- Granted / Denied
- read-only marker for non-runtime permissions

For `runtimeMutable=true`, tapping the state opens a confirmation dialog:
- Grant: explicit confirmation
- Revoke: stronger warning that Android may terminate the target process

After confirmation:
1. show mutation-in-progress state for that row;
2. perform mutation;
3. re-run the inspector;
4. render the actual returned state;
5. surface backend failure through existing error events.

No optimistic flip.

### AppOps

Add an AppOps section below permissions containing exactly the three supported keys and current mode.

Selecting a mode opens a bounded selector for `ALLOW / IGNORE / DENY / DEFAULT`. After set, re-query AppOps and render the actual mode.

## Failure behavior

- Permission metadata unknown → read-only.
- Permission is not runtime-dangerous → read-only.
- Target disappears → NotFound behavior from Phase 1.
- No Root/ADB for cross-user or mutation → explicit Error/event; no fallback to wrong user.
- AppOps output cannot be parsed → error, never assume DEFAULT.
- Mutation command returns false → error and re-read if possible.
- Coroutine cancellation is rethrown.

## Safety / preservation

This milestone does not:
- alter global package scan flags;
- change Root/Shizuku/Porter routing order;
- introduce Dhizuku;
- expose arbitrary shell commands;
- expose arbitrary AppOps keys;
- mutate signature/privileged/internal/special permissions;
- change package/signing/branding configuration;
- merge to `dev` without a separate approved integration boundary.

## Verification

Focused:
- raw permission overload tests
- permission classification tests
- AppOps parser/query tests
- controller validation/mutation tests
- ViewModel confirmation/mutation/refresh tests

Preservation:
- `:app-tool-appcontrol:testDebugUnitTest`
- affected `app-common-io` tests
- `testCommonModules`
- `testToolModules`
- FOSS/GPlay debug builds
- release lint gate already present in fork CI

## Deferred

- persistent operation history
- undo/rollback stack across process restarts
- arbitrary AppOps enumeration
- component manager
- capability-router refactor
- Dhizuku
