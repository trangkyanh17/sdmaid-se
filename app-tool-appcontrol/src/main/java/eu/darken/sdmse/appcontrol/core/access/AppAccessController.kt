package eu.darken.sdmse.appcontrol.core.access

import android.os.Build
import eu.darken.sdmse.appcontrol.core.access.history.AppAccessHistoryDatabase
import eu.darken.sdmse.appcontrol.core.access.history.AppAccessOperation
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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

    data class UndoResult(
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

    suspend fun recentHistory(installId: InstallId): List<AppAccessOperation> =
        history.history(installId).first()

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

    suspend fun undo(
        installId: InstallId,
        operationId: String,
    ): UndoResult? {
        val operation = history.get(operationId) ?: return null
        if (operation.packageName != installId.pkgId.name) return null
        if (operation.userId != installId.userHandle.handleId) return null
        if (operation.revertOf != null) return null
        if (history.hasRevert(operation.id)) return null

        return when (operation.kind) {
            AppAccessOperation.Kind.RUNTIME_PERMISSION -> undoRuntimePermission(installId, operation)
            AppAccessOperation.Kind.APP_OP -> undoAppOp(installId, operation)
        }
    }

    private suspend fun undoRuntimePermission(
        installId: InstallId,
        operation: AppAccessOperation,
    ): UndoResult? {
        val before = operation.before as? AppAccessOperation.Value.Permission ?: return null
        val after = operation.after as? AppAccessOperation.Value.Permission ?: return null
        if (before == after) return null

        val currentSnapshot = inspector.inspect(installId) ?: return null
        val current = currentSnapshot.permissions
            .singleOrNull { it.name == operation.subjectId }
            ?.takeIf { it.runtimeMutable }
            ?: return null
        if (current.granted != after.granted) return null

        val changed = if (before.granted) {
            pkgOps.grantPermission(installId, operation.subjectId)
        } else {
            pkgOps.revokePermission(installId, operation.subjectId)
        }
        if (!changed) return null

        val verifiedSnapshot = inspector.inspect(installId)
            ?: throw IllegalStateException("Target disappeared after permission undo")
        val verified = verifiedSnapshot.permissions
            .singleOrNull { it.name == operation.subjectId }
            ?.takeIf { it.runtimeMutable }
            ?: throw IllegalStateException(
                "Runtime permission ${operation.subjectId} disappeared or became immutable after undo"
            )
        if (verified.granted != before.granted) {
            throw IllegalStateException(
                "Permission undo verification failed for ${operation.subjectId}: " +
                    "requested=${before.granted} actual=${verified.granted}"
            )
        }

        val historyError = recordHistory(
            installId = installId,
            kind = operation.kind,
            subjectId = operation.subjectId,
            before = AppAccessOperation.Value.Permission(after.granted),
            after = AppAccessOperation.Value.Permission(verified.granted),
            revertOf = operation.id,
        )
        return UndoResult(historyError)
    }

    private suspend fun undoAppOp(
        installId: InstallId,
        operation: AppAccessOperation,
    ): UndoResult? {
        val key = PkgOps.AppOpsKey.entries.singleOrNull { it.name == operation.subjectId }
            ?: return null
        if (!key.isSupported(Build.VERSION.SDK_INT)) return null

        val before = operation.before as? AppAccessOperation.Value.AppOp ?: return null
        val after = operation.after as? AppAccessOperation.Value.AppOp ?: return null
        if (before == after) return null

        val current = pkgOps.queryAppOps(installId, key)
        if (current != after.value) return null

        if (!pkgOps.setAppOps(installId, key, before.value)) return null

        val actual = queryAppOps(installId)
        val verified = actual.singleOrNull { it.key == key }
            ?: throw IllegalStateException("AppOps key $key disappeared after undo")
        if (verified.value != before.value) {
            throw IllegalStateException(
                "AppOps undo verification failed for $key: requested=${before.value} actual=${verified.value}"
            )
        }

        val historyError = recordHistory(
            installId = installId,
            kind = operation.kind,
            subjectId = operation.subjectId,
            before = AppAccessOperation.Value.AppOp(after.value),
            after = AppAccessOperation.Value.AppOp(verified.value),
            revertOf = operation.id,
        )
        return UndoResult(historyError)
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
        revertOf: String? = null,
    ): Throwable? = try {
        history.record(
            installId = installId,
            kind = kind,
            subjectId = subjectId,
            before = before,
            after = after,
            revertOf = revertOf,
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
