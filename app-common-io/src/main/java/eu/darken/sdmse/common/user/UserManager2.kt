package eu.darken.sdmse.common.user

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager2 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userManager: UserManager,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val shellOps: ShellOps,
) {

    suspend fun currentUser(): UserProfile2 = UserProfile2(
        handle = if (!hasMultiUserSupport) UserHandle2(handleId = 0) else Process.myUserHandle().toUserHandle2(),
        isCurrent = true,
        isVisible = true,
    )

    suspend fun systemUser(): UserProfile2 = UserProfile2(
        handle = UserHandle2(handleId = -1),
        isRunning = true,
        type = UserProfile2.Type.SYSTEM,
    )

    suspend fun allUsers(): Set<UserProfile2> {
        val profiles = mutableSetOf<UserProfile2>()

        val shellMode = when {
            rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
            adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
            else -> null
        }

        log(TAG) { "allUsers(): shellMode=$shellMode" }

        if (shellMode != null) {
            try {
                val command = if (Build.VERSION.SDK_INT >= 33) "cmd user list -v" else "pm list users"
                val shellResult = shellOps.execute(ShellOpsCmd(command), shellMode)
                log(TAG) { "allUsers() result: $shellResult" }
                if (!shellResult.isSuccess) throw IllegalStateException("allUsers() failed")

                UserListParser.parse(shellResult.output)
                    .map { parsed ->
                        UserProfile2(
                            handle = UserHandle2(parsed.id),
                            label = parsed.name,
                            code = parsed.code,
                            isRunning = parsed.isRunning,
                            type = parsed.type,
                            rawType = parsed.rawType,
                            flags = parsed.flags,
                            isCurrent = parsed.isCurrent,
                            isVisible = parsed.isVisible,
                            isQuietMode = parsed.isQuietMode,
                        )
                    }
                    .run { profiles.addAll(this) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "allUsers(): Lookup failed ${e.asLog()}" }
            }
        }

        if (profiles.isEmpty()) {
            userManager.userProfiles
                .map { UserProfile2(handle = it.toUserHandle2()) }
                .run { profiles.addAll(this) }
        }

        val current = currentUser()
        val existing = profiles.firstOrNull { it.handle == current.handle }
        if (existing == null) {
            profiles.add(current)
        } else if (!existing.isCurrent) {
            profiles.remove(existing)
            profiles.add(existing.copy(isCurrent = true, isVisible = existing.isVisible || current.isVisible))
        }

        return profiles
    }

    suspend fun otherUsers() = allUsers() - currentUser()

    suspend fun isAdminUser(userHandle: UserHandle2): Boolean = !hasMultiUserSupport || userHandle.handleId == 0

    val hasMultiUserSupport: Boolean by lazy {
        try {
            UserManager::class.java.getDeclaredMethod("supportsMultipleUsers").invoke(null) as Boolean
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to determine multi-user support state: ${e.asLog()}" }
            false
        }
    }

    private fun UserHandle.toUserHandle2(): UserHandle2 {
        var id: Int? = getIdentifier()

        if (id == null) id = userManager.getSerialNumberForUser(this).toInt()

        if (id == -1) id = this.hashCode()
        return UserHandle2(handleId = id)
    }

    suspend fun getHandleForId(rawId: Int) = UserHandle2(handleId = rawId)

    companion object {
        internal val TAG = logTag("UserManager2")
    }
}
