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
        val packageInfo = pkgOps.queryPkg(
            id = installId.pkgId,
            flags = PackageManager.GET_PERMISSIONS.toLong(),
            userHandle = installId.userHandle,
        ) ?: return null

        val grantFlags = packageInfo.requestedPermissionsFlags
        val permissions = packageInfo.requestedPermissions
            .orEmpty()
            .mapIndexed { index, name ->
                val flags = grantFlags?.getOrNull(index) ?: 0
                AppPermissionSnapshot.Entry(
                    name = name,
                    granted = flags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0,
                )
            }
            .sortedBy { it.name }

        return AppPermissionSnapshot(
            installId = installId,
            permissions = permissions,
        )
    }
}
