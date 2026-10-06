package eu.darken.sdmse.appcontrol.ui.access

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.access.AppAccessController
import eu.darken.sdmse.appcontrol.core.access.AppOpEntry
import eu.darken.sdmse.appcontrol.core.access.AppPermissionInspector
import eu.darken.sdmse.appcontrol.core.access.AppPermissionSnapshot
import eu.darken.sdmse.appcontrol.core.access.history.AppAccessOperation
import eu.darken.sdmse.appcontrol.ui.AppAccessRoute
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
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
                statePub.value = loadAccessState(route.installId)
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
        if (ready.hasPendingOrActiveMutation()) return

        val permission = ready.snapshot.permissions.singleOrNull { it.name == permissionId } ?: return
        if (!permission.runtimeMutable) return

        val action = if (permission.granted) PermissionAction.REVOKE else PermissionAction.GRANT
        statePub.value = ready.copy(
            pendingMutation = PermissionMutation(permissionId = permissionId, action = action),
        )
    }

    fun dismissPermissionMutation() {
        val ready = statePub.value as? State.Ready ?: return
        if (ready.hasActiveMutation()) return
        statePub.value = ready.copy(pendingMutation = null)
    }

    fun confirmPermissionMutation() {
        val ready = statePub.value as? State.Ready ?: return
        val mutation = ready.pendingMutation ?: return
        if (
            ready.mutatingPermissionId != null ||
            ready.mutatingAppOpKey != null ||
            ready.undoingOperationId != null ||
            ready.pendingAppOpMutation != null ||
            ready.pendingUndoOperationId != null
        ) return

        val installId = ready.snapshot.installId
        statePub.value = ready.copy(
            pendingMutation = null,
            mutatingPermissionId = mutation.permissionId,
        )

        launch {
            try {
                val result = when (mutation.action) {
                    PermissionAction.GRANT -> controller.grantRuntimePermission(
                        installId,
                        mutation.permissionId,
                    )

                    PermissionAction.REVOKE -> controller.revokeRuntimePermission(
                        installId,
                        mutation.permissionId,
                    )
                } ?: throw IllegalStateException(
                    "Permission mutation rejected for ${mutation.permissionId}"
                )

                statePub.value = loadAccessState(result.snapshot)
                result.historyError?.let { errorEvents.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                statePub.value = recoverAfterMutationFailure(installId, e)
            }
        }
    }

    fun requestAppOpMutation(key: PkgOps.AppOpsKey) {
        val ready = statePub.value as? State.Ready ?: return
        if (ready.hasPendingOrActiveMutation()) return

        val entry = ready.appOps.singleOrNull { it.key == key } ?: return
        statePub.value = ready.copy(
            pendingAppOpMutation = AppOpMutation(key = key, currentValue = entry.value),
        )
    }

    fun dismissAppOpMutation() {
        val ready = statePub.value as? State.Ready ?: return
        if (ready.hasActiveMutation()) return
        statePub.value = ready.copy(pendingAppOpMutation = null)
    }

    fun selectAppOpValue(value: PkgOps.AppOpsValue) {
        val ready = statePub.value as? State.Ready ?: return
        val mutation = ready.pendingAppOpMutation ?: return
        if (
            ready.mutatingPermissionId != null ||
            ready.mutatingAppOpKey != null ||
            ready.undoingOperationId != null ||
            ready.pendingMutation != null ||
            ready.pendingUndoOperationId != null
        ) return

        if (value == mutation.currentValue) {
            statePub.value = ready.copy(pendingAppOpMutation = null)
            return
        }

        val installId = ready.snapshot.installId
        statePub.value = ready.copy(
            pendingAppOpMutation = null,
            mutatingAppOpKey = mutation.key,
        )

        launch {
            try {
                val result = controller.setAppOp(
                    installId = installId,
                    key = mutation.key,
                    value = value,
                ) ?: throw IllegalStateException(
                    "AppOps mutation rejected for ${mutation.key} -> $value"
                )

                statePub.value = loadAccessState(
                    snapshot = ready.snapshot,
                    knownAppOps = result.appOps,
                )
                result.historyError?.let { errorEvents.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                statePub.value = recoverAfterMutationFailure(installId, e)
            }
        }
    }

    fun requestUndo(operationId: String) {
        val ready = statePub.value as? State.Ready ?: return
        if (ready.hasPendingOrActiveMutation()) return

        val item = ready.history.singleOrNull { it.operation.id == operationId } ?: return
        if (!item.undoAvailable) return

        statePub.value = ready.copy(pendingUndoOperationId = operationId)
    }

    fun dismissUndo() {
        val ready = statePub.value as? State.Ready ?: return
        if (ready.hasActiveMutation()) return
        statePub.value = ready.copy(pendingUndoOperationId = null)
    }

    fun confirmUndo() {
        val ready = statePub.value as? State.Ready ?: return
        val operationId = ready.pendingUndoOperationId ?: return
        if (
            ready.mutatingPermissionId != null ||
            ready.mutatingAppOpKey != null ||
            ready.undoingOperationId != null ||
            ready.pendingMutation != null ||
            ready.pendingAppOpMutation != null
        ) return

        val installId = ready.snapshot.installId
        statePub.value = ready.copy(
            pendingUndoOperationId = null,
            undoingOperationId = operationId,
        )

        launch {
            try {
                val result = controller.undo(installId, operationId)
                    ?: throw IllegalStateException("Undo rejected for operation $operationId")

                statePub.value = loadAccessState(installId)
                result.historyError?.let { errorEvents.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                statePub.value = recoverAfterMutationFailure(installId, e)
            }
        }
    }

    private suspend fun recoverAfterMutationFailure(
        installId: eu.darken.sdmse.common.pkgs.features.InstallId,
        failure: Throwable,
    ): State {
        errorEvents.emit(failure)
        return try {
            loadAccessState(installId)
        } catch (e: CancellationException) {
            throw e
        } catch (refreshFailure: Throwable) {
            errorEvents.emit(refreshFailure)
            State.Error(refreshFailure)
        }
    }

    private suspend fun loadAccessState(
        installId: eu.darken.sdmse.common.pkgs.features.InstallId,
    ): State {
        val snapshot = inspector.inspect(installId) ?: return State.NotFound
        return loadAccessState(snapshot)
    }

    private suspend fun loadAccessState(
        snapshot: AppPermissionSnapshot,
        knownAppOps: List<AppOpEntry>? = null,
    ): State {
        val installId = snapshot.installId

        var appOps = knownAppOps ?: emptyList()
        var appOpsError: Throwable? = null
        if (knownAppOps == null) {
            try {
                appOps = controller.queryAppOps(installId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                appOpsError = e
                errorEvents.emit(e)
            }
        }

        var operations = emptyList<AppAccessOperation>()
        var historyError: Throwable? = null
        try {
            operations = controller.recentHistory(installId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            historyError = e
            errorEvents.emit(e)
        }

        return State.Ready(
            snapshot = snapshot,
            appOps = appOps,
            appOpsError = appOpsError,
            history = buildHistoryItems(snapshot, appOps, operations),
            historyError = historyError,
        )
    }

    private fun State.Ready.hasActiveMutation(): Boolean =
        mutatingPermissionId != null ||
            mutatingAppOpKey != null ||
            undoingOperationId != null

    private fun State.Ready.hasPendingOrActiveMutation(): Boolean =
        hasActiveMutation() ||
            pendingMutation != null ||
            pendingAppOpMutation != null ||
            pendingUndoOperationId != null

    data class PermissionMutation(
        val permissionId: String,
        val action: PermissionAction,
    )

    data class AppOpMutation(
        val key: PkgOps.AppOpsKey,
        val currentValue: PkgOps.AppOpsValue,
    )

    data class HistoryItem(
        val operation: AppAccessOperation,
        val undoAvailable: Boolean,
        val reverted: Boolean,
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
            val appOps: List<AppOpEntry> = emptyList(),
            val appOpsError: Throwable? = null,
            val pendingAppOpMutation: AppOpMutation? = null,
            val mutatingAppOpKey: PkgOps.AppOpsKey? = null,
            val history: List<HistoryItem> = emptyList(),
            val historyError: Throwable? = null,
            val pendingUndoOperationId: String? = null,
            val undoingOperationId: String? = null,
        ) : State
        data class Error(val cause: Throwable) : State
        data object NotFound : State
    }

    companion object {
        private const val TAG = "AppControl:Access"

        internal fun buildHistoryItems(
            snapshot: AppPermissionSnapshot,
            appOps: List<AppOpEntry>,
            operations: List<AppAccessOperation>,
        ): List<HistoryItem> {
            val revertedIds = operations.mapNotNull { it.revertOf }.toSet()
            val installId = snapshot.installId

            return operations.map { operation ->
                val reverted = operation.id in revertedIds
                val sameTarget =
                    operation.packageName == installId.pkgId.name &&
                        operation.userId == installId.userHandle.handleId
                val structurallyUndoable =
                    sameTarget &&
                        operation.revertOf == null &&
                        !reverted &&
                        operation.before != operation.after

                val stateMatchesAfter = if (!structurallyUndoable) {
                    false
                } else {
                    when (operation.kind) {
                        AppAccessOperation.Kind.RUNTIME_PERMISSION -> {
                            val after = operation.after as? AppAccessOperation.Value.Permission
                            val current = snapshot.permissions
                                .singleOrNull { it.name == operation.subjectId }
                                ?.takeIf { it.runtimeMutable }
                            after != null && current?.granted == after.granted
                        }

                        AppAccessOperation.Kind.APP_OP -> {
                            val key = PkgOps.AppOpsKey.entries
                                .singleOrNull { it.name == operation.subjectId }
                            val after = operation.after as? AppAccessOperation.Value.AppOp
                            key != null &&
                                after != null &&
                                appOps.singleOrNull { it.key == key }?.value == after.value
                        }
                    }
                }

                HistoryItem(
                    operation = operation,
                    undoAvailable = structurallyUndoable && stateMatchesAfter,
                    reverted = reverted,
                )
            }
        }
    }
}
