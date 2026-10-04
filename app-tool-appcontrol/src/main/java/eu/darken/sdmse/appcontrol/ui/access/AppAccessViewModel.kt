package eu.darken.sdmse.appcontrol.ui.access

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.access.AppAccessController
import eu.darken.sdmse.appcontrol.core.access.AppPermissionInspector
import eu.darken.sdmse.appcontrol.core.access.AppPermissionSnapshot
import eu.darken.sdmse.appcontrol.ui.AppAccessRoute
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppAccessViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val inspector: AppPermissionInspector,
    private val controller: AppAccessController,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private var boundRoute: AppAccessRoute? = null
    private val statePub = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = statePub

    fun bindRoute(route: AppAccessRoute) {
        if (boundRoute != null) return
        boundRoute = route

        launch {
            statePub.value = State.Loading
            try {
                statePub.value = inspector.inspect(route.installId)
                    ?.let(State::Ready)
                    ?: State.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                statePub.value = State.Error(e)
                errorEvents.emit(e)
            }
        }
    }

    fun requestPermissionMutation(permissionId: String) {
        val ready = statePub.value as? State.Ready ?: return
        if (ready.mutatingPermissionId != null) return

        val permission = ready.snapshot.permissions.singleOrNull { it.name == permissionId } ?: return
        if (!permission.runtimeMutable) return

        val action = if (permission.granted) {
            PermissionAction.REVOKE
        } else {
            PermissionAction.GRANT
        }
        statePub.value = ready.copy(
            pendingMutation = PermissionMutation(
                permissionId = permissionId,
                action = action,
            ),
        )
    }

    fun dismissPermissionMutation() {
        val ready = statePub.value as? State.Ready ?: return
        if (ready.mutatingPermissionId != null) return
        statePub.value = ready.copy(pendingMutation = null)
    }

    fun confirmPermissionMutation() {
        val ready = statePub.value as? State.Ready ?: return
        val mutation = ready.pendingMutation ?: return
        if (ready.mutatingPermissionId != null) return

        val installId = ready.snapshot.installId
        statePub.value = ready.copy(
            pendingMutation = null,
            mutatingPermissionId = mutation.permissionId,
        )

        launch {
            try {
                val changed = when (mutation.action) {
                    PermissionAction.GRANT -> controller.grantRuntimePermission(
                        installId,
                        mutation.permissionId,
                    )

                    PermissionAction.REVOKE -> controller.revokeRuntimePermission(
                        installId,
                        mutation.permissionId,
                    )
                }
                if (!changed) {
                    throw IllegalStateException(
                        "Permission mutation rejected for ${mutation.permissionId}"
                    )
                }

                statePub.value = inspector.inspect(installId)
                    ?.let(State::Ready)
                    ?: State.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                statePub.value = State.Ready(snapshot = ready.snapshot)
                errorEvents.emit(e)
            }
        }
    }

    data class PermissionMutation(
        val permissionId: String,
        val action: PermissionAction,
    )

    enum class PermissionAction {
        GRANT,
        REVOKE,
    }

    sealed interface State {
        data object Loading : State
        data class Ready(
            val snapshot: AppPermissionSnapshot,
            val pendingMutation: PermissionMutation? = null,
            val mutatingPermissionId: String? = null,
        ) : State
        data class Error(val cause: Throwable) : State
        data object NotFound : State
    }

    companion object {
        private const val TAG = "AppControl:Access"
    }
}
