package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.appcontrol.core.access.history.AppAccessHistoryDatabase
import eu.darken.sdmse.appcontrol.core.access.history.AppAccessOperation
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class AppAccessController @Inject constructor(
    private val inspector: AppPermissionInspector,
    private val pkgOps: PkgOps,
    private val history: AppAccessHistoryDatabase,
) {

    data class PermissionMutationResult(
        val snapshot: AppPermissionSnapshot,
        val historyError: Throwable? = null,
    )

    data class AppOpMutationResult(
        val appOps: List<AppOpEntry>,
        val historyError: Throwable? = null,
    )

    suspend fun grantRuntimePermission(
        installId: InstallId,
        permissionId: String,
    ): PermissionMutationResult? = mutateRuntimePermission(
        installId = installId,
        permissionId = permissionId,
        desiredGranted = true,
    )

    suspend fun revokeRuntimePermission(
        installId: InstallId,
        permissionId: String,
    ): PermissionMutationResult? = mutateRuntimePermission(
        installId = installId,
        permissionId = permissionId,
        desiredGranted = false,
    )

    suspend fun queryAppOps(installId: InstallId): List<AppOpEntry> =
        AppOpsInspector.inspect(pkgOps, installId)

    suspend fun setAppOp(
        installId: InstallId,
        key: PkgOps.AppOpsKey,
        value: PkgOps.AppOpsValue,
    ): AppOpMutationResult? {
        val before = pkgOps.queryAppOps(installId, key)
        if (before == value) return null

        if (!pkgOps.setAppOps(installId, key, value)) return null

        val actual = queryAppOps(installId)
        val after = actual.singleOrNull { it.key == key }
            ?: throw IllegalStateException("AppOps key $key disappeared after mutation")
        if (after.value != value) {
            throw IllegalStateException(
                "AppOps mutation verification failed for $key: requested=$value actual=${after.value}"
            )
        }

        val historyError = recordHistory(
            installId = installId,
            kind = AppAccessOperation.Kind.APP_OP,
            subjectId = key.name,
            before = AppAccessOperation.Value.AppOp(before),
            after = AppAccessOperation.Value.AppOp(after.value),
        )

        return AppOpMutationResult(
            appOps = actual,
            historyError = historyError,
        )
    }

    private suspend fun mutateRuntimePermission(
        installId: InstallId,
        permissionId: String,
        desiredGranted: Boolean,
    ): PermissionMutationResult? {
        val before = findMutablePermission(installId, permissionId) ?: return null
        if (before.granted == desiredGranted) return null

        val changed = if (desiredGranted) {
            pkgOps.grantPermission(installId, permissionId)
        } else {
            pkgOps.revokePermission(installId, permissionId)
        }
        if (!changed) return null

        val afterSnapshot = inspector.inspect(installId)
            ?: throw IllegalStateException("Target disappeared after permission mutation")
        val after = afterSnapshot.permissions
            .singleOrNull { it.name == permissionId }
            ?.takeIf { it.runtimeMutable }
            ?: throw IllegalStateException(
                "Runtime permission $permissionId disappeared or became immutable after mutation"
            )
        if (after.granted != desiredGranted) {
            throw IllegalStateException(
                "Permission mutation verification failed for $permissionId: " +
                    "requested=$desiredGranted actual=${after.granted}"
            )
        }

        val historyError = recordHistory(
            installId = installId,
            kind = AppAccessOperation.Kind.RUNTIME_PERMISSION,
            subjectId = permissionId,
            before = AppAccessOperation.Value.Permission(before.granted),
            after = AppAccessOperation.Value.Permission(after.granted),
        )

        return PermissionMutationResult(
            snapshot = afterSnapshot,
            historyError = historyError,
        )
    }

    private suspend fun recordHistory(
        installId: InstallId,
        kind: AppAccessOperation.Kind,
        subjectId: String,
        before: AppAccessOperation.Value,
        after: AppAccessOperation.Value,
    ): Throwable? = try {
        history.record(
            installId = installId,
            kind = kind,
            subjectId = subjectId,
            before = before,
            after = after,
        )
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        e
    }

    private suspend fun findMutablePermission(
        installId: InstallId,
        permissionId: String,
    ): AppPermissionSnapshot.Entry? {
        val snapshot = inspector.inspect(installId) ?: return null
        return snapshot.permissions
            .singleOrNull { it.name == permissionId }
            ?.takeIf { it.runtimeMutable }
    }
}
