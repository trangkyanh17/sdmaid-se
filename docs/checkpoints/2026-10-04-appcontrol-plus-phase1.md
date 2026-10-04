# AppControl+ Phase 1 Checkpoint

Date: 2026-10-04
Status: Integrated into `dev` and verified
Feature: AppControl+ Access Inspector

## Repository identities

- Fork: `trangkyanh17/sdmaid-se`
- Upstream: `d4rken-org/sdmaid-se`
- Upstream mirror branch: `main`
- Integration branch: `dev`
- Feature branch: `feat/appcontrol-plus`
- Upstream/main base: `21642bab7fff3dccc02fe1e2e69fa3056e205092`
- Integrated feature HEAD: `e3bed4c1cfd0d5d19176f5130520deb630acbb29`
- Integration method: fast-forward `dev` to the verified feature HEAD
- `main` remained unchanged and matched upstream at checkpoint time

## Delivered behavior

- On-demand permission inspection through existing `PkgOps`
- Exact `InstallId` / Android user preserved
- Requested permissions mapped to Granted/Denied state
- Missing permission flags fail closed as denied
- AppControl action: **Permissions & access**
- Dedicated navigation route and read-only access screen
- Loading, empty, missing-package, and explicit error states
- Access action restricted to normal installed packages only
- Cross-user query failures surface an error instead of leaving an infinite loading state

## Preserved behavior

The feature did not modify:
- Root backend
- Shizuku / Porter backend
- `PkgOps` routing
- global package inventory query flags
- package ID / signing configuration
- other SD Maid tool modules

The global AppControl scan remains unchanged; permission metadata is queried only when the access screen is opened.

## Review findings resolved

1. Access action originally appeared for archived/hidden/uninstalled/library rows.
   - Fixed with an explicit `accessAvailable` capability derived from `NormalPkg`.
2. Cross-user or package-query failure could leave the screen in `Loading`.
   - Fixed with explicit `State.Error`, cancellation preservation, and error-event propagation.

## Verification evidence

Integration run on `dev`:
- GitHub Actions run: `37187783757`
- Head: `e3bed4c1cfd0d5d19176f5130520deb630acbb29`
- Conclusion: SUCCESS

Verified gates:
- AppControl fast tests: SUCCESS
- Tool module tests: SUCCESS
- FOSS + GPlay debug builds: SUCCESS
- Release lint: SUCCESS

Pre-integration feature verification:
- Run `37169195584`: SUCCESS
- AppControl tests, tool-module tests, both debug variants, and release lint all passed.

## Git state at checkpoint

- `dev`: integrated Phase 1
- `main`: upstream mirror, unchanged
- `feat/appcontrol-plus`: retained for traceability
- No PR required for this approved fast-forward integration
- No production release performed

## Next legal action

Start the next AppControl+ milestone from `dev` on a new feature branch.

Recommended next scope:
1. Permission grant/revoke UI and safety rules
2. AppOps read/edit surface
3. Operation history / rollback semantics before broader destructive controls

Do not implement component management or privilege-engine refactors in the same milestone.
