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
    ): Boolean = false

    suspend fun revokeRuntimePermission(
        installId: InstallId,
        permissionId: String,
    ): Boolean = false
}
