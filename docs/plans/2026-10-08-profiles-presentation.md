# Phase 6 implementation plan — Profile-aware AppControl

Base: dev@d76735ef950757fc2f8bc37ca0b80b3bdcc6a3a1
Branch: feat/profiles-presentation

1. Add rollback-safe ProfileFilterSettings with a dedicated DataStore key.
2. Feed profile scope into AppControlListViewModel display options.
3. Apply profile scope only to multi-user snapshots.
4. Add profile-scope controls to the existing filter sheet and active-filter row.
5. Add Work Profile / Private Space / Other Profile badges to AppInfoTagsRow.
6. Add serialization and ViewModel filter tests.
7. Run Fork Feature CI and preservation audit.
8. Only after a green checkpoint, fast-forward into dev with an expected-SHA lease.

No release and no main mutation are part of this phase.
