package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import javax.inject.Inject

class AppAccessController @Inject constructor(
    private val inspector: AppPermissionInspector,
    private val pkgOps: PkgOps,
) {
    suspend fun grantRuntimePermission(
        installId: InstallId,
        permissionId: String,
    ): Boolean {
        val permission = findMutablePermission(installId, permissionId) ?: return false
        if (permission.granted) return false
        return pkgOps.grantPermission(installId, permissionId)
    }

    suspend fun queryAppOps(installId: InstallId): List<AppOpEntry> = emptyList()

    suspend fun setAppOp(
        installId: InstallId,
        key: PkgOps.AppOpsKey,
        value: PkgOps.AppOpsValue,
    ): Boolean = false

    suspend fun revokeRuntimePermission(
        installId: InstallId,
        permissionId: String,
    ): Boolean {
        val permission = findMutablePermission(installId, permissionId) ?: return false
        if (!permission.granted) return false
        return pkgOps.revokePermission(installId, permissionId)
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
