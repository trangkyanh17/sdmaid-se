# Phase 6 checkpoint — Profile-aware AppControl

Date: 2026-10-08

## Base and feature state

- Base dev: `d76735ef950757fc2f8bc37ca0b80b3bdcc6a3a1`
- Feature implementation: `8d46c014b9b87bc804bf25a91fcd9167c3a963b9`
- Branch: `feat/profiles-presentation`

## Delivered

- Dedicated rollback-safe `ProfileFilterSettings` persisted at `list.profile.filter.settings`.
- Profile scopes: All, Current user, Other users/profiles, Work Profile, Private Space.
- Profile scope only applies when the AppControl snapshot actually includes multi-user data.
- Non-ALL profile scopes fail closed on missing profile metadata.
- AppControl rows/tags surface Work Profile, Private Space, and Other Profile types.
- Existing include-multi-user discovery and package mutation behavior remain unchanged.

## Verification

Feature CI #71, run `37692962471`, completed successfully on implementation SHA `8d46c014b9b87bc804bf25a91fcd9167c3a963b9`.

Preservation audit:

- `git diff --check`: PASS
- Feature branch: ahead 3 / behind 0 from the Phase 5+sync dev base
- Forbidden privilege/routing areas touched: none
- Added privilege/profile-mutation keywords: none
- Root / ADB / Shizuku / Porter / Dhizuku routing changes: none
- Manifest/signing/build-system/database schema changes: none
- Profile inventory remains read-only

## Scope boundaries retained

No create/remove/start/stop/switch/lock/unlock user/profile operations.
No `ACCESS_HIDDEN_PROFILES`.
No cross-profile permission additions.
No Analyzer/AppCleaner behavior changes.
No release.

## Integration gate

Fast-forward to `dev` only if:
1. checkpoint CI is green;
2. `dev` is still at the expected base;
3. upstream has not moved relative to fork `main`;
4. feature branch remains a clean descendant of `dev`.
