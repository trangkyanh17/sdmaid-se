package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.common.pkgs.pkgops.PkgOps

data class AppOpEntry(
    val key: PkgOps.AppOpsKey,
    val value: PkgOps.AppOpsValue,
)
