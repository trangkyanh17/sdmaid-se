package eu.darken.sdmse.appcontrol.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.access.AppOpEntry
import eu.darken.sdmse.appcontrol.core.access.AppPermissionSnapshot
import eu.darken.sdmse.appcontrol.core.access.history.AppAccessOperation
import eu.darken.sdmse.appcontrol.ui.AppAccessRoute
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date
import eu.darken.sdmse.common.R as CommonR

@Composable
fun AppAccessScreenHost(
    route: AppAccessRoute,
    vm: AppAccessViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    AppAccessScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onPermissionMutationRequested = vm::requestPermissionMutation,
        onPermissionMutationDismissed = vm::dismissPermissionMutation,
        onPermissionMutationConfirmed = vm::confirmPermissionMutation,
        onAppOpMutationRequested = vm::requestAppOpMutation,
        onAppOpMutationDismissed = vm::dismissAppOpMutation,
        onAppOpValueSelected = vm::selectAppOpValue,
        onUndoRequested = vm::requestUndo,
        onUndoDismissed = vm::dismissUndo,
        onUndoConfirmed = vm::confirmUndo,
    )
}

@Composable
internal fun AppAccessScreen(
    stateSource: StateFlow<AppAccessViewModel.State> =
        MutableStateFlow(AppAccessViewModel.State.Loading),
    onNavigateUp: () -> Unit = {},
    onPermissionMutationRequested: (String) -> Unit = {},
    onPermissionMutationDismissed: () -> Unit = {},
    onPermissionMutationConfirmed: () -> Unit = {},
    onAppOpMutationRequested: (PkgOps.AppOpsKey) -> Unit = {},
    onAppOpMutationDismissed: () -> Unit = {},
    onAppOpValueSelected: (PkgOps.AppOpsValue) -> Unit = {},
    onUndoRequested: (String) -> Unit = {},
    onUndoDismissed: () -> Unit = {},
    onUndoConfirmed: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    if (state is AppAccessViewModel.State.NotFound) {
        LaunchedEffect(Unit) { onNavigateUp() }
    }

    val title = when (val current = state) {
        is AppAccessViewModel.State.Ready -> current.snapshot.installId.pkgId.name
        AppAccessViewModel.State.Loading,
        is AppAccessViewModel.State.Error,
        AppAccessViewModel.State.NotFound -> stringResource(R.string.appcontrol_access_title)
    }

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
    ) { paddingValues ->
        when (val current = state) {
            AppAccessViewModel.State.Loading -> ProgressOverlay(
                data = null,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) { }

            AppAccessViewModel.State.NotFound -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            )

            is AppAccessViewModel.State.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.appcontrol_access_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is AppAccessViewModel.State.Ready -> PermissionList(
                snapshot = current.snapshot,
                contentPadding = paddingValues,
                mutatingPermissionId = current.mutatingPermissionId,
                onPermissionMutationRequested = onPermissionMutationRequested,
                appOps = current.appOps,
                appOpsError = current.appOpsError,
                mutatingAppOpKey = current.mutatingAppOpKey,
                onAppOpMutationRequested = onAppOpMutationRequested,
                history = current.history,
                historyError = current.historyError,
                undoingOperationId = current.undoingOperationId,
                onUndoRequested = onUndoRequested,
            )
        }
    }

    val readyState = state as? AppAccessViewModel.State.Ready
    readyState?.pendingMutation?.let { mutation ->
        PermissionMutationDialog(
            mutation = mutation,
            onDismiss = onPermissionMutationDismissed,
            onConfirm = onPermissionMutationConfirmed,
        )
    }

    readyState?.pendingAppOpMutation?.let { mutation ->
        AppOpModeDialog(
            mutation = mutation,
            onDismiss = onAppOpMutationDismissed,
            onValueSelected = onAppOpValueSelected,
        )
    }

    readyState?.pendingUndoOperationId?.let { operationId ->
        readyState.history.singleOrNull { it.operation.id == operationId }?.let { item ->
            UndoDialog(
                item = item,
                onDismiss = onUndoDismissed,
                onConfirm = onUndoConfirmed,
            )
        }
    }
}

@Composable
private fun PermissionList(
    snapshot: AppPermissionSnapshot,
    contentPadding: PaddingValues,
    mutatingPermissionId: String?,
    onPermissionMutationRequested: (String) -> Unit,
    appOps: List<AppOpEntry>,
    appOpsError: Throwable?,
    mutatingAppOpKey: PkgOps.AppOpsKey?,
    onAppOpMutationRequested: (PkgOps.AppOpsKey) -> Unit,
    history: List<AppAccessViewModel.HistoryItem>,
    historyError: Throwable?,
    undoingOperationId: String?,
    onUndoRequested: (String) -> Unit,
) {
    val grantedCount = snapshot.permissions.count { it.granted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item("summary") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.appcontrol_access_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.appcontrol_access_summary,
                        snapshot.permissions.size,
                        grantedCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (snapshot.permissions.isEmpty()) {
            item("empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.appcontrol_access_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(snapshot.permissions, key = { it.name }) { permission ->
                PermissionRow(
                    permission = permission,
                    mutatingPermissionId = mutatingPermissionId,
                    onPermissionMutationRequested = onPermissionMutationRequested,
                )
                HorizontalDivider()
            }
        }

        item("appops_header") {
            Text(
                text = stringResource(R.string.appcontrol_appops_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }

        if (appOpsError != null) {
            item("appops_error") {
                Text(
                    text = stringResource(R.string.appcontrol_appops_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(appOps, key = { it.key.raw }) { appOp ->
                AppOpRow(
                    entry = appOp,
                    mutationInProgress = mutatingAppOpKey != null || undoingOperationId != null,
                    isThisAppOpMutating = mutatingAppOpKey == appOp.key,
                    onMutationRequested = onAppOpMutationRequested,
                )
                HorizontalDivider()
            }
        }

        item("history_header") {
            Text(
                text = stringResource(R.string.appcontrol_history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }

        when {
            historyError != null -> item("history_error") {
                Text(
                    text = stringResource(R.string.appcontrol_history_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            history.isEmpty() -> item("history_empty") {
                Text(
                    text = stringResource(R.string.appcontrol_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            else -> items(history, key = { it.operation.id }) { item ->
                HistoryRow(
                    item = item,
                    undoing = undoingOperationId == item.operation.id,
                    anyUndoInProgress = undoingOperationId != null,
                    onUndoRequested = onUndoRequested,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: AppPermissionSnapshot.Entry,
    mutatingPermissionId: String?,
    onPermissionMutationRequested: (String) -> Unit,
) {
    val mutationInProgress = mutatingPermissionId != null
    val isThisPermissionMutating = mutatingPermissionId == permission.name

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = permission.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    if (permission.granted) {
                        R.string.appcontrol_access_granted
                    } else {
                        R.string.appcontrol_access_denied
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.padding(horizontal = 4.dp))

        if (permission.runtimeMutable) {
            TextButton(
                enabled = !mutationInProgress,
                onClick = { onPermissionMutationRequested(permission.name) },
            ) {
                Text(
                    text = stringResource(
                        when {
                            isThisPermissionMutating -> R.string.appcontrol_access_updating
                            permission.granted -> R.string.appcontrol_access_revoke
                            else -> R.string.appcontrol_access_grant
                        },
                    ),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.appcontrol_access_read_only),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppOpRow(
    entry: AppOpEntry,
    mutationInProgress: Boolean,
    isThisAppOpMutating: Boolean,
    onMutationRequested: (PkgOps.AppOpsKey) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = entry.key.raw, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = entry.value.raw,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(
            enabled = !mutationInProgress,
            onClick = { onMutationRequested(entry.key) },
        ) {
            Text(
                text = stringResource(
                    if (isThisAppOpMutating) {
                        R.string.appcontrol_access_updating
                    } else {
                        R.string.appcontrol_appops_change
                    },
                ),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    item: AppAccessViewModel.HistoryItem,
    undoing: Boolean,
    anyUndoInProgress: Boolean,
    onUndoRequested: (String) -> Unit,
) {
    val operation = item.operation
    val type = when (operation.kind) {
        AppAccessOperation.Kind.RUNTIME_PERMISSION ->
            stringResource(R.string.appcontrol_history_permission)
        AppAccessOperation.Kind.APP_OP ->
            stringResource(R.string.appcontrol_history_appop)
    }
    val before = historyValue(operation.before)
    val after = historyValue(operation.after)
    val timestamp = DateFormat
        .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date.from(operation.createdAt))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = if (operation.revertOf == null) {
                    type
                } else {
                    stringResource(R.string.appcontrol_history_undo_entry, type)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = operation.subjectId, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$before → $after",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (item.reverted) {
                    "$timestamp · ${stringResource(R.string.appcontrol_history_reverted)}"
                } else {
                    timestamp
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (item.undoAvailable || undoing) {
            TextButton(
                enabled = item.undoAvailable && !anyUndoInProgress,
                onClick = { onUndoRequested(operation.id) },
            ) {
                Text(
                    stringResource(
                        if (undoing) {
                            R.string.appcontrol_history_undoing
                        } else {
                            R.string.appcontrol_history_undo
                        },
                    )
                )
            }
        }
    }
}

@Composable
private fun historyValue(value: AppAccessOperation.Value): String = when (value) {
    is AppAccessOperation.Value.Permission -> stringResource(
        if (value.granted) R.string.appcontrol_access_granted else R.string.appcontrol_access_denied
    )
    is AppAccessOperation.Value.AppOp -> value.value.raw
}

@Composable
private fun UndoDialog(
    item: AppAccessViewModel.HistoryItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val operation = item.operation
    SdmConfirmDialog(
        title = stringResource(R.string.appcontrol_history_undo_title),
        message = stringResource(
            R.string.appcontrol_history_undo_message,
            operation.subjectId,
            historyValue(operation.before),
        ),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(R.string.appcontrol_history_undo),
            onClick = onConfirm,
        ),
        negative = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            initialFocus = true,
            onClick = onDismiss,
        ),
    )
}

@Composable
private fun AppOpModeDialog(
    mutation: AppAccessViewModel.AppOpMutation,
    onDismiss: () -> Unit,
    onValueSelected: (PkgOps.AppOpsValue) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appcontrol_appops_choose_mode)) },
        text = {
            Column {
                Text(
                    text = mutation.key.raw,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PkgOps.AppOpsValue.entries.forEach { value ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onValueSelected(value) },
                    ) {
                        Text(
                            text = if (value == mutation.currentValue) {
                                "${value.raw} •"
                            } else {
                                value.raw
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Composable
private fun PermissionMutationDialog(
    mutation: AppAccessViewModel.PermissionMutation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isRevoke = mutation.action == AppAccessViewModel.PermissionAction.REVOKE
    SdmConfirmDialog(
        title = stringResource(
            if (isRevoke) {
                R.string.appcontrol_access_revoke_title
            } else {
                R.string.appcontrol_access_grant_title
            },
        ),
        message = stringResource(
            if (isRevoke) {
                R.string.appcontrol_access_revoke_message
            } else {
                R.string.appcontrol_access_grant_message
            },
            mutation.permissionId,
        ),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(
                if (isRevoke) {
                    R.string.appcontrol_access_revoke
                } else {
                    R.string.appcontrol_access_grant
                },
            ),
            onClick = onConfirm,
        ),
        negative = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            initialFocus = true,
            onClick = onDismiss,
        ),
    )
}
