# Profiles Phase 5 Foundation Implementation Plan

**Goal:** Build a shared, typed Android user/profile inventory for multi-user, Work Profile, Private Space, and future profile-aware features.

**Base:** `dev@218d43cfe61ef35863154c7519358462fcf8dffd`

**Branch:** `feat/profiles-foundation`

## Task 1 — Add a pure user-list parser

Create a parser in `app-common-io` that supports:

- modern `cmd user list -v` output;
- legacy `pm list users` output;
- normalized flags;
- running/current/visible state;
- quiet-mode state;
- raw platform user type.

TDD cases:
- full owner/current user;
- managed Work Profile;
- Android Private Space profile;
- other/unknown profile type;
- legacy managed-profile flag;
- legacy full user;
- malformed line isolation;
- names containing punctuation.

## Task 2 — Extend UserProfile2 conservatively

Add typed profile metadata with defaults so current constructors remain valid.

No caller is required to opt into the new metadata in this slice.

## Task 3 — Upgrade UserManager2 inventory

When shell access is available:
- API 33+ → `cmd user list -v`;
- older API → `pm list users`.

Map parsed records to `UserProfile2`.

Preserve:
- current `UserManager.userProfiles` fallback;
- guaranteed inclusion of the current process user;
- exact `UserHandle2` identity.

Do not add any profile mutation command.

## Task 4 — Regression audit

Run:
- focused `app-common-io` tests;
- common module tests;
- tool module tests;
- AppControl tests;
- FOSS/GPlay debug builds;
- release lint;
- `git diff --check`.

Audit:
- no manifest/permission additions;
- no profile lifecycle mutation;
- no Root/Shizuku/Porter behavioral changes;
- no AppControl/Analyzer/AppCleaner mutation changes;
- no database/schema changes.

Create a Phase 5 foundation checkpoint and stop before integration into `dev`.
