package eu.darken.sdmse.appcontrol.core.access

import android.os.Build
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps

internal object AppOpsInspector {
    suspend fun inspect(
        pkgOps: PkgOps,
        installId: InstallId,
        apiLevel: Int = Build.VERSION.SDK_INT,
    ): List<AppOpEntry> = PkgOps.AppOpsKey.entries
        .filter { it.isSupported(apiLevel) }
        .map { key ->
        AppOpEntry(
            key = key,
            value = pkgOps.queryAppOps(installId, key),
        )
    }
}
