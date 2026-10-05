package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps

internal object AppOpsInspector {
    suspend fun inspect(
        pkgOps: PkgOps,
        installId: InstallId,
    ): List<AppOpEntry> = emptyList()
}
