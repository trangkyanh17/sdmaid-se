package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.common.pkgs.features.InstallId

data class AppPermissionSnapshot(
    val installId: InstallId,
    val permissions: List<Entry>,
) {
    data class Entry(
        val name: String,
        val granted: Boolean,
    )
}
