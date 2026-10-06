package eu.darken.sdmse.common.user

import android.os.Parcelable
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserProfile2(
    val handle: UserHandle2,
    val label: String? = null,
    val code: String? = null,
    val isRunning: Boolean = true,
    val type: Type = Type.UNKNOWN,
    val rawType: String? = null,
    val flags: Set<String> = emptySet(),
    val isCurrent: Boolean = false,
    val isVisible: Boolean = false,
    val isQuietMode: Boolean = false,
) : Parcelable {

    val isProfile: Boolean
        get() = type == Type.WORK_PROFILE || type == Type.PRIVATE_PROFILE || type == Type.OTHER_PROFILE

    fun getHumanLabel(): CaString = caString {
        when {
            label != null -> label
            handle.handleId == 0 -> getString(R.string.general_user_label_owner)
            handle.handleId == -1 -> getString(R.string.general_user_label_system)
            else -> "User-${handle.handleId}"
        }
    }

    enum class Type {
        SYSTEM,
        FULL_USER,
        WORK_PROFILE,
        PRIVATE_PROFILE,
        OTHER_PROFILE,
        UNKNOWN,
    }
}
