# Phase 6 — Profile-aware AppControl presentation and filtering

## Goal

Turn the typed Android user/profile inventory introduced in Phase 5 into visible, useful AppControl behavior without changing package mutation semantics or privilege routing.

## Scope

- Surface profile type badges for Work Profile, Private Space, and other Android profiles on AppControl rows/action sheets.
- Add a dedicated profile-scope filter for AppControl:
  - All users/profiles
  - Current user
  - Other users/profiles
  - Work Profile
  - Private Space
- Keep profile scope in a new independent DataStore key rather than extending the existing persisted FilterSettings enum. This preserves rollback compatibility with older builds that do not know Phase 6 values.
- Apply profile filtering only when the current AppControl snapshot actually includes multi-user data.
- Non-ALL profile filters fail closed when profile metadata is absent or unknown.

## Data model

New serializable ProfileFilterSettings:

- scope: ALL (default)
- CURRENT_USER
- OTHER_USERS
- WORK_PROFILE
- PRIVATE_PROFILE

The setting is stored at list.profile.filter.settings.

## Matching semantics

- ALL: keep every row.
- CURRENT_USER: userProfile?.isCurrent == true.
- OTHER_USERS: userProfile?.isCurrent == false.
- WORK_PROFILE: userProfile?.type == WORK_PROFILE.
- PRIVATE_PROFILE: userProfile?.type == PRIVATE_PROFILE.

When AppControl.Data.hasIncludedMultiUser is false, profile scope is not applied and the profile filter controls are not surfaced. This prevents a persisted multi-user filter from blanking a single-user snapshot.

## Presentation

AppInfoTagsRow adds a profile-type tag only for:
- WORK_PROFILE → Work profile
- PRIVATE_PROFILE → Private space
- OTHER_PROFILE → Other profile

Full secondary users keep the existing human user label and do not gain a generic type badge.

## Preservation boundaries

Phase 6 must not:
- change Root, ADB, Shizuku, Porter, or Dhizuku routing;
- add cross-profile permissions or ACCESS_HIDDEN_PROFILES;
- start, stop, unlock, lock, create, remove, or switch Android users/profiles;
- broaden package discovery beyond the existing include-multi-user scan;
- alter AppControl mutation eligibility or operation semantics;
- change Analyzer/AppCleaner deletion/scanning behavior;
- modify signing, branding, manifests, database schemas, or release configuration.

## Verification

- golden serialization tests for ProfileFilterSettings;
- AppControl list tests for each profile scope and fail-closed metadata handling;
- UI/build/lint through Fork Feature CI;
- preservation audit before integration.
