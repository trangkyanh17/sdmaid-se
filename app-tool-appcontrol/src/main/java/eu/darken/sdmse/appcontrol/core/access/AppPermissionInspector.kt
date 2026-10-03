package eu.darken.sdmse.appcontrol.core.access

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import javax.inject.Inject

class AppPermissionInspector @Inject constructor(
    private val pkgOps: PkgOps,
) {
    suspend fun inspect(installId: InstallId): AppPermissionSnapshot? {
        val pkgInfo = pkgOps.queryPkg(
            id = installId.pkgId,
            flags = PackageManager.GET_PERMISSIONS.toLong(),
            userHandle = installId.userHandle,
        ) ?: return null

        val names = pkgInfo.requestedPermissions.orEmpty()
        val flags = pkgInfo.requestedPermissionsFlags.orEmpty()

        val permissions = names
            .mapIndexed { index, name ->
                val granted = flags
                    .getOrNull(index)
                    ?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
                    ?: false
                AppPermissionSnapshot.Entry(
                    name = name,
                    granted = granted,
                )
            }
            .sortedBy { it.name }

        return AppPermissionSnapshot(
            installId = installId,
            permissions = permissions,
        )
    }
}
