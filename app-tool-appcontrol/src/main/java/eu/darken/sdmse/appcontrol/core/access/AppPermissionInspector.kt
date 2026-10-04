package eu.darken.sdmse.appcontrol.core.access

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import javax.inject.Inject

class AppPermissionInspector @Inject constructor(
    private val pkgOps: PkgOps,
    @ApplicationContext private val context: Context,
) {
    suspend fun inspect(installId: InstallId): AppPermissionSnapshot? {
        val pkgInfo = pkgOps.queryPkg(
            id = installId.pkgId,
            flags = PackageManager.GET_PERMISSIONS.toLong(),
            userHandle = installId.userHandle,
        ) ?: return null

        val names = pkgInfo.requestedPermissions.orEmpty()
        val flags = pkgInfo.requestedPermissionsFlags ?: IntArray(0)

        val permissions = names
            .mapIndexed { index, name ->
                val granted = flags
                    .getOrNull(index)
                    ?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
                    ?: false
                AppPermissionSnapshot.Entry(
                    name = name,
                    granted = granted,
                    runtimeMutable = isRuntimeMutable(name),
                )
            }
            .sortedBy { it.name }

        return AppPermissionSnapshot(
            installId = installId,
            permissions = permissions,
        )
    }

    @Suppress("DEPRECATION")
    private fun isRuntimeMutable(permissionId: String): Boolean {
        val info = try {
            context.packageManager.getPermissionInfo(permissionId, 0)
        } catch (_: Exception) {
            return false
        }

        val baseProtection = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        return baseProtection == PermissionInfo.PROTECTION_DANGEROUS
    }
}
