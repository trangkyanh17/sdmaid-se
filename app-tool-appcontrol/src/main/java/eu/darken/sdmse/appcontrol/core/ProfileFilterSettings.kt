package eu.darken.sdmse.appcontrol.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileFilterSettings(
    @SerialName("scope") val scope: Scope = Scope.ALL,
) {
    @Serializable
    enum class Scope {
        @SerialName("ALL") ALL,
        @SerialName("CURRENT_USER") CURRENT_USER,
        @SerialName("OTHER_USERS") OTHER_USERS,
        @SerialName("WORK_PROFILE") WORK_PROFILE,
        @SerialName("PRIVATE_PROFILE") PRIVATE_PROFILE,
    }
}
