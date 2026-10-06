# AppControl+ Phase 3: Persistent Access Operation History and Safe Undo

## Objective

Add a persistent, bounded journal for AppControl+ access mutations and a conservative undo path for the successful runtime-permission and AppOps mutations introduced in Phase 2.

This milestone is deliberately limited to access mutations. It does not add uninstall/archive/restore/toggle rollback.

## Baseline

- Integration base: `dev@0d907777a5f5ab5c28deb754b21589f4be014eed`
- Feature branch: `feat/appcontrol-operation-history`
- Phase 2 is integrated and verified.
- Runtime permission and AppOps mutations already re-read actual state after every successful change.

## Safety model

History is an immutable journal. Undo is a new compensating operation; original entries are never rewritten.

Every entry stores:
- operation id;
- exact package name;
- exact Android user id;
- operation kind;
- bounded subject id;
- value before mutation;
- value after mutation;
- timestamp;
- optional `revertOf` link to the original operation.

A unique nullable index on `revertOf` prevents more than one persisted undo record for the same original operation.

## Supported operation kinds

### Runtime permission

- Subject: requested permission id.
- Values: `granted` or `denied`.
- History does not make a permission mutable. Undo must re-run the same runtime-dangerous permission guard used by Phase 2.

### AppOps

- Subject: one of the typed `PkgOps.AppOpsKey` values.
- Values: `allow`, `ignore`, `deny`, or `default`.
- No arbitrary AppOps key/value is accepted by undo.

## Recording semantics

A history entry is written only after:
1. the mutation backend reports success; and
2. the affected state has been re-read from the real system.

The recorded `after` value is therefore the observed post-mutation value, not the requested value.

If the post-mutation state does not match the requested state, the mutation is treated as failed for history purposes and no successful journal entry is fabricated.

## Safe undo semantics

Undo is allowed only when all conditions hold:

1. Entry belongs to the exact package + Android user currently open.
2. Entry is an original operation (`revertOf == null`).
3. No undo record already points to it.
4. The operation kind and persisted values parse strictly.
5. The subject remains supported:
   - permission still exists and is runtime-dangerous;
   - AppOps key is one of the bounded enum values and is supported on this Android API.
6. Current real device state equals the recorded `after` state.

If current state drifted, undo refuses to act. It never overwrites a newer user/system change.

After the inverse mutation:
1. re-read real state;
2. require it to equal the original `before` value;
3. append a compensating history entry whose `revertOf` points to the original.

No optimistic rollback state is shown.

## Persistence and retention

Use a small Room database owned by `app-tool-appcontrol`.

Retention is bounded:
- keep at most 1,000 journal rows globally;
- prune rows older than 30 days when a new row is recorded;
- UI reads at most the newest 20 rows for the current package/user.

The history database is not added to config backup/restore in this milestone.

## UI

Add a recent access operations section to the existing Access screen.

For each row show:
- operation type;
- subject;
- before → after;
- timestamp;
- reverted state when applicable.

Only an eligible original operation exposes Undo. Undo requires explicit confirmation.

## Concurrency

The existing single access-mutation gate remains authoritative. Permission, AppOps, and undo mutations may not overlap.

History writes occur after the verified system mutation and before the UI publishes the refreshed state.

## Failure behavior

- Database failure after a successful system mutation must not roll the system state back implicitly.
- History persistence failure is surfaced as an error and the screen reloads real state.
- Malformed history entry fails closed.
- State drift blocks undo.
- Double undo is blocked both in logic and by the database unique index.
- Coroutine cancellation is rethrown.

## Preservation boundaries

This milestone must not:
- change global package scanning;
- change Root/Shizuku/Porter routing order;
- expose arbitrary shell commands;
- broaden runtime-permission mutability;
- broaden AppOps key/value scope;
- change package/signing/branding;
- add destructive AppControl actions;
- merge to `dev` without a separate approved integration boundary.
