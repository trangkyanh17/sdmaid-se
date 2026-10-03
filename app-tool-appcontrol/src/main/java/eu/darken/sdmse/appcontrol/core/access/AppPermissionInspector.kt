package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import javax.inject.Inject

class AppPermissionInspector @Inject constructor(
    private val pkgOps: PkgOps,
) {
    suspend fun inspect(installId: InstallId): AppPermissionSnapshot? = null
}
