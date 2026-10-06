# Profiles Phase 5: Shared profile inventory foundation

## Objective

Create a shared, typed Android user/profile inventory that can be consumed consistently by AppControl, Analyzer, and AppCleaner before any cross-profile mutation is introduced.

This milestone covers Android multi-user, Work Profile, Private Space, and other profile types. It does **not** mean saved AppControl presets or cleanup presets.

## Baseline

- Integration base: `dev@218d43cfe61ef35863154c7519358462fcf8dffd`
- Feature branch: `feat/profiles-foundation`
- AppControl+ Phases 1–3 and Storage Intelligence Phase 4 are integrated and green.
- Existing shared user primitive: `UserProfile2`
- Existing shared inventory: `UserManager2.allUsers()`
- Existing consumers already span AppControl, Analyzer, AppCleaner, package inventory, and data-area discovery.

## Current limitation

`UserManager2` currently treats users largely as ids/names/running state.

That is insufficient for later cross-profile behavior because:
- a secondary full user is not the same thing as a Work Profile;
- Private Space is a distinct profile type;
- locked/quiet profiles must not be treated as ordinary running profiles;
- consumers need one shared classification instead of each tool inventing its own heuristics.

## Inventory model

Extend `UserProfile2` with typed, conservative metadata:

- `type`
  - `SYSTEM`
  - `FULL_USER`
  - `WORK_PROFILE`
  - `PRIVATE_PROFILE`
  - `OTHER_PROFILE`
  - `UNKNOWN`
- `rawType`: platform user-type string when available.
- `flags`: normalized platform flags when available.
- `isCurrent`
- `isVisible`
- `isQuietMode`

Existing fields remain:
- exact `UserHandle2`;
- label;
- legacy code/flag string;
- running state.

Every new field has a safe default so existing callers remain source-compatible.

## Discovery tiers

### API 33+

When privileged shell access is available, use `cmd user list -v`.

Parse the structured fields:
- id;
- name;
- user type;
- flags;
- status markers.

Classification uses the platform user type first and normalized flags second.

### Pre-API 33

Use `pm list users`.

The legacy numeric flag field is decoded only for the small set needed by this milestone:
- system;
- full user;
- managed profile;
- generic profile;
- quiet mode.

Unknown combinations fail closed to `UNKNOWN` or `OTHER_PROFILE`.

### No privileged shell

Keep the existing `UserManager.userProfiles` fallback.

Fallback entries remain conservatively typed as `UNKNOWN`; the current process user is still guaranteed to appear.

## Classification rules

Priority:
1. private profile type → `PRIVATE_PROFILE`;
2. managed-profile type/flag → `WORK_PROFILE`;
3. other profile type/flag → `OTHER_PROFILE`;
4. system type/flag → `SYSTEM`;
5. full-user type/flag → `FULL_USER`;
6. otherwise → `UNKNOWN`.

Do not infer Work Profile merely because an install is outside user 0.

Do not infer Private Space from an arbitrary non-owner user id.

## Parser safety

The parser is isolated from shell execution and unit tested as a pure component.

Requirements:
- malformed lines are skipped, not partially fabricated;
- one bad line must not discard valid users;
- names containing spaces, commas, or colons must not corrupt id/type parsing;
- unknown future flags are preserved but do not crash classification;
- modern and legacy formats are parsed independently.

## Preservation boundary

This foundation must not:
- toggle, start, stop, unlock, lock, create, remove, or switch users/profiles;
- request cross-profile permissions;
- add `ACCESS_HIDDEN_PROFILES` or launcher-role behavior;
- change Root/Shizuku/Porter routing;
- change AppControl mutation semantics;
- change Analyzer storage arithmetic;
- change AppCleaner deletion behavior;
- change package visibility policy;
- change signing, branding, manifests, release configuration, or database schemas.

## Follow-up slices

After this shared inventory is verified, later profile slices can consume it deliberately:
1. profile-aware presentation/filtering;
2. profile availability/locked-state handling;
3. cross-profile read operations;
4. only then, separately gated cross-profile mutations where the existing privilege stack can prove support.

Privilege Engine refactoring stays deferred until these profile slices expose a concrete backend limitation.
